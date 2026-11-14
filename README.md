# 音频助手 · AndroidAudioEditor

Android 13+ 单人离线音频工具。新工程重启开发，默认 MP3，完全中文。

当前完成至**阶段5源码**：在真实导入、Media3片段试听与非破坏编辑基础上，加入锁定FFmpeg/LAME源码、本地NDK脚本、JNI探测、峰值/RMS多级波形及缩放/平移/定位。**本阶段未编译、测试或设备验证，没有提交.so。**

播放和标记来自真实播放器，原曲/编辑/选区/接缝与工作区试听各用实际片段列表；不改原始文件。微秒编辑模型与播放器毫秒位置有精度区别，不承诺预览与最终编码逐采样一致，普通试听不做响度均衡。

版本0.3.0/code3；保留上一阶段短动画。原生依赖须先按[原生说明](native/README.md)在用户本地构建，默认CMake缺依赖会明确失败；纯UI可使用`-PnativeAudioEnabled=false`，波形届时如实报不可用。真转换/MP3导出/loudnorm仍在阶段6，未开放；没有假音频、假波形或假成果。

- [产品需求](docs/PRODUCT_SPEC.md)
- [架构与版本](docs/ARCHITECTURE.md)
- [八阶段计划](docs/DEVELOPMENT_PLAN.md)
- [实施状态](docs/IMPLEMENTATION_STATUS.md)
- [决策](docs/DECISIONS.md)
- [本地验证](docs/LOCAL_VALIDATION.md)

新会话先阅读以上文档和 Git 状态。阶段提交后停止；云端禁止 Android/Gradle/NDK 编译及测试。用户本地构建方法见 LOCAL_VALIDATION.md。
