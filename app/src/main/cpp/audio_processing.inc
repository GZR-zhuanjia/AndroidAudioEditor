// Included inside audio_engine.cpp's private namespace; shares its FD/RAII/task primitives.
constexpr int OUTPUT_RATE = 48000;
constexpr int OUTPUT_CHANNELS = 2;
using PcmConsumer = std::function<void(const float*, int)>;

struct OutputFile {
    FILE* value = nullptr;
    explicit OutputFile(const char* path) : value(fopen(path, "wb+")) {
        if (!value) throw std::runtime_error("无法创建音频临时文件");
    }
    ~OutputFile() { if (value) fclose(value); }
    void write(const void* bytes, size_t length) {
        if (fwrite(bytes, 1, length, value) != length) throw std::runtime_error("写入音频失败，可能存储空间不足");
    }
    void finish() {
        if (ferror(value) || fflush(value) != 0 || fsync(fileno(value)) != 0) throw std::runtime_error("音频未能完整写入存储");
        FILE* closing = value; value = nullptr;
        if (fclose(closing) != 0) throw std::runtime_error("关闭音频文件失败");
    }
};

struct LameEncoder {
    lame_t encoder = nullptr;
    OutputFile file;
    std::vector<unsigned char> bytes;
    explicit LameEncoder(const char* path) : file(path) {
        encoder = lame_init();
        if (!encoder) throw std::runtime_error("无法初始化 LAME");
        // No constructor throws after allocation without releasing the encoder.
        if (lame_set_in_samplerate(encoder, OUTPUT_RATE) < 0 || lame_set_out_samplerate(encoder, OUTPUT_RATE) < 0 ||
            lame_set_num_channels(encoder, OUTPUT_CHANNELS) < 0 || lame_set_mode(encoder, JOINT_STEREO) < 0 ||
            lame_set_VBR(encoder, vbr_default) < 0 || lame_set_VBR_quality(encoder, 2.0f) < 0 ||
            lame_set_bWriteVbrTag(encoder, 1) < 0 || lame_init_params(encoder) < 0) {
            lame_close(encoder); encoder = nullptr;
            throw std::runtime_error("LAME 编码参数初始化失败");
        }
    }
    ~LameEncoder() { if (encoder) lame_close(encoder); }
    void append(const float* samples, int frames) {
        for (int i = 0; i < frames * OUTPUT_CHANNELS; ++i)
            if (!std::isfinite(samples[i])) throw std::runtime_error("音频含无效采样，已停止编码");
        bytes.resize((size_t(frames) * 5 + 3) / 4 + 7200);
        int count = lame_encode_buffer_interleaved_ieee_float(encoder, samples, frames, bytes.data(), bytes.size());
        if (count < 0) throw std::runtime_error("LAME MP3 编码失败，错误码：" + std::to_string(count));
        file.write(bytes.data(), count);
    }
    void finish() {
        bytes.resize(16384);
        int count = lame_encode_flush(encoder, bytes.data(), bytes.size());
        if (count < 0) throw std::runtime_error("LAME MP3 尾帧写入失败，错误码：" + std::to_string(count));
        file.write(bytes.data(), count);
        lame_mp3_tags_fid(encoder, file.value); // real Xing/LAME delay/padding metadata; one tag for the whole mix.
        file.finish();
    }
};

struct SampleSpan { int64_t start, end; };
int64_t toSample(int64_t microseconds) {
    return av_rescale_rnd(microseconds, OUTPUT_RATE, AV_TIME_BASE, AV_ROUND_UP);
}

// Decode once, preserve timestamp gaps, trim on the PCM sample grid, then concatenate retained spans.
// All source reads are through Input's already authorized descriptor. No modification to the source.
int64_t renderPcm(Input& input, int streamIndex, const std::vector<SampleSpan>& keep,
    const PcmConsumer& write, Progress& progress) {
    Decoder d;
    if (!d.frame || !d.packet) throw std::bad_alloc();
    AVStream* stream = input.format->streams[streamIndex];
    const AVCodec* implementation = avcodec_find_decoder(stream->codecpar->codec_id);
    if (!implementation) throw std::runtime_error("精简引擎不支持此音轨解码");
    d.codec = avcodec_alloc_context3(implementation);
    if (!d.codec) throw std::bad_alloc();
    checked(avcodec_parameters_to_context(d.codec, stream->codecpar), "读取解码参数失败");
    d.codec->pkt_timebase = stream->time_base; d.codec->thread_count = 1;
    checked(avcodec_open2(d.codec, implementation, nullptr), "打开音频解码器失败");
    int64_t cursor = 0, written = 0, origin = stream->start_time;
    size_t part = 0;
    std::vector<float> converted;
    std::vector<float> silence(4096 * OUTPUT_CHANNELS, 0.f);
    const auto select = [&](const float* samples, int count) {
        if (keep.empty()) { write(samples, count); written += count; return; }
        const int64_t end = cursor + count;
        while (part < keep.size() && keep[part].end <= cursor) ++part;
        size_t p = part;
        while (p < keep.size() && keep[p].start < end) {
            const int64_t start = std::max(cursor, keep[p].start);
            const int64_t stop = std::min(end, keep[p].end);
            if (stop > start) { write(samples + (start - cursor) * OUTPUT_CHANNELS, stop - start); written += stop - start; }
            if (keep[p].end > end) break;
            ++p;
        }
    };
    const auto append = [&](int count, int64_t target) {
        for (int i = 0; i < count * OUTPUT_CHANNELS; ++i)
            if (!std::isfinite(converted[i])) throw std::runtime_error("源音频包含非有限采样值");
        // Reject malformed timestamps rather than filling an arbitrarily large hole on disk.
        constexpr int64_t MAX_TIMELINE = int64_t(OUTPUT_RATE) * 24 * 60 * 60;
        if (target < -MAX_TIMELINE || target > MAX_TIMELINE || cursor > MAX_TIMELINE)
            throw std::runtime_error("暂不支持超过24小时或时间戳异常的音频");
        if (std::abs(target - cursor) <= 2) target = cursor;
        while (target > cursor) {
            input.task->check();
            if (!keep.empty() && cursor >= keep.back().end) return;
            int gap = std::min<int64_t>(4096, target - cursor);
            if (!keep.empty()) gap = std::min<int64_t>(gap, keep.back().end - cursor);
            select(silence.data(), gap); cursor += gap;
        }
        int skipped = std::min<int64_t>(count, std::max<int64_t>(0, cursor - target));
        if (count > skipped) { select(converted.data() + skipped * OUTPUT_CHANNELS, count - skipped); cursor += count - skipped; }
        progress.send(av_rescale(cursor, AV_TIME_BASE, OUTPUT_RATE));
    };
    const auto complete = [&]() { return !keep.empty() && cursor >= keep.back().end; };
    const auto receive = [&]() {
        while (true) {
            input.task->check();
            int result = avcodec_receive_frame(d.codec, d.frame);
            if (result == AVERROR(EAGAIN) || result == AVERROR_EOF) return;
            checked(result, "音频解码失败");
            if (!d.swr) {
                d.inputRate = d.frame->sample_rate; d.sampleFormat = static_cast<AVSampleFormat>(d.frame->format);
                if (d.inputRate <= 0 || d.inputRate > 384000 || d.frame->ch_layout.nb_channels < 1 || d.frame->ch_layout.nb_channels > 32)
                    throw std::runtime_error("音频采样率或声道布局不受支持");
                checked(av_channel_layout_copy(&d.layout, &d.frame->ch_layout), "读取声道布局失败");
                AVChannelLayout stereo = AV_CHANNEL_LAYOUT_STEREO;
                checked(swr_alloc_set_opts2(&d.swr, &stereo, AV_SAMPLE_FMT_FLT, OUTPUT_RATE,
                    &d.layout, d.sampleFormat, d.inputRate, 0, nullptr), "创建重采样器失败");
                checked(swr_init(d.swr), "初始化重采样器失败");
            }
            if (d.frame->sample_rate != d.inputRate || d.frame->format != d.sampleFormat ||
                av_channel_layout_compare(&d.layout, &d.frame->ch_layout) != 0)
                throw std::runtime_error("暂不支持途中改变音频格式的源文件");
            int64_t delay = av_rescale(swr_get_delay(d.swr, d.inputRate), OUTPUT_RATE, d.inputRate);
            int64_t target = cursor;
            if (d.frame->best_effort_timestamp != AV_NOPTS_VALUE) {
                if (origin == AV_NOPTS_VALUE) origin = d.frame->best_effort_timestamp - av_rescale_q(cursor + delay, AVRational{1, OUTPUT_RATE}, stream->time_base);
                target = av_rescale_q(d.frame->best_effort_timestamp - origin, stream->time_base, AVRational{1, OUTPUT_RATE}) - delay;
            }
            int capacity = swr_get_out_samples(d.swr, d.frame->nb_samples);
            checked(capacity, "读取重采样容量失败");
            if (int64_t(capacity) * OUTPUT_CHANNELS > 1024 * 1024) throw std::runtime_error("音频帧过大");
            converted.resize(size_t(capacity) * OUTPUT_CHANNELS);
            uint8_t* outputs[] = {reinterpret_cast<uint8_t*>(converted.data())};
            int frames = swr_convert(d.swr, outputs, capacity, const_cast<const uint8_t**>(d.frame->extended_data), d.frame->nb_samples);
            checked(frames, "音频重采样失败"); append(frames, target); av_frame_unref(d.frame);
            if (complete()) return;
        }
    };
    while (!complete()) {
        input.task->check();
        int result = av_read_frame(input.format, d.packet);
        input.task->check();
        if (result == AVERROR_EOF) break;
        checked(result, "读取媒体数据失败");
        if (d.packet->stream_index == streamIndex) {
            result = avcodec_send_packet(d.codec, d.packet);
            if (result == AVERROR(EAGAIN)) {
                receive();
                if (complete()) { av_packet_unref(d.packet); break; }
                result = avcodec_send_packet(d.codec, d.packet);
            }
            checked(result, "提交解码数据失败"); receive();
        }
        av_packet_unref(d.packet);
    }
    if (!complete()) {
        checked(avcodec_send_packet(d.codec, nullptr), "完成解码失败"); receive();
        if (d.swr) while (!complete()) {
            input.task->check();
            int capacity = swr_get_out_samples(d.swr, 0); checked(capacity, "读取尾部采样失败");
            capacity = std::max(4096, capacity);
            if (capacity > 512 * 1024) throw std::runtime_error("重采样尾部过大");
            converted.resize(size_t(capacity) * OUTPUT_CHANNELS);
            uint8_t* outputs[] = {reinterpret_cast<uint8_t*>(converted.data())};
            int frames = swr_convert(d.swr, outputs, capacity, nullptr, 0);
            checked(frames, "完成重采样失败"); if (!frames) break;
            append(frames, cursor);
        }
    }
    if (written <= 0) throw std::runtime_error("保留区间没有可导出的音频采样");
    // Container durations may include a small amount of codec padding; never invent tail samples.
    if (!keep.empty() && cursor + OUTPUT_RATE / 10 < keep.back().end)
        throw std::runtime_error("源音频提前结束，不能覆盖保存的剪辑范围");
    input.task->check(); return written;
}

struct Muxer {
    AVFormatContext* format = nullptr;
    AVPacket* packet = av_packet_alloc();
    ~Muxer() {
        av_packet_free(&packet);
        if (format) { if (format->pb) avio_closep(&format->pb); avformat_free_context(format); }
    }
};
void copyMp3(Input& input, int index, const char* output, Progress& progress) {
    Muxer mux;
    if (!mux.packet) throw std::bad_alloc();
    checked(avformat_alloc_output_context2(&mux.format, nullptr, "mp3", output), "创建 MP3 封装失败");
    AVStream* source = input.format->streams[index];
    AVStream* stream = avformat_new_stream(mux.format, nullptr);
    if (!stream) throw std::bad_alloc();
    checked(avcodec_parameters_copy(stream->codecpar, source->codecpar), "复制 MP3 音轨参数失败");
    stream->codecpar->codec_tag = 0; stream->time_base = source->time_base;
    checked(avio_open(&mux.format->pb, output, AVIO_FLAG_WRITE), "创建 MP3 文件失败");
    checked(avformat_write_header(mux.format, nullptr), "写入 MP3 头失败");
    int64_t origin = AV_NOPTS_VALUE, next = 0; bool wrote = false;
    while (true) {
        input.task->check();
        int result = av_read_frame(input.format, mux.packet); input.task->check();
        if (result == AVERROR_EOF) break;
        checked(result, "读取 MP3 音轨失败");
        if (mux.packet->stream_index == index) {
            if (origin == AV_NOPTS_VALUE) origin = mux.packet->dts != AV_NOPTS_VALUE ? mux.packet->dts : mux.packet->pts;
            if (origin == AV_NOPTS_VALUE) origin = 0;
            if (mux.packet->duration <= 0) {
                int samples = av_get_audio_frame_duration2(source->codecpar, mux.packet->size);
                if (samples <= 0 || source->codecpar->sample_rate <= 0) throw std::runtime_error("MP3 帧缺少有效时长");
                mux.packet->duration = av_rescale_q(samples, AVRational{1, source->codecpar->sample_rate}, source->time_base);
            }
            mux.packet->pts = mux.packet->pts == AV_NOPTS_VALUE ? next : mux.packet->pts - origin;
            mux.packet->dts = mux.packet->dts == AV_NOPTS_VALUE ? mux.packet->pts : mux.packet->dts - origin;
            next = mux.packet->pts + mux.packet->duration;
            progress.send(av_rescale_q(next, source->time_base, AV_TIME_BASE_Q));
            av_packet_rescale_ts(mux.packet, source->time_base, stream->time_base);
            mux.packet->stream_index = stream->index; mux.packet->pos = -1;
            checked(av_interleaved_write_frame(mux.format, mux.packet), "写入 MP3 音轨失败"); wrote = true;
        }
        av_packet_unref(mux.packet);
    }
    if (!wrote) throw std::runtime_error("MP3 音轨没有数据");
    checked(av_write_trailer(mux.format), "完成 MP3 封装失败");
    checked(avio_closep(&mux.format->pb), "关闭 MP3 文件失败"); input.task->check();
}

int64_t rawFrames(const std::string& path) {
    struct stat info{};
    if (stat(path.c_str(), &info) != 0 || !S_ISREG(info.st_mode) || info.st_size <= 0 || info.st_size % (sizeof(float) * OUTPUT_CHANNELS) != 0)
        throw std::runtime_error("歌曲 PCM 临时文件缺失或不完整");
    return info.st_size / (sizeof(float) * OUTPUT_CHANNELS);
}

// FFmpeg loudnorm prints its actual measurements on uninit. Capture only this thread and filter.
struct LogCapture { void* filter = nullptr; std::string json; bool failed = false; };
thread_local LogCapture* capture = nullptr;
void nativeLog(void* context, int level, const char* format, va_list args) {
    if (capture && capture->filter && context == capture->filter && level <= AV_LOG_INFO) {
        char text[8192]; va_list copy; va_copy(copy, args);
        int count = vsnprintf(text, sizeof(text), format, copy); va_end(copy);
        if (count < 0 || size_t(count) >= sizeof(text) || capture->json.size() + count > 16384) { capture->failed = true; return; }
        try { capture->json.append(text, count); } catch (...) { capture->failed = true; }
    } else av_log_default_callback(context, level, format, args);
}
struct CaptureScope {
    LogCapture* previous;
    explicit CaptureScope(LogCapture& next) : previous(capture) {
        static std::once_flag installed;
        std::call_once(installed, [] { av_log_set_callback(nativeLog); }); capture = &next;
    }
    ~CaptureScope() { capture = previous; }
};
struct FilterGraph {
    AVFilterGraph* graph = avfilter_graph_alloc();
    AVFrame* frame = av_frame_alloc();
    AVFrame* filtered = av_frame_alloc();
    ~FilterGraph() { avfilter_graph_free(&graph); av_frame_free(&frame); av_frame_free(&filtered); }
};
const AVFilter* requiredFilter(const char* name) {
    const AVFilter* filter = avfilter_get_by_name(name);
    if (!filter) throw std::runtime_error(std::string("原生库缺少所需滤镜，请按当前脚本重新构建：") + name);
    return filter;
}

std::string loudnormPass(const std::string& input, const char* output, const std::string& options,
    const Task& task, Progress& progress, int pass) {
    task.check();
    const int64_t frames = rawFrames(input);
    LogCapture logs; CaptureScope capturing(logs); FilterGraph g;
    if (!g.graph || !g.frame || !g.filtered) throw std::bad_alloc();
    // A graph never schedules log output onto an unrelated worker thread.
    g.graph->nb_threads = 1;
    AVFilterContext *source = nullptr, *loudness = nullptr, *format = nullptr, *sink = nullptr;
    checked(avfilter_graph_create_filter(&source, requiredFilter("abuffer"), "source",
        "time_base=1/48000:sample_rate=48000:sample_fmt=flt:channel_layout=stereo", nullptr, g.graph), "创建响度输入失败");
    checked(avfilter_graph_create_filter(&loudness, requiredFilter("loudnorm"), "song_loudness", options.c_str(), nullptr, g.graph), "创建 loudnorm 失败");
    logs.filter = loudness;
    checked(avfilter_graph_create_filter(&format, requiredFilter("aformat"), "output_format",
        "sample_fmts=flt:sample_rates=48000:channel_layouts=stereo", nullptr, g.graph), "创建响度输出格式失败");
    checked(avfilter_graph_create_filter(&sink, requiredFilter("abuffersink"), "sink", nullptr, nullptr, g.graph), "创建响度输出失败");
    checked(avfilter_link(source, 0, loudness, 0), "连接 loudnorm 输入失败");
    checked(avfilter_link(loudness, 0, format, 0), "连接 loudnorm 输出失败");
    checked(avfilter_link(format, 0, sink, 0), "连接响度输出失败");
    checked(avfilter_graph_config(g.graph, nullptr), "配置 loudnorm 流程失败");
    std::unique_ptr<FILE, decltype(&fclose)> file(fopen(input.c_str(), "rb"), fclose);
    if (!file) throw std::runtime_error("无法读取歌曲 PCM");
    std::unique_ptr<OutputFile> rendered;
    if (output) rendered = std::make_unique<OutputFile>(output);
    int64_t cursor = 0, produced = 0;
    const auto drain = [&](bool final) {
        while (true) {
            task.check(); int result = av_buffersink_get_frame(sink, g.filtered);
            if (result == AVERROR_EOF) return;
            if (result == AVERROR(EAGAIN)) {
                if (final) throw std::runtime_error("loudnorm 未完成尾部输出");
                return;
            }
            checked(result, "loudnorm 处理失败");
            if (g.filtered->format != AV_SAMPLE_FMT_FLT || g.filtered->sample_rate != OUTPUT_RATE || g.filtered->ch_layout.nb_channels != OUTPUT_CHANNELS)
                throw std::runtime_error("loudnorm 输出格式不符合约定");
            if (rendered) rendered->write(g.filtered->data[0], size_t(g.filtered->nb_samples) * OUTPUT_CHANNELS * sizeof(float));
            produced += g.filtered->nb_samples; av_frame_unref(g.filtered);
        }
    };
    while (cursor < frames) {
        task.check(); const int count = std::min<int64_t>(4096, frames - cursor);
        g.frame->format = AV_SAMPLE_FMT_FLT; g.frame->sample_rate = OUTPUT_RATE;
        av_channel_layout_default(&g.frame->ch_layout, OUTPUT_CHANNELS);
        g.frame->nb_samples = count; g.frame->pts = cursor;
        checked(av_frame_get_buffer(g.frame, 0), "分配响度输入采样失败");
        const size_t bytes = size_t(count) * OUTPUT_CHANNELS * sizeof(float);
        if (fread(g.frame->data[0], 1, bytes, file.get()) != bytes) throw std::runtime_error("歌曲 PCM 被截断");
        checked(av_buffersrc_add_frame_flags(source, g.frame, AV_BUFFERSRC_FLAG_KEEP_REF), "提交响度采样失败");
        av_frame_unref(g.frame); cursor += count; drain(false);
        progress.send(av_rescale((pass - 1) * frames + cursor, AV_TIME_BASE, OUTPUT_RATE));
    }
    checked(av_buffersrc_add_frame_flags(source, nullptr, 0), "结束响度输入失败"); drain(true);
    if (std::abs(produced - frames) > 2) throw std::runtime_error("loudnorm 输出时长异常，未提交成果");
    if (rendered) rendered->finish();
    avfilter_graph_free(&g.graph); // uninit now, while this exact capture context is still alive
    task.check();
    if (logs.failed || logs.json.find("\"input_i\"") == std::string::npos) throw std::runtime_error("未取得真实 loudnorm 分析数据");
    return logs.json;
}

double measured(const std::string& json, const char* name) {
    const size_t key = json.find(std::string("\"") + name + "\"");
    if (key == std::string::npos) throw std::runtime_error("loudnorm 分析缺少所需字段");
    const size_t colon = json.find(':', key), quote = json.find('"', colon);
    if (colon == std::string::npos || quote == std::string::npos) throw std::runtime_error("loudnorm 分析格式无效");
    size_t consumed = 0;
    const std::string value = json.substr(quote + 1, json.find('"', quote + 1) - quote - 1);
    const double result = std::stod(value, &consumed);
    if (consumed != value.size()) throw std::runtime_error("loudnorm 分析数值无效");
    return result;
}

std::string normalizeSong(const std::string& input, const char* output, const Task& task, Progress& progress) {
    const std::string analysis = loudnormPass(input, nullptr, "I=-16:TP=-1.5:LRA=11:print_format=json", task, progress, 1);
    double integrated = measured(analysis, "input_i"), peak = measured(analysis, "input_tp");
    double range = measured(analysis, "input_lra"), threshold = measured(analysis, "input_thresh"), offset = measured(analysis, "target_offset");
    if (!std::isfinite(integrated) && !std::isfinite(peak) && integrated < 0 && peak < 0) {
        // Digital silence has no measurable LUFS. Keep actual zero PCM, without claiming a gain was applied.
        std::unique_ptr<FILE, decltype(&fclose)> file(fopen(input.c_str(), "rb"), fclose);
        if (!file) throw std::runtime_error("无法读取静音 PCM");
        OutputFile copied(output); std::vector<float> bytes(8192);
        while (size_t count = fread(bytes.data(), sizeof(float), bytes.size(), file.get())) {
            task.check();
            for (size_t i = 0; i < count; ++i) if (bytes[i] != 0.f) throw std::runtime_error("响度不可测且非静音，请关闭响度均衡后重试");
            copied.write(bytes.data(), count * sizeof(float));
        }
        if (ferror(file.get())) throw std::runtime_error("读取静音 PCM 失败");
        copied.finish(); return "silence";
    }
    if (!std::isfinite(integrated) || !std::isfinite(peak) || !std::isfinite(range) || !std::isfinite(threshold) || !std::isfinite(offset))
        throw std::runtime_error("歌曲过短或响度不可测，请关闭响度均衡后重试");
    // Use actual first-pass measurements, never guessed defaults. Standard loudnorm may fall back internally.
    std::ostringstream options; options.imbue(std::locale::classic()); options << std::setprecision(12)
        << "I=-16:TP=-1.5:LRA=11:linear=true:print_format=json:measured_I=" << integrated
        << ":measured_TP=" << peak << ":measured_LRA=" << range << ":measured_thresh=" << threshold << ":offset=" << offset;
    const std::string result = loudnormPass(input, output, options.str(), task, progress, 2);
    return result.find("\"linear\"") != std::string::npos ? "linear" : "dynamic";
}
