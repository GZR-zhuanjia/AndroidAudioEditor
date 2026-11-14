# 原生音频引擎（阶段5）

此目录不包含二进制。锁定信息见 sources.lock.json；JNI 源码在 app/src/main/cpp/。
脚本只供用户本地运行，云端本阶段没有执行下载整套源码、配置、编译或 ELF 检查脚本。

## 本地准备

JDK17、Android SDK36、NDK **28.2.13676358/r28c**、CMake **3.22.1**。
脚本支持本地 Linux x86_64 或 macOS（Apple Silicon 需可运行 NDK 的 darwin-x86_64 工具链）；Windows 请用 Linux/WSL 构建原生依赖，再在 Windows Android Studio 使用生成的 Android .so/include。
主机需自行备好 Bash、Python3、Git、Make、pkg-config 与宿主C编译器；不通过脚本安装工具。仓库路径避免空格（上游 configure/libtool 限制）。

仅用户本地执行：

```bash
export ANDROID_NDK_HOME=/absolute/path/to/ndk/28.2.13676358
bash scripts/build-native.sh
```

脚本获取锁定真实源码至 `.native/src`（Git FFmpeg提交、SHA-256核验LAME包），保持源码工作区不被静默覆盖；在 `.native/build` 编译，输出 `.native/prefix/arm64-v8a/{include,lib,notices}`。这些均被Git忽略。失败时保留源码与构建日志，由具体错误修复，不绕过校验或生成假库。
使用 Gradle 默认开启的 externalNativeBuild 链接真实动态库；没有依赖时 CMake 明确停止。`-PnativeAudioEnabled=false` 仅允许无JNI的UI构建，波形会报原生库不可用，不能作为原生能力验证。

## 精简范围

- 动态库：avformat、avcodec、avutil、swresample、avfilter、mp3lame；另有本应用 audioengine 和 NDK libc++_shared。
- demuxer：MOV/MP4、MP3、WAV、FLAC、Ogg、Matroska/WebM、ASF、AAC；对应AAC/MP3/FLAC/ALAC/Vorbis/Opus/WMA和常用PCM解码器，精确清单在脚本。
- 编入 libmp3lame 编码器、MP3/WAV muxer，以及 loudnorm/aresample/aformat/atrim/asetpts/concat/anull，为阶段6准备。**阶段5没有转换、导出或响度处理调用**。
- 禁用网络、程序、GPL/nonfree/version3、额外库自动探测、视频解码和显示库。列表外容器/编码明确报不支持；播放器可能支持而波形引擎不支持。
- LAME生成libtool使用无版本SONAME，FFmpeg使用Android目标默认无版本SONAME；脚本读取ELF验证，不直接改扩展名或伪造文件。

## JNI 与缓存协议

输入：ContentResolver授权 AssetFileDescriptor 的 fd/startOffset/declaredLength。JNI复制fd并通过有界AVIO pread读取，不重新打开任意路径、不复制源文件；不可seek的pipe采用poll读取，需随机访问的容器可能拒绝该provider。底层provider阻塞pread时，取消仍受系统调用返回约束，不能承诺硬实时中断。

探测返回最佳音轨的真实codec、采样率、声道、流索引/流数量及可获得的时长（未知=-1）。取消：每任务共享atomic，AVFormat interrupt与读取/解码/写缓存循环轮询。进度每100ms最多一次，未知总长显示不定进度。worker异常通过JNI返回，不写成果记录。

波形：解码PCM转float，**保留原采样率和所有声道**，不单声道混合抵消，也不降至低采样率丢失高频。约20ms一个基础bin（ceil(rate/50)帧），peak=max(abs(channel sample))、RMS=sqrt(sum squares/channel sample count)。父级4倍合并，RMS按实际采样数加权。支持最长8小时、最多32声道、384kHz；超限报错。长音频内存/耗时仍需设备检查。

按帧PTS相对stream.start_time定位（无起点时按首个可用PTS对齐），正向时间空隙补零、重叠丢弃重复呈现采样；无PTS连续累计。编码延迟/时间戳处理与Media3可能存在差异，需要本地定位/静音/接缝检查。

缓存仅在cacheDir/waveforms-v1，无正式成果或源文件。AAWAVE01为8字节magic，随后全部little endian：u32版本1、u32采样率、i64总PCM帧数、f32全曲最大peak、u32级数；每级i64每bin帧数、u32 bin数量、重复f32 peak/f32 RMS。解析检查时长/级数/数量/非有限值/文件截断。无有效修改时间的provider不跨会话复用；其他缓存键含版本、URI、大小、修改时间，不是全文件内容哈希。缓存可重建，不承担项目持久化。

UI按保留片段映射原曲bin，删剪/撤销后立即合拢；离屏包络在Default线程产生，播放tick只重画播放线。partial边界仅有约20ms信息，缩放不创造更细数据，接缝附近柱形可能含该基础bin中区间外能量。全曲统一显示增益（0.01–16倍，标注倍率），不逐柱归一化，静音保持0；波形不代表响度均衡成品。

## 16 KB 与许可

NDK r28c、AGP8.10.1、未压缩JNI打包，全部FFmpeg/LAME/JNI显式max/common-page-size=16384。
`scripts/inspect-native.py` 供本地检查arm64、LOAD对齐、SONAME/NEEDED；还需检查APK中包括libc++_shared在内所有.so、zipalign及16KB设备。当前**没有二进制、编译、ELF/APK或设备兼容性结论**。

完整许可证与原生说明随APK assets/licenses交付；分发时对应源码/配置/重链接材料义务见其中NATIVE_NOTICES.md。脚本保留实际FFmpeg config.mak、锁文件/库许可，并将NDK NOTICE复制到.native/runtime-notices由Gradle打包；跨系统转移prefix时也要转移runtime-notices。发布前须将完整对应源码、脚本和实际配置等一并准备，不能只提供上游网址。
