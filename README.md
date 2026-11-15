# 音频助手 · AndroidAudioEditor

Android 13+ 单人离线音频工具。新工程重启开发，默认 MP3，完全中文。

当前完成至**阶段6源码**：真实文件导入、Media3非破坏编辑、多级波形，以及视频转MP3/兼容MP3音轨直接复制、批量进度/取消/失败重试、多曲片段渲染、歌曲级loudnorm两遍处理、连续LAME编码与验证入库。**本阶段未编译、测试或设备验证，没有提交.so或APK。**

播放和标记来自真实播放器，原曲/编辑/选区/接缝与工作区试听各用实际片段列表；不改原始文件。微秒编辑模型与播放器毫秒位置有精度区别，不承诺预览与最终编码逐采样一致，普通试听不做响度均衡。

版本0.4.0/code4，原生/Android工具版本与短动画不变。原生依赖须先按[原生说明](native/README.md)在用户本地构建，默认CMake缺依赖会明确失败；纯UI可使用`-PnativeAudioEnabled=false`，波形/转换/导出届时如实报不可用。成果试听/改名/分享/删除和U盘另存留待阶段7，相关入口保持真实未开放状态；已有“添加到拼接”保留。

- [产品需求](docs/PRODUCT_SPEC.md)
- [架构与版本](docs/ARCHITECTURE.md)
- [八阶段计划](docs/DEVELOPMENT_PLAN.md)
- [实施状态](docs/IMPLEMENTATION_STATUS.md)
- [决策](docs/DECISIONS.md)
- [本地验证](docs/LOCAL_VALIDATION.md)

新会话先阅读以上文档和 Git 状态。阶段提交后停止；云端禁止 Android/Gradle/NDK 编译及测试。用户本地构建方法见 LOCAL_VALIDATION.md。
