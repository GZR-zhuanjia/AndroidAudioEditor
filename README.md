# 音频助手 · AndroidAudioEditor

Android 13+ 单人离线音频工具。新工程重启开发，默认 MP3，完全中文。

当前完成至**阶段4源码**：真实文件导入/排序/项目恢复、Media3按保留片段试听、按钮标记剪切、跨片段删除/仅保留/分割、撤销重做与恢复、精确时间操作和自动保存。**本阶段未编译、测试或设备验证。**

播放和标记来自真实播放器，原曲/编辑/选区/接缝与工作区试听各用实际片段列表；不改原始文件。微秒编辑模型与播放器毫秒位置有精度区别，不承诺预览与最终编码逐采样一致，普通试听不做响度均衡。

版本0.2.0/code2；短页面淡化/底栏颜色反馈与图标缓存替代前轮即时切换。波形/FFmpeg/LAME/JNI在阶段5，真转换/拼接导出在阶段6，仍未开放。没有假音频、假波形或假成果。

- [产品需求](docs/PRODUCT_SPEC.md)
- [架构与版本](docs/ARCHITECTURE.md)
- [八阶段计划](docs/DEVELOPMENT_PLAN.md)
- [实施状态](docs/IMPLEMENTATION_STATUS.md)
- [决策](docs/DECISIONS.md)
- [本地验证](docs/LOCAL_VALIDATION.md)

新会话先阅读以上文档和 Git 状态。阶段提交后停止；云端禁止 Android/Gradle/NDK 编译及测试。用户本地构建方法见 LOCAL_VALIDATION.md。
