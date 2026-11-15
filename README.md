# 音频助手 · AndroidAudioEditor

Android 13+ 单人离线音频工具。新工程重启开发，默认 MP3，完全中文。

当前完成至**阶段7源码**：真实导入、Media3非破坏编辑、多级波形、LAME转换/片段拼接/loudnorm两遍处理，以及成果试听、重命名、分享、加入拼接、SAF外部复制、最近目录与引用安全删除。**本阶段未编译、测试或设备验证，没有提交.so或APK。**

播放和标记来自真实播放器，原曲/编辑/选区/接缝与工作区试听各用实际片段列表；不改原始文件。微秒编辑模型与播放器毫秒位置有精度区别，不承诺预览与最终编码逐采样一致，普通试听不做响度均衡。

版本0.5.0/code5，依赖/原生工具与短动画不变。须先按[原生说明](native/README.md)在用户本地构建依赖；默认CMake缺真实库会失败。`-PnativeAudioEnabled=false`仅供无JNI界面/既有文件功能检查，波形/转换/导出不可用，不能算原生验收。另存需用户授权文件夹，可选U盘；创建新副本并回读核对，不移动内部成果。分享使用只读缓存副本，系统分享面板关闭不等于送达成功。

- [产品需求](docs/PRODUCT_SPEC.md)
- [架构与版本](docs/ARCHITECTURE.md)
- [八阶段计划](docs/DEVELOPMENT_PLAN.md)
- [实施状态](docs/IMPLEMENTATION_STATUS.md)
- [决策](docs/DECISIONS.md)
- [本地验证](docs/LOCAL_VALIDATION.md)

新会话先阅读以上文档和 Git 状态。阶段提交后停止；云端禁止 Android/Gradle/NDK 编译及测试。用户本地构建方法见 LOCAL_VALIDATION.md。
