# 架构与工具版本

## 当前源码结构（阶段7）

单个 `:app`，不引入多模块或 DI 框架，依赖方向 UI → ViewModel → Repository / Service → Native Audio Engine。

```text
app/src/main/java/com/audioassistant/
  MainActivity.kt                   Compose 宿主
  model/EditProject.kt              微秒片段、来源、歌曲和导出设置
  model/WorkspaceSnapshot.kt        唯一项目 + 成果元数据快照
  data/RecentProjectRepository.kt   最近项目契约
  data/FileWorkspaceRepository.kt   Mutex / AtomicFile / JSON / 受控路径与引用校验
  data/MediaImporter.kt             系统元数据、持久读授权与按需内部副本
  data/LibraryRepository.kt         成果有效性、改名/删除事务
  data/ResultFileService.kt         分享快照、SAF目录/新文件/复制回读
  data/UriGrantLedger.kt            本版本获取的持久URI授权账本
  data/ResultNames.kt               MP3名称清理/去扩展名/去重
  processing/AudioProcessingService.kt  转换/导出编排、临时目录、验证与成果提交
  ui/WorkspaceViewModel.kt          Activity列表/文件操作，持有编辑会话
  ui/AudioEditor.kt                 编辑会话、独立状态Flow、保存协调
  editing/SegmentTimeline.kt        微秒非破坏片段操作/映射
  editing/TimeInput.kt               十进制时间输入
  playback/AudioPreview.kt           Media3真实片段播放列表
  playback/ResultPreview.kt          完整成品MP3试听、长持有文件引用
  ui/AudioAssistantApp.kt           系统选择器、唯一 NavHost / Scaffold
  ui/navigation/                   四一级入口 + 单曲裁剪子路由
  ui/screens/                      五类中文页面
  ui/components/                   面板、状态、拖动排序状态
  ui/components/ResultControls.kt   成果播放器/复制进度、改名/删除/另存面板
  ui/theme/                        系统深浅色 Material 3
  engine/NativeAudioEngine.kt       JNI探测/波形/转换/渲染/均衡/编码，worker与取消桥
  waveform/                        多级数据读取、缓存、独立会话Flow
  ui/components/WaveformPanel.kt    编辑/原曲投影、Canvas、缩放平移定位
app/src/main/cpp/                   audio_engine.cpp、audio_processing.inc / CMake真实库链接
native/                            源码版本锁与构建/协议说明
scripts/                           用户本地获取源码、交叉编译、ELF检查
app/src/main/assets/licenses/       完整原生许可与分发说明
```

阶段7接通成果管理和文件交付；既有原生编码、裁剪和短动画不变。仍为单app，使用已声明Core的FileProvider及Android DocumentsContract，不引入依赖。所有完成项只表示源码已写，未构建/运行；原生完整源码仍按锁定脚本本地获取，不提交.so。

## 固定版本

| 工具/库 | 选定版本 | 状态 |
| --- | --- | --- |
| Gradle Wrapper | 8.11.1 | 已提交脚本、JAR、发行包 SHA-256 配置；未执行 |
| AGP | 8.10.1 | 已配置 |
| JDK / JVM 目标 | 17 | 用户本地要求，云端未验证工具链 |
| Kotlin / Compose 编译插件 / Serialization 插件 | 2.1.20 | 三者同版 |
| compileSdk / targetSdk / minSdk | 36 / 36 / 33 | 已配置 |
| ABI | arm64-v8a | 原生脚本与CMake只支持此ABI |
| Compose BOM | 2025.04.01 | 管理 UI/Foundation/Material3/Material Icons Core，不单独混配 |
| Core KTX | 1.16.0 | 已声明 |
| Activity Compose | 1.10.1 | 已声明 |
| Lifecycle runtime / runtime-compose / viewmodel-compose | 2.9.0 | 已声明 |
| Navigation Compose | 2.9.0 | 已接线；未编译/运行 |
| Media3 ExoPlayer | 1.7.1 | 已接真实预览，未编译/设备验证 |
| Coroutines Android | 1.10.2 | 已声明 |
| Serialization JSON | 1.8.1 | 已接入 JSON 原子持久化，未编译/运行 |
| Android Studio | 支持 AGP 8.10 的 Meerkat Feature Drop 2024.3.2 或兼容更新稳定版 | 本地选用，未验证 |
| NDK / CMake | r28c 28.2.13676358 / 3.22.1 | 已配置externalNativeBuild，未安装/执行 |
| FFmpeg | 7.1.5 / n7.1.5 / 3a0867c2bfda4a4d4309ca1a8cbdc6175e67f587 | 已核对远端标签与peeled提交 |
| LAME | 3.100 | 原包SHA-256 ddfe36cab873794038ae2c1210557ad34857a4b6bdc515785d1da9e175b1da1e；已核对Debian源包校验表 |

版本表表示源码选择，不代表依赖解析、构建兼容性已验证。版本目录 `gradle/libs.versions.toml` 为依赖配置来源。Wrapper 从旧仓库完整复制的通用构建工具文件；不复用旧产品层代码。旧仓库检查基点 4144c1ccf6a293a3b4f179c7d9ab7aa3c5209d1b。

## 路由与 UI 状态

首页卡片和底栏使用相同 Destination/导航方法，NavHost 外的 WorkspaceViewModel 共用歌曲/视频/成果状态；launchSingleTop 与 saveState/restoreState 保留页面 UI 状态。编辑子路由 `editor?songId={songId}` 隐藏底栏，当前接入真实片段试听与编辑，空歌曲仍不显示工作台。

底部关键操作固定、LazyColumn 独立滚动。系统返回先关面板/对话框；拖动时返回只请求取消、恢复原序；裁剪器返回工作区；一级模块返回首页。主 Scaffold 消费 Insets。实际导航、键盘和手势布局待设备验证。

Activity Result：PickMultipleVisualMedia(VideoOnly) 一次选一或多视频；OpenMultipleDocuments 分别约束 video/* 与 audio/*，另有 OpenDocument 用于重新关联原曲。无需新增权限。选择器取消不改变数据；缺少选择器可报告错误。进程重建的结果若先于项目加载返回，会排队等待，不能静默丢弃。

## 时间与项目数据

AudioSegment 为原曲半开区间 `[sourceStartUs, sourceEndUs)`，整数微秒、非负且结束大于开始；片段记录源 ID，歌曲记录顺序片段。SourceReference 记录稳定 ID、名称、原始时长、持久 URI/受控内部相对路径、原导入 URI、可获取的 MIME/大小。

导入建立一段 `[0, originalDurationUs)`；恢复完整保留已存片段，不展开/重新编码源文件。校验来源存在、同曲来源一致、终点不越原曲、ID 不重复、时长累加不溢出。时间映射、跨段删除和撤销/恢复由阶段4的SegmentTimeline与AudioEditor实现，原文件不变。

唯一files/project/workspace.json保存WorkspaceSnapshot(schemaVersion=1)、EditProject与真实成果索引，schema未增字段。成果记录分类、内部路径、名称、探测时长和大小，普通导入不登记。阶段7改名只改展示名称与同路径项目来源名称，不改UUID实体路径/内容；同名加副本后缀。名称去MP3扩展名/非法字符，限制100码点/220 UTF-8字节并留后缀空间。

## 持久化与失败保护

ViewModel 的 Channel 串行处理导入、移除、排序和设置；Repository 在 Dispatchers.IO 下以 Mutex 保护读写。AtomicFile + 明确 fsync 完成替换后才发布 StateFlow；临界写入与发布不可取消，防止已落盘的源引用被当作未提交清理。设置改变逐次排队存盘，不建立项目版本管理或任务恢复系统。

项目读取/结构校验失败或版本未知时保留原文件、禁止写入和清理，显示真实错误；不会用空项目覆盖。文件失效只给来源标记，不丢弃歌曲/片段。恢复后、应用重新回到前台或手动检查时验证来源可打开；重新选择同一原曲可保留旧片段，若新时长不足覆盖旧范围则拒绝。仅靠元数据无法证明重新选择的文件音频内容相同，由用户选择同一原曲。

排序期间仅本地预览，松手单次提交最终 ID 顺序；取消按钮、系统返回、指针取消和页面销毁均不提交。靠列表边缘按帧滚动并更新目标位置；上下移走相同持久化路径。保存失败回到最后提交的列表并显示错误。设置与写入队列仍有尚未落盘的短暂窗口，进程立即被杀可能丢失未提交操作，不能宣称后台恢复。

## 来源、复制与引用安全

视频仅保留当前会话 URI/元数据，不复制大型视频，不存最近项目、不承诺重开恢复队列。歌曲先尝试/复用持久读授权；provider 不支持时才在 IO 线程建立受控 `files/sources/<UUID>.source` 副本，这是源字节副本，不是格式转换。

副本先 `<UUID>.part`，检查可用空间（保留 8 MiB），分块复制、支持协程取消、fsync、非零/已知大小一致检查后重命名，再提交引用；失败清理本次未引用文件。同一规范化 URI 去重，不能凭同名误合并；不同 provider URI 对应同一内容时可能保留为独立来源，不做大文件哈希扫描。

路径必须是规范相对路径，且 canonicalFile 父目录仅 sources/ 或 results/；不接受绝对路径、../ 或规范化后不同的别名。isReferenced 同时检查项目和成果。移除歌曲先提交引用释放，再清理；成果若仍在索引中保留实体。外部原曲和另存副本从不删除。读取损坏项目时禁止清理，避免误删有效来源。

isReferenced同时检查项目来源、成果索引与活动文件引用计数。阶段7提供幂等FileLease与原子openResult（同一Mutex内查索引、验证大小、持引用），供成品试听、分享/另存与处理使用。句柄获取跨IO取消时捕获并关闭，不丢失引用。计数增减用ConcurrentHashMap原子操作，关闭播放器/完成任务后释放；原曲波形仍只读fd，退出取消。

删除先原子移除成果记录，随后只删除无项目/活动引用的内部UUID.mp3；项目仍引用时保留实体，最后歌曲移除/来源替换后清理。清理失败反馈“记录已移除、实体待清理”，不伪装事务回滚。播放该成果时删除会先停止试听。来源清理仍只扫UUID.part/.source，运行期成果清理只扫完成的UUID.mp3、不碰正在生产.part。正式输出重命名前先持有目标路径引用，覆盖rename与索引登记之间的清理窗口；这个变动只涉及Kotlin文件提交，不改原生引擎。

启动成功读取有效快照、开放任务前，清理processing/中的UUID目录及results/中无引用UUID.part/UUID.mp3；正常任务仅清自己的临时文件。损坏/未知项目不清理文件或回收权限；不恢复任务、不扫描外部源文件/未知目录。外部另存副本不在内部清理范围。

UriGrantLedger在获取新持久授权之前同步记录owned_uri_grants偏好，覆盖获取与登记间的中断窗口；导入不能持久授权仍只建受控内部副本。有效项目加载后、写入队列每个操作末尾统一回收不再需要的已跟踪授权，保护PERSISTED_URI来源、当前视频与最近保存目录。未跟踪的旧版授权不擅自撤销；树授权在有同authority来源时保守保留，以免设备断开、无法判定祖先关系时伤及项目。有效项目未加载时，连排队操作的finally也不回收授权。

## 阶段7文件交付与试听

ResultPreview用Media3直接播放完整内部MP3，不套原曲/剪辑片段，因而试听的是实际编码/响度成品。持有ResultAccess直至停止/替换；后台暂停且正在准备时也禁止迟到自动播放。一个独立时钟Flow给全局小播放器，摘要Flow只更新播放/加载/错误；复制另有独立Flow、每100ms最多报告一次，不驱动整个导航的时钟重组。小播放器可播放/暂停、拖动定位和停止；打开裁剪器/试听拼接时停止成果播放器，避免叠播。

分享先对真实成果做受控缓存快照，写完fsync、按大小/SHA-256回读核对再发系统ACTION_SEND/ClipData只读授权。FileProvider未导出，路径只开放cache/shared_audio，不暴露files/results或来源目录。快照键含成果ID/名/大小/mtime，重复分享可复用；原库删除不立即删分享快照。完成缓存按24小时在启动/新分享时清理，启动清未完成.part；系统可提前回收cache，分享URI不是永久交付地址。chooser取消/选目标无送达回执，应用不宣称“已发送成功”。

另存Bottom Sheet显示实际名称、最近位置、选择/重新选择按钮；系统ACTION_OPEN_DOCUMENT_TREE让用户授权具体目录，可选U盘，不申请广泛存储权限。external_save偏好只记最近URI/标题，是否长期授权以系统实有权限为准；仅临时授权时如实提示，重开可能重选。选择目录不假称另存完成，随后用户点保存。

每次另存重新查询目录可创建能力和全部子文档ID/名称，选择新名称，createDocument后若返回已有ID则停止、不打开写入也不删它。只对本次确认新建的文档打开写fd，复制内部原字节，尽可能fsync，关闭后重开读回，核对全长与SHA-256；完成确认前取消仅尝试清本次新文件，核对完成后的提交临界区保留已完成副本。内部文件与索引全程不移除。同名不覆盖，实际provider返回文件名用于反馈；目录超过100000项拒绝，不能用未列完的目录猜测覆盖安全。

U盘断开/位置失效/只读/授权失败报真实“位置不可用或读写失败”，给连接U盘和重新选择的入口，不伪造硬件检测；失败会重开另存面板，内部成果保留。新建外部文件若因拔盘无法删除，明确提示可能留下未完成副本，不在未来盲删外部文件。CancellationSignal用于provider打开，循环检查协程取消；已阻塞的provider I/O仍可能延迟。副本回读增加约一遍读取耗时，不保证物理介质断电后数据永不丢失。

## 后续引擎与兼容性

导入沿用MediaExtractor/MediaMetadataRetriever/OpenableColumns，没有重写其持久化。FFmpeg负责波形和正式处理；播放器支持而精简引擎不支持的格式可继续按钮编辑，波形/导出明确报错。

JniNativeAudioEngine读取授权AssetFileDescriptor，JNI自定义AVIO处理偏移和长度，probe返回真实最佳音轨。串行worker处理FFmpeg，取消回调直接设置native atomic；interrupt、读取/解码/写缓存循环检查，退出释放资源。阻塞provider的pread仍受系统调用约束；pipe可轮询取消，但需要seek的容器可能不支持。

WaveformRepository只写cacheDir/waveforms-v1，不复制原曲/登记成果。保留原采样率和声道转float，约20ms峰值/按实际采样数加权RMS，4倍父级；AAWAVE01有界读取，最长8小时/32声道/384kHz。128MiB LRU；有效mtime/size/URI才跨会话复用，无可靠mtime重分析。取消/失败清本次.part，超过24小时崩溃残留在后续load回收，不触碰项目。

WaveformSession独立Flow、代次隔离旧任务；删剪只更新片段映射，不重新解码原曲。视口包络在Default线程计算，播放tick只更新播放线；全曲统一标明显示倍率，静音为0。基础bin约20ms，边界仍受此粒度限制。完整协议见native/README.md。

AudioProcessingService独立于列表写入Channel，Activity ViewModel一次只运行一个批次/导出。进度来自实际源时间/PCM帧和处理阶段，未知时长不定进度，不用计时器推进。任务代次+取消状态隔离迟到回调；轮换模块不重建任务。批量逐项失败继续、仅失败项可重试，已完成项不再次转换；取消保留已成功登记项。

视频最佳音轨为MP3、1/2声道且标准MP3采样率、无需变换时走FFmpeg包复制/MP3 mux；其他音轨真实解码→48kHz stereo float→LAME VBR quality2。原始位流复制保留原采样率/声道。没有FFmpeg CLI或shell命令执行。

拼接按歌曲列表快照顺序：每首源解码、原曲PTS映射到48kHz采样网格、按半开保留区间ceil边界切出连续PCM，写processing/UUID目录；不修改原文件。每首loudnorm两遍处理后，再将全部实际PCM顺序喂给同一个LAME编码器；整个输出只flush一次并写Xing/LAME delay/padding标签，不逐曲编码后拼MP3字节。

loudnorm目标I=-16 LUFS、TP=-1.5dBTP、LRA=11，第一遍真实滤镜graph分析丢弃输出，从指定滤镜/当前线程的uninit日志取实际input_i/tp/lra/thresh/target_offset，第二遍传入这些值、linear=true。可能回退FFmpeg loudnorm内置dynamic模式，不另加压缩/增强滤镜；原曲试听不做此处理。数字静音逐样验证为0后原样保留；非静音但测量无效/过短则报错，让用户关闭均衡重试。滤镜输出长度不符即拒绝。

正式输出仍先results/UUID.part，真实MP3 probe与fsync后，NonCancellable中持目标引用、rename、原子登记并发布。登记失败回收未引用文件，启动处理残留；提交区取消保留成果。UI成功面板留当前模块，可直接试听真实成品、另存和查看成果库；进入库时选全部以看见新成果，关闭面板不删除文件。

PCM为48kHz双声道float32，约384KB/秒，导出预估全部PCM+均衡时最大单曲额外PCM+MP3+32MiB空间；不足或实际写失败不登记。处理总时间线最多24小时、每曲最多100000片段、最多4096首、源最多32声道/384kHz；波形仍8小时。容器尾时长最多100ms padding差允许实际缩短，绝不凭空补尾；更大的提前EOF失败。PTS空隙用真实呈现时间上的0采样补齐、重叠丢弃重复，缺PTS连续累计。Media3、原生PTS/priming、MP3编码延迟不同，不能宣称逐采样一致或所有设备绝对无缝。

NDK r28c、AGP8.10.1未压缩JNI打包，FFmpeg/LAME/JNI显式max/common-page-size=16384。scripts/inspect-native.py用于本地读ELF架构/LOAD/SONAME/NEEDED；还须检查APK中所有.so（含libc++_shared）、zipalign及16KB设备。当前无.so或兼容性结论。

## 开源许可

Wrapper保留Apache-2.0版权头。FFmpeg总体LGPL2.1+，LAME库LGPL2+；配置禁GPL/version3/nonfree/外部自动探测，动态链接。assets/licenses含完整LGPL2/LGPL2.1/GPL2配套文本、FFmpeg许可说明和对应源码/重链接义务。LAME生成libtool仅改SONAME版本策略；本地脚本保留实际config.mak/锁/许可，分发前须准备完整对应源码、构建材料和NDK NOTICE，不能只给上游链接。AndroidX/Compose/Kotlin等其余依赖仍按固定版本核对许可。

## UI 专项设计系统（阶段 3 后，不推进业务）

`ui/theme/AppColors.kt` 定义成对深浅 ColorScheme；`AppTypography.kt` 固定字体层级，`AppDimensions.kt` 集中 AppSpacing、AppShapes 和面板顶部圆角。AudioAssistantTheme 统一注入，页面不单独硬编码配色。

`AppIcon.kt` 将37个本地线性 VectorDrawable 映射成语义图标；stroke 1.8/24视口，主题 tint，资源离线打包，没有图像截图、网络字体或新依赖。波形图标是标识，不是音频采样数据。

`DesignComponents.kt` 统一 IconBadge、横向 FeatureCard、EmptyState、SectionHeader、Primary/SecondaryActionButton、TextAction、ActionDock、NoteRow、SheetHeader 和 HomeAppBar。ActionPair 按可用宽度/字体放置并列操作；固定底部与标记按钮优先紧凑并列且允许文字换行，不强制固定高度。

首页标识在顶栏，两功能卡为核心，真实最近记录用简洁卡；空视频页不显示醒目转换按钮，有队列才显示固定命名区。歌曲选中只控制本地可展开操作，不改变业务状态；拖柄坐标随新卡片容器与垂直对齐计算，排序算法、提交/取消接口保持原样。成果分类与导入工具栏固定，列表独立滚动。

裁剪器按第二轮UI状态分支呈现（见下文）；无歌曲不构建工作台，信息区与固定主控分离；矮屏横向分区。音频行为在阶段4接入、波形在阶段5接入；下文UI专项为当时记录。导出/命名/精调/成功面板统一标题/关闭、可滚动内容；导出/转换确认区固定。

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

阶段4应用0.2.0/code2、阶段5为0.3.0/code3、阶段6为0.4.0/code4；阶段7为0.5.0/code5，全部工具与既有依赖不变。

## 0.5.1 标准交互覆盖说明

本节覆盖历史阶段4的手写导航颜色动画和150/100ms页面淡化选择。底栏改用标准 Material3 NavigationBar/NavigationBarItem，保留默认交互与安全区；NavHost 不额外淡化页面，顶部标题和内容随同一路由更新。ModalBottomSheet 显式关闭和提交先完成标准 hide，再执行状态更新/业务回调，协程挂在稳定宿主作用域。主题、共享卡片/表单/标题/列表调整为中性黑灰与局部强调。无新依赖，不改文件和音频架构。

## 0.5.2 工作区成果选择

剪辑页空/有歌曲的“从成果库添加”统一打开AppSheet.IMPORT_RESULTS与ResultImportOptions，消费同一WorkspaceUiState.results。点文件复用AppModalSheet.finish收起后调用既有addResultToProject，不改变路由；存储仍验证实体/大小、拒绝重复、直接引用并原子保存。列表独立滚动，空库/已添加/失效状态来自真实数据。没有修改0.5.1主题/导航/动画。
