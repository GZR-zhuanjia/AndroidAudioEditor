# 架构与工具版本

## 当前源码结构（阶段 3）

单个 `:app`，不引入多模块或 DI 框架，依赖方向 UI → ViewModel → Repository / Service → Native Audio Engine。

```text
app/src/main/java/com/audioassistant/
  MainActivity.kt                   Compose 宿主
  model/EditProject.kt              微秒片段、来源、歌曲和导出设置
  model/WorkspaceSnapshot.kt        唯一项目 + 成果元数据快照
  data/RecentProjectRepository.kt   最近项目契约
  data/FileWorkspaceRepository.kt   Mutex / AtomicFile / JSON / 受控路径与引用校验
  data/MediaImporter.kt             系统元数据、持久读授权与按需内部副本
  ui/WorkspaceViewModel.kt          Activity 作用域列表、串行写操作、错误与面板状态
  ui/AudioAssistantApp.kt           系统选择器、唯一 NavHost / Scaffold
  ui/navigation/                   四一级入口 + 单曲裁剪子路由
  ui/screens/                      五类中文页面
  ui/components/                   面板、状态、拖动排序状态
  ui/theme/                        系统深浅色 Material 3
  engine/NativeAudioEngine.kt       仍为无实现能力契约
```

阶段 4 接入播放和编辑，阶段 5 才增加 cpp/、NDK脚本、JNI与许可；阶段 6 才建立真实转换任务。所有已完成项仅表示源码已写，未构建/运行。

## 固定版本

| 工具/库 | 选定版本 | 状态 |
| --- | --- | --- |
| Gradle Wrapper | 8.11.1 | 已提交脚本、JAR、发行包 SHA-256 配置；未执行 |
| AGP | 8.10.1 | 已配置 |
| JDK / JVM 目标 | 17 | 用户本地要求，云端未验证工具链 |
| Kotlin / Compose 编译插件 / Serialization 插件 | 2.1.20 | 三者同版 |
| compileSdk / targetSdk / minSdk | 36 / 36 / 33 | 已配置 |
| ABI | arm64-v8a | 已配置，原生尚未接入 |
| Compose BOM | 2025.04.01 | 管理 UI/Foundation/Material3/Material Icons Core，不单独混配 |
| Core KTX | 1.16.0 | 已声明 |
| Activity Compose | 1.10.1 | 已声明 |
| Lifecycle runtime / runtime-compose / viewmodel-compose | 2.9.0 | 已声明 |
| Navigation Compose | 2.9.0 | 已接线；未编译/运行 |
| Media3 ExoPlayer | 1.7.1 | 已声明，未使用 |
| Coroutines Android | 1.10.2 | 已声明 |
| Serialization JSON | 1.8.1 | 已接入 JSON 原子持久化，未编译/运行 |
| Android Studio | 支持 AGP 8.10 的 Meerkat Feature Drop 2024.3.2 或兼容更新稳定版 | 本地选用，未验证 |
| NDK / CMake | 阶段 5 冻结；优先评估 NDK r28+ | 当前不安装、不配置 externalNativeBuild |
| FFmpeg / LAME | 阶段 5 冻结具体源码版本和校验值 | 当前未引入 |

版本表表示源码选择，不代表依赖解析、构建兼容性已验证。版本目录 `gradle/libs.versions.toml` 为依赖配置来源。Wrapper 从旧仓库完整复制的通用构建工具文件；不复用旧产品层代码。旧仓库检查基点 4144c1ccf6a293a3b4f179c7d9ab7aa3c5209d1b。

## 路由与 UI 状态

首页卡片和底栏使用相同 Destination/导航方法，NavHost 外的 WorkspaceViewModel 共用歌曲/视频/成果状态；launchSingleTop 与 saveState/restoreState 保留页面 UI 状态。编辑子路由 `editor?songId={songId}` 隐藏底栏，当前仅查看真实歌曲信息，播放/剪切禁用。

底部关键操作固定、LazyColumn 独立滚动。系统返回先关面板/对话框；拖动时返回只请求取消、恢复原序；裁剪器返回工作区；一级模块返回首页。主 Scaffold 消费 Insets。实际导航、键盘和手势布局待设备验证。

Activity Result：PickMultipleVisualMedia(VideoOnly) 一次选一或多视频；OpenMultipleDocuments 分别约束 video/* 与 audio/*，另有 OpenDocument 用于重新关联原曲。无需新增权限。选择器取消不改变数据；缺少选择器可报告错误。进程重建的结果若先于项目加载返回，会排队等待，不能静默丢弃。

## 时间与项目数据

AudioSegment 为原曲半开区间 `[sourceStartUs, sourceEndUs)`，整数微秒、非负且结束大于开始；片段记录源 ID，歌曲记录顺序片段。SourceReference 记录稳定 ID、名称、原始时长、持久 URI/受控内部相对路径、原导入 URI、可获取的 MIME/大小。

导入建立一段 `[0, originalDurationUs)`；恢复完整保留已存片段，不展开/重新编码源文件。校验来源存在、同曲来源一致、终点不越原曲、ID 不重复、时长累加不溢出。时间映射、跨段删除、撤销/恢复由阶段 4 实现，本阶段不假装可剪切。

唯一 `files/project/workspace.json` 保存 WorkspaceSnapshot(schemaVersion=1)，包含唯一 EditProject(schemaVersion=1) 和成果元数据列表。成果元数据只定义来源分类、真实内部路径、名称、时长和大小；普通导入不登记成果。本阶段无成果生产者，真实成果新增/删除事务在阶段 6/7 完成。

## 持久化与失败保护

ViewModel 的 Channel 串行处理导入、移除、排序和设置；Repository 在 Dispatchers.IO 下以 Mutex 保护读写。AtomicFile + 明确 fsync 完成替换后才发布 StateFlow；临界写入与发布不可取消，防止已落盘的源引用被当作未提交清理。设置改变逐次排队存盘，不建立项目版本管理或任务恢复系统。

项目读取/结构校验失败或版本未知时保留原文件、禁止写入和清理，显示真实错误；不会用空项目覆盖。文件失效只给来源标记，不丢弃歌曲/片段。恢复后、应用重新回到前台或手动检查时验证来源可打开；重新选择同一原曲可保留旧片段，若新时长不足覆盖旧范围则拒绝。仅靠元数据无法证明重新选择的文件音频内容相同，由用户选择同一原曲。

排序期间仅本地预览，松手单次提交最终 ID 顺序；取消按钮、系统返回、指针取消和页面销毁均不提交。靠列表边缘按帧滚动并更新目标位置；上下移走相同持久化路径。保存失败回到最后提交的列表并显示错误。设置与写入队列仍有尚未落盘的短暂窗口，进程立即被杀可能丢失未提交操作，不能宣称后台恢复。

## 来源、复制与引用安全

视频仅保留当前会话 URI/元数据，不复制大型视频，不存最近项目、不承诺重开恢复队列。歌曲先尝试/复用持久读授权；provider 不支持时才在 IO 线程建立受控 `files/sources/<UUID>.source` 副本，这是源字节副本，不是格式转换。

副本先 `<UUID>.part`，检查可用空间（保留 8 MiB），分块复制、支持协程取消、fsync、非零/已知大小一致检查后重命名，再提交引用；失败清理本次未引用文件。同一规范化 URI 去重，不能凭同名误合并；不同 provider URI 对应同一内容时可能保留为独立来源，不做大文件哈希扫描。

路径必须是规范相对路径，且 canonicalFile 父目录仅 sources/ 或 results/；不接受绝对路径、../ 或规范化后不同的别名。isReferenced 同时检查项目和成果。移除歌曲先提交引用释放，再清理；成果若仍在索引中保留实体。外部原曲和另存副本从不删除。读取损坏项目时禁止清理，避免误删有效来源。

启动成功加载后，只扫描本应用 sources/ 中 UUID 命名的 .part/.source，删除不再被项目/成果引用的文件，不扫描 results/ 或未知目录。原生任务临时文件和活动任务引用尚不存在，阶段 6 必须接入任务引用后才能扩展清理；阶段 7 完善成果删除和全生命周期。

移除/失败时尽量释放本次持久读授权；若视频会话还用到同一 URI 则保守保留。进程在获取授权但未登记前突然结束，或保护视频后退出时，可能保留无文件实体的读授权；不以全量撤销 provider 权限冒险破坏有效引用，阶段 7 再完善授权账本。内部未登记副本会在下一次有效加载后回收。

## 后续引擎与兼容性

当前使用 MediaExtractor 读取真实轨道类型/时长，必要时 MediaMetadataRetriever 补充时长，OpenableColumns 读名称/大小；不支持的媒体明确拒绝或视频显示未知时长。不是假探测，也不替代阶段 5 的 FFmpeg 格式覆盖。波形、播放器、原生探测/转换均未接入。

后续服务负责 URI 适配、进度/取消、临时文件验证提交，UI 不直接拼 FFmpeg 命令。loudnorm 按每曲剪后内容独立分析处理，再按顺序拼接；Media3 预览遵循片段，受格式和编码延迟影响。不得以普通试听冒充最终响度或编码结果。

16 KB 仍仅有未压缩原生库打包配置；阶段 5 固定支持 16 KB 的 NDK/全部原生依赖，检查 ELF/包对齐并由用户本地验证，当前无 .so 或兼容性结论。

## 开源许可

Wrapper 保留 Apache-2.0 版权头，JAR 与 Gradle 官方 v8.11.1 对照记录见 LOCAL_VALIDATION。AndroidX/Compose、Kotlin 等分发前按固定版本核对许可。尚未纳入 FFmpeg/LAME，阶段 5 必须随精简配置记录完整许可、传递依赖、源码提供/重链接义务，不未经核实启用 GPL/nonfree。
