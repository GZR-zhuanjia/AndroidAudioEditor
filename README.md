# 音频助手 · AndroidAudioEditor

Android 13+ 单人离线音频工具。新工程重启开发，默认 MP3，完全中文。

当前完成至阶段 3 源码：中文 Material 3 UI/统一导航、系统 Photo Picker 视频导入、SAF 视频/音频导入、真实媒体信息、歌曲上下移与拖柄排序、唯一最近项目自动保存与恢复。**未运行编译、测试或设备验证。**

歌曲优先保留读取授权，无法持久授权时才建立受控内部副本；普通导入不会进入成果库。视频队列只在当前会话保留，不恢复后台任务。源文件失效会保留项目并提示重新选择。

播放器与剪切操作属于阶段 4，真实引擎/波形和转换导出属于阶段 5–6，仍未开放；没有假波形、假进度或假成果。成果元数据与加入拼接路径已定义，但当前尚无真实成果生产者。

- [产品需求](docs/PRODUCT_SPEC.md)
- [架构与版本](docs/ARCHITECTURE.md)
- [八阶段计划](docs/DEVELOPMENT_PLAN.md)
- [实施状态](docs/IMPLEMENTATION_STATUS.md)
- [决策](docs/DECISIONS.md)
- [本地验证](docs/LOCAL_VALIDATION.md)

新会话先阅读以上文档和 Git 状态。阶段提交后停止；云端禁止 Android/Gradle/NDK 编译及测试。用户本地构建方法见 LOCAL_VALIDATION.md。
