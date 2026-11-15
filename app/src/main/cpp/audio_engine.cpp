#include <jni.h>
#include <algorithm>
#include <atomic>
#include <cerrno>
#include <chrono>
#include <cmath>
#include <cstdio>
#include <cstdint>
#include <cstring>
#include <fcntl.h>
#include <memory>
#include <functional>
#include <iomanip>
#include <locale>
#include <sstream>
#include <cstdarg>
#include <poll.h>
#include <mutex>
#include <stdexcept>
#include <string>
#include <unordered_map>
#include <vector>
#include <sys/stat.h>
#include <unistd.h>
extern "C" {
#include <libavformat/avformat.h>
#include <libavcodec/avcodec.h>
#include <libavfilter/avfilter.h>
#include <libavfilter/buffersrc.h>
#include <libavfilter/buffersink.h>
#include <libavutil/channel_layout.h>
#include <libavutil/avutil.h>
#include <libavutil/error.h>
#include <libavutil/mem.h>
#include <libavutil/samplefmt.h>
#include <libswresample/swresample.h>
#include <lame/lame.h>
}

namespace {
struct Cancelled : std::exception {};
struct Task {
    std::atomic<bool> cancelled{false};
    void check() const { if (cancelled.load()) throw Cancelled(); }
};
std::mutex tasksMutex;
std::unordered_map<int64_t, std::shared_ptr<Task>> tasks;
std::atomic<int64_t> nextTask{1};
std::shared_ptr<Task> taskFor(int64_t id) {
    std::lock_guard<std::mutex> lock(tasksMutex);
    auto found = tasks.find(id);
    if (found == tasks.end()) throw std::runtime_error("原生任务已释放");
    return found->second;
}
void checked(int value, const char* operation) {
    if (value >= 0) return;
    char message[AV_ERROR_MAX_STRING_SIZE];
    av_strerror(value, message, sizeof(message));
    throw std::runtime_error(std::string(operation) + "：" + message);
}
void reportException(JNIEnv* env) {
    if (env->ExceptionCheck()) return;
    try { throw; }
    catch (const Cancelled&) {
        env->ThrowNew(env->FindClass("java/util/concurrent/CancellationException"), "音频分析已取消");
    } catch (const std::exception& error) {
        env->ThrowNew(env->FindClass("java/io/IOException"), error.what());
    } catch (...) { env->ThrowNew(env->FindClass("java/io/IOException"), "原生音频分析失败"); }
}
struct Utf {
    JNIEnv* env; jstring value; const char* data;
    Utf(JNIEnv* e, jstring v) : env(e), value(v), data(e->GetStringUTFChars(v, nullptr)) {
        if (!data) throw std::runtime_error("无法读取文件路径");
    }
    ~Utf() { env->ReleaseStringUTFChars(value, data); }
};

// Own a duplicate descriptor. No pathname reopening, unrestricted URI access, or whole-input copying.
struct Input {
    std::shared_ptr<Task> task;
    int fd = -1;
    int64_t offset = 0, length = -1, position = 0;
    bool seekable = false;
    AVIOContext* io = nullptr;
    AVFormatContext* format = nullptr;
    explicit Input(std::shared_ptr<Task> t) : task(std::move(t)) {}
    ~Input() {
        if (format) avformat_close_input(&format);
        if (io) { av_freep(&io->buffer); avio_context_free(&io); }
        if (fd >= 0) close(fd);
    }
    static int interrupt(void* opaque) { return static_cast<Input*>(opaque)->task->cancelled.load() ? 1 : 0; }
    static int readBytes(void* opaque, uint8_t* buffer, int size) {
        auto& in = *static_cast<Input*>(opaque);
        if (in.task->cancelled.load()) return AVERROR_EXIT;
        if (in.length >= 0) {
            if (in.position >= in.length) return AVERROR_EOF;
            size = static_cast<int>(std::min<int64_t>(size, in.length - in.position));
        }
        ssize_t count;
        while (true) {
            if (!in.seekable) {
                pollfd descriptor{in.fd, POLLIN, 0};
                int ready = poll(&descriptor, 1, 100);
                if (in.task->cancelled.load()) return AVERROR_EXIT;
                if (ready == 0 || (ready < 0 && errno == EINTR)) continue;
                if (ready < 0) return AVERROR(errno);
            }
            count = in.seekable ? pread(in.fd, buffer, size, in.offset + in.position) : read(in.fd, buffer, size);
            if (!(count < 0 && errno == EINTR && !in.task->cancelled.load())) break;
        }
        if (in.task->cancelled.load()) return AVERROR_EXIT;
        if (count < 0) return AVERROR(errno);
        if (!count) return AVERROR_EOF;
        in.position += count;
        return static_cast<int>(count);
    }
    static int64_t seekBytes(void* opaque, int64_t amount, int whence) {
        auto& in = *static_cast<Input*>(opaque);
        if (in.task->cancelled.load()) return AVERROR_EXIT;
        if (whence == AVSEEK_SIZE) return in.length >= 0 ? in.length : AVERROR(ENOSYS);
        if (!in.seekable) return AVERROR(ENOSYS);
        whence &= ~AVSEEK_FORCE;
        int64_t base = whence == SEEK_SET ? 0 : whence == SEEK_CUR ? in.position : whence == SEEK_END ? in.length : -1;
        if (base < 0 || (amount > 0 && base > INT64_MAX - amount) || amount < -base) return AVERROR(EINVAL);
        int64_t target = base + amount;
        if (in.length >= 0 && target > in.length) return AVERROR(EINVAL);
        in.position = target;
        return target;
    }
    void open(int originalFd, int64_t start, int64_t size) {
        task->check();
        if (start < 0 || size < -1 || (size >= 0 && start > INT64_MAX - size)) throw std::runtime_error("无效的文件描述符范围");
        fd = fcntl(originalFd, F_DUPFD_CLOEXEC, 0);
        if (fd < 0) throw std::runtime_error("不能复制已授权文件描述符");
        offset = start; length = size;
        seekable = lseek(fd, 0, SEEK_CUR) >= 0;
        if (!seekable && offset != 0) throw std::runtime_error("此文件提供者不支持所需的偏移读取");
        struct stat info{};
        if (length < 0 && fstat(fd, &info) == 0 && S_ISREG(info.st_mode)) length = std::max<int64_t>(0, info.st_size - offset);
        auto* buffer = static_cast<uint8_t*>(av_malloc(64 * 1024));
        if (!buffer) throw std::bad_alloc();
        io = avio_alloc_context(buffer, 64 * 1024, 0, this, readBytes, nullptr, seekable ? seekBytes : nullptr);
        if (!io) { av_free(buffer); throw std::bad_alloc(); }
        format = avformat_alloc_context();
        if (!format) throw std::bad_alloc();
        format->pb = io;
        format->flags |= AVFMT_FLAG_CUSTOM_IO;
        format->interrupt_callback = {interrupt, this};
        // Bound malformed-container probing. No network protocols are compiled in.
        format->probesize = 8 * 1024 * 1024;
        format->max_analyze_duration = 10 * AV_TIME_BASE;
        int result = avformat_open_input(&format, nullptr, nullptr, nullptr);
        task->check(); checked(result, "打开音频失败");
        result = avformat_find_stream_info(format, nullptr);
        task->check(); checked(result, "读取音轨信息失败");
    }
    int audioStream() {
        int index = av_find_best_stream(format, AVMEDIA_TYPE_AUDIO, -1, -1, nullptr, 0);
        checked(index, "未找到可用音轨");
        return index;
    }
    int64_t duration(int index) const {
        const AVStream* stream = format->streams[index];
        if (stream->duration != AV_NOPTS_VALUE && stream->duration > 0)
            return av_rescale_q(stream->duration, stream->time_base, AV_TIME_BASE_Q);
        return format->duration != AV_NOPTS_VALUE && format->duration > 0 ? format->duration : -1;
    }
};

struct Bin { float peak; double squares; int64_t samples; };
struct Envelope {
    int rate = 0, binFrames = 0;
    std::vector<Bin> bins;
    int64_t frames = 0;
    int pendingFrames = 0;
    Bin pending{0, 0, 0};
    float maximum = 0;
    void frame(const float* values, int channels) {
        if (frames >= int64_t(rate) * 60 * 60 * 8) throw std::runtime_error("波形分析最多支持8小时音频");
        for (int channel = 0; channel < channels; ++channel) {
            const float value = values ? values[channel] : 0;
            if (!std::isfinite(value)) throw std::runtime_error("音频包含非有限采样值");
            pending.peak = std::max(pending.peak, std::abs(value));
            pending.squares += double(value) * value;
            ++pending.samples;
        }
        ++frames;
        if (++pendingFrames == binFrames) flush();
    }
    void flush() {
        if (!pendingFrames) return;
        maximum = std::max(maximum, pending.peak);
        bins.push_back(pending); pending = {0, 0, 0}; pendingFrames = 0;
    }
};
struct Decoder {
    AVCodecContext* codec = nullptr;
    SwrContext* swr = nullptr;
    AVFrame* frame = av_frame_alloc();
    AVPacket* packet = av_packet_alloc();
    AVChannelLayout layout{};
    int inputRate = 0, channels = 0;
    AVSampleFormat sampleFormat = AV_SAMPLE_FMT_NONE;
    ~Decoder() {
        av_channel_layout_uninit(&layout); swr_free(&swr);
        avcodec_free_context(&codec); av_frame_free(&frame); av_packet_free(&packet);
    }
};
struct Progress {
    JNIEnv* env; jobject listener; jmethodID method; int64_t total;
    std::chrono::steady_clock::time_point previous{};
    void send(int64_t position, bool force = false) {
        auto now = std::chrono::steady_clock::now();
        if (!force && now - previous < std::chrono::milliseconds(100)) return;
        previous = now;
        env->CallVoidMethod(listener, method, static_cast<jlong>(position), static_cast<jlong>(total));
        if (env->ExceptionCheck()) throw std::runtime_error("波形进度回调失败");
    }
};
void u32(FILE* output, uint32_t value) {
    uint8_t bytes[4]; for (int i = 0; i < 4; ++i) bytes[i] = value >> (8 * i);
    if (fwrite(bytes, 1, 4, output) != 4) throw std::runtime_error("写入波形缓存失败");
}
void u64(FILE* output, uint64_t value) { u32(output, uint32_t(value)); u32(output, uint32_t(value >> 32)); }
void f32(FILE* output, float value) { uint32_t bits; memcpy(&bits, &value, 4); u32(output, bits); }
void saveEnvelope(Envelope& envelope, const char* path, const Task& task) {
    envelope.flush();
    if (!envelope.frames) throw std::runtime_error("音轨没有可分析的音频采样");
    int levels = 1;
    for (size_t size = envelope.bins.size(); size > 1; size = (size + 3) / 4) ++levels;
    std::unique_ptr<FILE, decltype(&fclose)> output(fopen(path, "wb"), fclose);
    if (!output) throw std::runtime_error("不能创建波形临时文件");
    if (fwrite("AAWAVE01", 1, 8, output.get()) != 8) throw std::runtime_error("写入波形头失败");
    u32(output.get(), 1); u32(output.get(), envelope.rate); u64(output.get(), envelope.frames);
    f32(output.get(), envelope.maximum); u32(output.get(), levels);
    auto bins = std::move(envelope.bins);
    int64_t step = envelope.binFrames;
    while (true) {
        task.check(); u64(output.get(), step); u32(output.get(), bins.size());
        size_t written = 0;
        for (const auto& bin : bins) {
            if ((written++ % 4096) == 0) task.check();
            f32(output.get(), bin.peak);
            f32(output.get(), float(std::sqrt(bin.squares / std::max<int64_t>(1, bin.samples))));
        }
        if (bins.size() == 1) break;
        std::vector<Bin> coarse;
        coarse.reserve((bins.size() + 3) / 4);
        for (size_t i = 0; i < bins.size(); i += 4) {
            Bin aggregate{0, 0, 0};
            for (size_t j = i; j < std::min(i + 4, bins.size()); ++j) {
                aggregate.peak = std::max(aggregate.peak, bins[j].peak);
                aggregate.squares += bins[j].squares; aggregate.samples += bins[j].samples;
            }
            coarse.push_back(aggregate);
        }
        bins = std::move(coarse); step *= 4;
    }
    if (fflush(output.get()) != 0 || fsync(fileno(output.get())) != 0) throw std::runtime_error("波形缓存未能完整写入");
    task.check();
}

void analyze(Input& input, int streamIndex, const char* path, Progress& progress) {
    Decoder d;
    if (!d.frame || !d.packet) throw std::bad_alloc();
    AVStream* stream = input.format->streams[streamIndex];
    const AVCodec* implementation = avcodec_find_decoder(stream->codecpar->codec_id);
    if (!implementation) throw std::runtime_error("精简引擎未包含此音频解码器");
    d.codec = avcodec_alloc_context3(implementation);
    if (!d.codec) throw std::bad_alloc();
    checked(avcodec_parameters_to_context(d.codec, stream->codecpar), "复制音轨参数失败");
    d.codec->pkt_timebase = stream->time_base;
    d.codec->thread_count = 1;
    checked(avcodec_open2(d.codec, implementation, nullptr), "打开解码器失败");
    Envelope envelope;
    int64_t origin = stream->start_time;
    std::vector<float> converted;
    auto append = [&](int count, int64_t target) {
        if (target > int64_t(envelope.rate) * 60 * 60 * 8 || target < -int64_t(envelope.rate) * 60 * 60 * 8)
            throw std::runtime_error("波形时间范围超过8小时");
        // Timestamps rounded by resampling within 2 samples are treated as continuous.
        if (std::abs(target - envelope.frames) <= 2) target = envelope.frames;
        while (envelope.frames < target) {
            if ((envelope.frames % envelope.rate) == 0) input.task->check();
            envelope.frame(nullptr, d.channels);
        }
        int skip = static_cast<int>(std::min<int64_t>(count, std::max<int64_t>(0, envelope.frames - target)));
        for (int i = skip; i < count; ++i) envelope.frame(converted.data() + size_t(i) * d.channels, d.channels);
    };
    auto consume = [&]() {
        while (true) {
            input.task->check();
            int result = avcodec_receive_frame(d.codec, d.frame);
            if (result == AVERROR(EAGAIN) || result == AVERROR_EOF) return result;
            checked(result, "解码音频帧失败");
            if (!d.swr) {
                d.channels = d.frame->ch_layout.nb_channels; d.inputRate = d.frame->sample_rate;
                if (d.channels <= 0 || d.channels > 32 || d.inputRate <= 0 || d.inputRate > 384000)
                    throw std::runtime_error("音频采样率/声道数不受支持");
                envelope.rate = d.inputRate; envelope.binFrames = (d.inputRate + 49) / 50;
                checked(av_channel_layout_copy(&d.layout, &d.frame->ch_layout), "读取声道布局失败");
                d.sampleFormat = static_cast<AVSampleFormat>(d.frame->format);
                // Keep the original rate and all channels: high-frequency/antiphase sound must not disappear.
                checked(swr_alloc_set_opts2(&d.swr, &d.layout, AV_SAMPLE_FMT_FLT, d.inputRate, &d.layout,
                    d.sampleFormat, d.inputRate, 0, nullptr), "创建波形重采样器失败");
                checked(swr_init(d.swr), "初始化波形重采样失败");
            }
            if (d.frame->sample_rate != d.inputRate || d.frame->format != d.sampleFormat ||
                av_channel_layout_compare(&d.layout, &d.frame->ch_layout) != 0)
                throw std::runtime_error("暂不支持播放途中改变采样格式的音频波形");
            int64_t delay = swr_get_delay(d.swr, d.inputRate);
            int64_t target = envelope.frames;
            if (d.frame->best_effort_timestamp != AV_NOPTS_VALUE) {
                if (origin == AV_NOPTS_VALUE) origin = d.frame->best_effort_timestamp
                    - av_rescale_q(envelope.frames + delay, AVRational{1, d.inputRate}, stream->time_base);
                target = av_rescale_q(d.frame->best_effort_timestamp - origin, stream->time_base, AVRational{1, d.inputRate}) - delay;
            }
            int capacity = swr_get_out_samples(d.swr, d.frame->nb_samples);
            checked(capacity, "读取重采样容量失败");
            if (int64_t(capacity) * d.channels > 1024 * 1024) throw std::runtime_error("异常音频帧过大");
            converted.resize(size_t(capacity) * d.channels);
            uint8_t* output[] = {reinterpret_cast<uint8_t*>(converted.data())};
            int count = swr_convert(d.swr, output, capacity, const_cast<const uint8_t**>(d.frame->extended_data), d.frame->nb_samples);
            checked(count, "波形重采样失败"); append(count, target);
            av_frame_unref(d.frame);
            progress.send(av_rescale(envelope.frames, AV_TIME_BASE, envelope.rate));
        }
    };
    while (true) {
        input.task->check();
        int result = av_read_frame(input.format, d.packet);
        input.task->check();
        if (result == AVERROR_EOF) break;
        checked(result, "读取音频数据失败");
        if (d.packet->stream_index == streamIndex) {
            result = avcodec_send_packet(d.codec, d.packet);
            if (result == AVERROR(EAGAIN)) { consume(); result = avcodec_send_packet(d.codec, d.packet); }
            checked(result, "提交音频数据失败"); consume();
        }
        av_packet_unref(d.packet);
    }
    checked(avcodec_send_packet(d.codec, nullptr), "结束音频解码失败"); consume();
    if (d.swr) {
        while (true) {
            input.task->check();
            int capacity = swr_get_out_samples(d.swr, 0);
            checked(capacity, "读取重采样尾部失败");
            if (int64_t(std::max(1024, capacity)) * d.channels > 1024 * 1024) throw std::runtime_error("异常重采样尾部过大");
            converted.resize(size_t(std::max(1024, capacity)) * d.channels);
            uint8_t* output[] = {reinterpret_cast<uint8_t*>(converted.data())};
            int count = swr_convert(d.swr, output, std::max(1024, capacity), nullptr, 0);
            checked(count, "重采样结束失败");
            if (!count) break;
            append(count, envelope.frames);
        }
    }
    saveEnvelope(envelope, path, *input.task);
    progress.send(av_rescale(envelope.frames, AV_TIME_BASE, envelope.rate), true);
}
#include "audio_processing.inc"
} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_audioassistant_engine_NativeBridge_createTask(JNIEnv* env, jobject) {
    try {
        auto task = std::make_shared<Task>();
        const int64_t id = nextTask.fetch_add(1);
        std::lock_guard<std::mutex> lock(tasksMutex); tasks.emplace(id, std::move(task)); return id;
    } catch (...) { reportException(env); return 0; }
}
extern "C" JNIEXPORT void JNICALL
Java_com_audioassistant_engine_NativeBridge_cancelTask(JNIEnv*, jobject, jlong id) {
    std::lock_guard<std::mutex> lock(tasksMutex);
    auto found = tasks.find(id); if (found != tasks.end()) found->second->cancelled.store(true);
}
extern "C" JNIEXPORT void JNICALL
Java_com_audioassistant_engine_NativeBridge_releaseTask(JNIEnv*, jobject, jlong id) {
    std::lock_guard<std::mutex> lock(tasksMutex); tasks.erase(id);
}
extern "C" JNIEXPORT jobject JNICALL
Java_com_audioassistant_engine_NativeBridge_probe(JNIEnv* env, jobject, jlong id, jint fd, jlong offset, jlong size) {
    try {
        Input input(taskFor(id)); input.open(fd, offset, size);
        const int index = input.audioStream();
        const auto* parameters = input.format->streams[index]->codecpar;
        jclass type = env->FindClass("com/audioassistant/engine/NativeProbe");
        if (!type) return nullptr;
        jmethodID constructor = env->GetMethodID(type, "<init>", "(JLjava/lang/String;IIII)V");
        if (!constructor) return nullptr;
        jstring codec = env->NewStringUTF(avcodec_get_name(parameters->codec_id));
        if (!codec) return nullptr;
        jobject result = env->NewObject(type, constructor, static_cast<jlong>(input.duration(index)), codec,
            parameters->sample_rate, parameters->ch_layout.nb_channels, index, static_cast<jint>(input.format->nb_streams));
        env->DeleteLocalRef(codec); env->DeleteLocalRef(type); input.task->check(); return result;
    } catch (...) { reportException(env); return nullptr; }
}
extern "C" JNIEXPORT void JNICALL
Java_com_audioassistant_engine_NativeBridge_analyze(JNIEnv* env, jobject, jlong id, jint fd, jlong offset,
    jlong size, jstring output, jobject listener) {
    try {
        Utf path(env, output);
        try {
            Input input(taskFor(id)); input.open(fd, offset, size);
            int index = input.audioStream();
            jclass listenerClass = env->GetObjectClass(listener);
            jmethodID method = env->GetMethodID(listenerClass, "onProgress", "(JJ)V");
            env->DeleteLocalRef(listenerClass);
            if (!method) return;
            Progress progress{env, listener, method, input.duration(index)};
            analyze(input, index, path.data, progress);
        } catch (...) { unlink(path.data); throw; }
    } catch (...) { reportException(env); }
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_audioassistant_engine_NativeBridge_versions(JNIEnv* env, jobject) {
    const std::string value = std::string("FFmpeg ") + av_version_info() + " / LAME " + get_lame_version()
        + " / avfilter " + std::to_string(avfilter_version());
    return env->NewStringUTF(value.c_str());
}

namespace {
Progress processingProgress(JNIEnv* env, jobject listener, int64_t total) {
    jclass type = env->GetObjectClass(listener);
    if (!type) throw std::runtime_error("缺少任务进度回调");
    jmethodID method = env->GetMethodID(type, "onProgress", "(JJ)V"); env->DeleteLocalRef(type);
    if (!method) throw std::runtime_error("任务进度接口不匹配");
    return Progress{env, listener, method, total};
}
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_audioassistant_engine_NativeBridge_convertMp3(JNIEnv* env, jobject, jlong id, jint fd,
    jlong offset, jlong size, jstring output, jobject listener) {
    try {
        Utf path(env, output);
        try {
            Input input(taskFor(id)); input.open(fd, offset, size);
            int index = input.audioStream(); auto* parameters = input.format->streams[index]->codecpar;
            auto progress = processingProgress(env, listener, input.duration(index));
            const int rate = parameters->sample_rate;
            const bool standardRate = rate == 8000 || rate == 11025 || rate == 12000 || rate == 16000 || rate == 22050 ||
                rate == 24000 || rate == 32000 || rate == 44100 || rate == 48000;
            if (parameters->codec_id == AV_CODEC_ID_MP3 && standardRate && parameters->ch_layout.nb_channels >= 1 && parameters->ch_layout.nb_channels <= 2) {
                copyMp3(input, index, path.data, progress); return JNI_TRUE;
            }
            LameEncoder encoder(path.data);
            renderPcm(input, index, {}, [&](const float* samples, int frames) { input.task->check(); encoder.append(samples, frames); }, progress);
            input.task->check(); encoder.finish(); input.task->check(); return JNI_FALSE;
        } catch (...) { unlink(path.data); throw; }
    } catch (...) { reportException(env); return JNI_FALSE; }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_audioassistant_engine_NativeBridge_renderSong(JNIEnv* env, jobject, jlong id, jint fd, jlong offset,
    jlong size, jlongArray starts, jlongArray ends, jstring output, jobject listener) {
    try {
        Utf path(env, output);
        try {
            const int count = env->GetArrayLength(starts);
            if (count <= 0 || count > 100000 || env->GetArrayLength(ends) != count) throw std::runtime_error("剪辑片段数量无效");
            std::vector<jlong> from(count), to(count);
            env->GetLongArrayRegion(starts, 0, count, from.data()); env->GetLongArrayRegion(ends, 0, count, to.data());
            if (env->ExceptionCheck()) return 0;
            std::vector<SampleSpan> keep; keep.reserve(count);
            int64_t previous = 0;
            for (int i = 0; i < count; ++i) {
                if (from[i] < previous || to[i] <= from[i] || to[i] > int64_t(AV_TIME_BASE) * 24 * 60 * 60)
                    throw std::runtime_error("剪辑片段时间无效或超过24小时");
                keep.push_back({toSample(from[i]), toSample(to[i])}); previous = to[i];
            }
            Input input(taskFor(id)); input.open(fd, offset, size); int index = input.audioStream();
            auto progress = processingProgress(env, listener, input.duration(index)); OutputFile pcm(path.data);
            int64_t frames = renderPcm(input, index, keep, [&](const float* samples, int count) {
                pcm.write(samples, size_t(count) * OUTPUT_CHANNELS * sizeof(float));
            }, progress);
            input.task->check(); pcm.finish(); input.task->check(); return frames;
        } catch (...) { unlink(path.data); throw; }
    } catch (...) { reportException(env); return 0; }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_audioassistant_engine_NativeBridge_normalizeSong(JNIEnv* env, jobject, jlong id, jstring source,
    jstring output, jobject listener) {
    try {
        Utf input(env, source), path(env, output);
        try {
            auto task = taskFor(id);
            auto progress = processingProgress(env, listener, av_rescale(rawFrames(input.data), AV_TIME_BASE * 2LL, OUTPUT_RATE));
            const std::string mode = normalizeSong(input.data, path.data, *task, progress);
            task->check(); return env->NewStringUTF(mode.c_str());
        } catch (...) { unlink(path.data); throw; }
    } catch (...) { reportException(env); return nullptr; }
}

extern "C" JNIEXPORT void JNICALL
Java_com_audioassistant_engine_NativeBridge_encodeMp3(JNIEnv* env, jobject, jlong id, jobjectArray inputs,
    jstring output, jobject listener) {
    try {
        Utf path(env, output);
        try {
            auto task = taskFor(id);
            int count = env->GetArrayLength(inputs);
            if (count <= 0 || count > 4096) throw std::runtime_error("拼接歌曲数量无效");
            std::vector<std::string> paths; int64_t total = 0;
            for (int i = 0; i < count; ++i) {
                task->check();
                jstring value = static_cast<jstring>(env->GetObjectArrayElement(inputs, i));
                if (!value) throw std::runtime_error("歌曲临时文件路径无效");
                { Utf source(env, value); paths.emplace_back(source.data); }
                env->DeleteLocalRef(value);
                int64_t frames = rawFrames(paths.back());
                if (frames > int64_t(OUTPUT_RATE) * 24 * 60 * 60 - total) throw std::runtime_error("拼接总时长超过24小时");
                total += frames;
            }
            auto progress = processingProgress(env, listener, av_rescale(total, AV_TIME_BASE, OUTPUT_RATE));
            LameEncoder encoder(path.data); int64_t cursor = 0;
            std::vector<float> samples(4096 * OUTPUT_CHANNELS);
            for (const auto& source : paths) {
                std::unique_ptr<FILE, decltype(&fclose)> file(fopen(source.c_str(), "rb"), fclose);
                if (!file) throw std::runtime_error("无法打开歌曲 PCM 文件");
                while (size_t read = fread(samples.data(), sizeof(float), samples.size(), file.get())) {
                    task->check();
                    if (read % OUTPUT_CHANNELS) throw std::runtime_error("歌曲 PCM 尾部不完整");
                    encoder.append(samples.data(), read / OUTPUT_CHANNELS); cursor += read / OUTPUT_CHANNELS;
                    progress.send(av_rescale(cursor, AV_TIME_BASE, OUTPUT_RATE));
                }
                if (ferror(file.get())) throw std::runtime_error("读取歌曲 PCM 失败");
            }
            task->check(); encoder.finish(); task->check();
        } catch (...) { unlink(path.data); throw; }
    } catch (...) { reportException(env); }
}
