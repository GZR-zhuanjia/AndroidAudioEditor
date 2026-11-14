# 音频助手 · AndriodAudioEditor

Android 13+ 单人离线音频工具。新工程重启开发，默认 MP3，完全中文。

当前完成至阶段 2 源码：工程基础、中文 Material 3 页面、统一四入口导航、全屏按钮式裁剪器、转换命名与导出设置 Bottom Sheet。列表初始为空；未开放的导入/播放/转换/编辑不会生成假结果。**未运行编译、测试或设备验证。**

可浏览首页卡片与底部导航；在音频剪辑空状态中点击“查看裁剪器”查看全屏结构；转换命名、导出设置、成果分类可操作。文件导入与自动保存属于阶段 3，播放/剪辑属于阶段 4，真实引擎和导出属于阶段 5–6。

- [产品需求](docs/PRODUCT_SPEC.md)
- [架构与版本](docs/ARCHITECTURE.md)
- [八阶段计划](docs/DEVELOPMENT_PLAN.md)
- [实施状态](docs/IMPLEMENTATION_STATUS.md)
- [决策](docs/DECISIONS.md)
- [本地验证](docs/LOCAL_VALIDATION.md)

新会话先阅读以上文档和 Git 状态。阶段提交后停止；云端禁止 Android/Gradle/NDK 编译及测试。用户本地构建方法见 LOCAL_VALIDATION.md。
