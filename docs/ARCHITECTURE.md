# 架构与工具版本

## 当前源码结构（阶段4）

单个 `:app`，不引入多模块或 DI 框架，依赖方向 UI → ViewModel → Repository / Service → Native Audio Engine。

```text
app/src/main/java/com/audioassistant/
  MainActivity.kt                   Compose 宿主
  model/EditProject.kt              微秒片段、来源、歌曲和导出设置
  model/WorkspaceSnapshot.kt        唯一项目 + 成果元数据快照
  data/RecentProjectRepository.kt   最近项目契约
  data/FileWorkspaceRepository.kt   Mutex / AtomicFile / JSON / 受控路径与引用校验
  data/MediaImporter.kt             系统元数据、持久读授权与按需内部副本
  ui/WorkspaceViewModel.kt          Activity列表/文件操作，持有编辑会话
  ui/AudioEditor.kt                 编辑会话、独立状态Flow、保存协调
  editing/SegmentTimeline.kt        微秒非破坏片段操作/映射
  editing/TimeInput.kt               十进制时间输入
  playback/AudioPreview.kt           Media3真实片段播放列表
  ui/AudioAssistantApp.kt           系统选择器、唯一 NavHost / Scaffold
  ui/navigation/                   四一级入口 + 单曲裁剪子路由
  ui/screens/                      五类中文页面
  ui/components/                   面板、状态、拖动排序状态
  ui/theme/                        系统深浅色 Material 3
  engine/NativeAudioEngine.kt       仍为无实现能力契约
```

阶段4已接入播放/编辑源码；阶段5才增加 cpp/、NDK脚本、JNI与许可；阶段 6 才建立真实转换任务。所有已完成项仅表示源码已写，未构建/运行。

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
| Media3 ExoPlayer | 1.7.1 | 已接真实预览，未编译/设备验证 |
| Coroutines Android | 1.10.2 | 已声明 |
| Serialization JSON | 1.8.1 | 已接入 JSON 原子持久化，未编译/运行 |
| Android Studio | 支持 AGP 8.10 的 Meerkat Feature Drop 2024.3.2 或兼容更新稳定版 | 本地选用，未验证 |
| NDK / CMake | 阶段 5 冻结；优先评估 NDK r28+ | 当前不安装、不配置 externalNativeBuild |
| FFmpeg / LAME | 阶段 5 冻结具体源码版本和校验值 | 当前未引入 |

版本表表示源码选择，不代表依赖解析、构建兼容性已验证。版本目录 `gradle/libs.versions.toml` 为依赖配置来源。Wrapper 从旧仓库完整复制的通用构建工具文件；不复用旧产品层代码。旧仓库检查基点 4144c1ccf6a293a3b4f179c7d9ab7aa3c5209d1b。

## 路由与 UI 状态

首页卡片和底栏使用相同 Destination/导航方法，NavHost 外的 WorkspaceViewModel 共用歌曲/视频/成果状态；launchSingleTop 与 saveState/restoreState 保留页面 UI 状态。编辑子路由 `editor?songId={songId}` 隐藏底栏，当前接入真实片段试听与编辑，空歌曲仍不显示工作台。

底部关键操作固定、LazyColumn 独立滚动。系统返回先关面板/对话框；拖动时返回只请求取消、恢复原序；裁剪器返回工作区；一级模块返回首页。主 Scaffold 消费 Insets。实际导航、键盘和手势布局待设备验证。

Activity Result：PickMultipleVisualMedia(VideoOnly) 一次选一或多视频；OpenMultipleDocuments 分别约束 video/* 与 audio/*，另有 OpenDocument 用于重新关联原曲。无需新增权限。选择器取消不改变数据；缺少选择器可报告错误。进程重建的结果若先于项目加载返回，会排队等待，不能静默丢弃。

## 时间与项目数据

AudioSegment 为原曲半开区间 `[sourceStartUs, sourceEndUs)`，整数微秒、非负且结束大于开始；片段记录源 ID，歌曲记录顺序片段。SourceReference 记录稳定 ID、名称、原始时长、持久 URI/受控内部相对路径、原导入 URI、可获取的 MIME/大小。

导入建立一段 `[0, originalDurationUs)`；恢复完整保留已存片段，不展开/重新编码源文件。校验来源存在、同曲来源一致、终点不越原曲、ID 不重复、时长累加不溢出。时间映射、跨段删除和撤销/恢复由阶段4的SegmentTimeline与AudioEditor实现，原文件不变。

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

导入仍使用 MediaExtractor 读取真实轨道类型/时长，必要时 MediaMetadataRetriever 补充时长，OpenableColumns 读名称/大小；不支持的媒体明确拒绝或视频显示未知时长。不是假探测，也不替代阶段 5 的 FFmpeg 格式覆盖。真实播放器已在阶段4接入；波形与原生探测/转换尚未接入。

后续服务负责 URI 适配、进度/取消、临时文件验证提交，UI 不直接拼 FFmpeg 命令。loudnorm 按每曲剪后内容独立分析处理，再按顺序拼接；Media3 预览遵循片段，受格式和编码延迟影响。不得以普通试听冒充最终响度或编码结果。

16 KB 仍仅有未压缩原生库打包配置；阶段 5 固定支持 16 KB 的 NDK/全部原生依赖，检查 ELF/包对齐并由用户本地验证，当前无 .so 或兼容性结论。

## 开源许可

Wrapper 保留 Apache-2.0 版权头，JAR 与 Gradle 官方 v8.11.1 对照记录见 LOCAL_VALIDATION。AndroidX/Compose、Kotlin 等分发前按固定版本核对许可。尚未纳入 FFmpeg/LAME，阶段 5 必须随精简配置记录完整许可、传递依赖、源码提供/重链接义务，不未经核实启用 GPL/nonfree。

## UI 专项设计系统（阶段 3 后，不推进业务）

`ui/theme/AppColors.kt` 定义成对深浅 ColorScheme；`AppTypography.kt` 固定字体层级，`AppDimensions.kt` 集中 AppSpacing、AppShapes 和面板顶部圆角。AudioAssistantTheme 统一注入，页面不单独硬编码配色。

`AppIcon.kt` 将37个本地线性 VectorDrawable 映射成语义图标；stroke 1.8/24视口，主题 tint，资源离线打包，没有图像截图、网络字体或新依赖。波形图标是标识，不是音频采样数据。

`DesignComponents.kt` 统一 IconBadge、横向 FeatureCard、EmptyState、SectionHeader、Primary/SecondaryActionButton、TextAction、ActionDock、NoteRow、SheetHeader 和 HomeAppBar。ActionPair 按可用宽度/字体放置并列操作；固定底部与标记按钮优先紧凑并列且允许文字换行，不强制固定高度。

首页标识在顶栏，两功能卡为核心，真实最近记录用简洁卡；空视频页不显示醒目转换按钮，有队列才显示固定命名区。歌曲选中只控制本地可展开操作，不改变业务状态；拖柄坐标随新卡片容器与垂直对齐计算，排序算法、提交/取消接口保持原样。成果分类与导入工具栏固定，列表独立滚动。

裁剪器现按第二轮 UI 状态分支呈现（见下文）；无歌曲不构建工作台，选中后信息区与固定主控分离；矮屏横向分区，音频行为仍未接入。导出/命名/精调/成功面板统一标题和关闭入口、可滚动内容；导出/转换确认区固定。

前两轮曾将Navigation Compose四项过渡置None；阶段4已按用户新反馈改为短淡化，以下段落的旧选择由阶段4末节覆盖。未增加内容尺寸、卡片弹簧、入场或排序过渡动画；原排序帧滚动是功能性滚动，保留。Material 标准 ripple/指示/面板反馈不以内部API强改。

底栏沿用 NavigationBar 默认安全区处理，胶囊选中和12sp短标签；Scaffold 继续消费 content padding，顶栏处理系统状态区，Bottom Sheet 沿用默认键盘/系统Insets。设备/大字/横屏验证仍空缺。Repository、模型、ViewModel、引擎、文件规则和依赖版本均无修改。

## 第二轮 UI：状态与密度

- 沿用 ColorScheme；AppSpacing.page=16dp，标题24sp/中等字重，面板18sp，文件名16sp。EmptyState 用40dp简洁图标，无大底色块；禁用按钮采用低对比容器和38%文字，与正常辅助文字区分。
- `VideoFileItem` 用一行结构容纳40dp图标、名称/元数据列和48dp移除触摸区。默认待转换不再占状态行，真实状态变化才展开。文件列表无固定卡片高度/额外边框；顶部导入并排、底部入口按现有队列显示。
- `VideoNameOptions` 单文件使用普通输入内容，批量才使用 LazyColumn（仅最大高度限制，不设最小高度）。外层按内容包裹；标题/格式/底部确认固定，输入或多文件区可滚动。空名称才出现错误辅助文案，输入键盘完成动作仅清焦点。Material3 ModalBottomSheet 继续管理 IME/系统安全区，不重复加底部系统 padding。
- 第二轮时 `EditorPresentation.kt` 只是 UI 展示契约（阶段4已接真实会话）：Ready、StartMarked、RangeMarked、AtSeam，时间为成品时间线整数微秒；有播放就绪/编辑就绪/撤销能力标记。没有 UI 状态机推进或播放器事件产生器，不保存这些展示分支到项目。
- 第二轮时业务尚未接入，根页面传默认 Ready、播放/编辑均未就绪，因此有歌曲仅展示真实名称/时长、未知位置、简短波形/功能说明和禁用主控。其余展示分支只有未来真实事件供数才呈现，不提供演示切换。UI 不自行改变 isPlaying，不把点击当成真实删除。
- 完整选区/接缝反馈和相关操作在信息区域，主控始终两行，保持固定位置；无歌曲或失效来源直接返回型空/错误状态，隐藏更多编辑入口。项目加载未完成时避免提前宣称无歌曲/无成果。
- 成果库的“去制作音频”只导航现有首页，无新业务；分类为空与全库为空分别处理；文件操作回调/ID不变。
- 底部导航保留标准最小触摸/图标和系统 inset，不通过缩小总高度侵占导航手势区。第二轮过渡为None，阶段4已按新授权修订。

所有改动限展示层，Repository、模型、ViewModel、音频引擎、排序数据逻辑、文件规则与依赖版本不变。

## 阶段4：编辑与试听的真实实现

AudioEditor由WorkspaceViewModel持有，使用同一FileWorkspaceRepository，避免第二份独立项目；UI只发EditorAction，SegmentTimeline只处理整数微秒和半开源区间，不接触媒体字节。

SongEdit增加resumePositionUs、deleted、undo、redo（均有默认值）：EditVersion只包含片段/删除记录/位置，不递归套历史。50步历史限制，删除记录存实际移除区间；恢复某次删除按源位置并集补回、消除重叠，不补回其他删除留下的空洞。split保留相邻边界，源/成品映射在接缝右偏，末端单独处理。允许空歌曲，仍有源引用以便恢复。

JSON继续schema1，新增字段默认值向前读取阶段3数据；所有当前/删除/历史源区间都校验顺序、来源、边界和位置。源重新关联也考虑历史范围，避免历史指向超出新文件时长的数据。旧应用严格JSON解析无法保证读新字段，回退需保留数据。

编辑命令立即暂停并标busy，使用会话Mutex及Repository原子事务：检查歌曲与开始操作时一致，保存成功才发布新片段/重建预览；失败保留原项目并显示错误，不宣称删除成功。返回通过同一Mutex等待未完写入，再保存光标；失败不退出。ON_STOP暂停/尝试保存，销毁释放；不建后台恢复系统。

AudioPreview只在主线程访问懒创建ExoPlayer，启用音频焦点/拔耳机暂停、禁用视频轨。每个保留源片段一个带微秒ClippingConfiguration的MediaItem，按顺序播放，不生成临时音频。不把切换到原曲当编辑结果。原曲标记主按钮禁用，精调的当前位置通过映射归成品接缝。选区/接缝试听用保留片段的切片子列表，结束后回到全编辑列表暂停。

Media3播放器播放头/seek是毫秒；按钮按下时读取真实位置，不用100ms显示采样值冒充标记精度。累积片段起点数组用于定位，避免每次读位置创建前缀列表。原曲/编辑结果播放位置明确区分，普通试听不加loudnorm。编码延迟/格式/设备造成的过渡差异必须由用户本地检查，不承诺预览与最终MP3完全一致。

EditorPresentation现由真实会话供数。位置更新只发编辑会话Flow；工作区仅收distinct的播放/忙/错误摘要，整个Scaffold和底栏不订阅100ms播放位置。编辑保存成功才刷新歌曲列表。试听全部按当前歌曲顺序的实际片段列表组建，结构性项目变更前清空工作区预览；准备中禁结构性操作，避免引用清理与待打开文件竞争。

## 阶段4：动画修订（替代前两轮None策略）

依据用户新的卡顿反馈与授权：AppMotion统一150ms进入/100ms退出淡化，底栏AppBottomBar仅130ms颜色背景/文字反馈。图标尺寸与基线恒定，不叠加Material导航项的位移/弹簧，保留标准selectable语义/触摸/系统inset。重复点击当前目标不重新导航。

AppIcon沿用原37个本地XML中的路径，改为缓存ImageVector，后台预热，不在切页临时解析XML。没有借图片冒充界面。原XML仍保留作离线资源来源。未升级依赖或迁移Navigation3；参考仅采用当前已固定版本支持的API。标准面板和触摸反馈保留。无设备性能剖析，不能证明根因或声称已消除掉帧。

参考：
- https://developer.android.com/develop/ui/compose/animation/quick-guide
- https://developer.android.com/media/media3/exoplayer/playlists
- https://github.com/androidx/media/blob/1.7.1/libraries/common/src/main/java/androidx/media3/common/MediaItem.java （核对微秒裁剪API）

源码应用版本0.2.0/versionCode2，其余工具版本不变。
