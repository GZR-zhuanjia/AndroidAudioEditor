# 架构与工具版本

## 当前源码边界

单个 `:app`，不引入 DI 框架或多模块。层级：UI → ViewModel → Repository / Service → Native Audio Engine。

```text
app/src/main/
  AndroidManifest.xml          无网络/相册全量读取权限，禁备份
  java/com/audioassistant/
    MainActivity.kt           空 Compose 启动宿主（非功能页）
    ui/theme/                 Material 3 系统深浅色主题
    model/EditProject.kt      可序列化片段、歌曲、引用、导出设置
    data/RecentProjectRepository.kt  Flow/挂起存取契约，无实现
    engine/NativeAudioEngine.kt      探测能力边界，无实现
  res/values{,-night}/        中文名称、窗口主题
```

阶段 2 增加 ui/navigation 与各功能页、ViewModel。阶段 3 在 data 中实现存储和文件服务；阶段 4 增加 playback/，实现 Media3 控制；阶段 5 才增加 cpp/、NDK 脚本、JNI 与原生许可。不用空目录、假二进制或成功桩宣称完成。

## 固定版本

| 工具/库 | 选定版本 | 状态 |
| --- | --- | --- |
| Gradle Wrapper | 8.11.1 | 已提交脚本、JAR、发行包 SHA-256 配置；未执行 |
| AGP | 8.10.1 | 已配置 |
| JDK / JVM 目标 | 17 | 用户本地要求，云端未验证工具链 |
| Kotlin / Compose 编译插件 / Serialization 插件 | 2.1.20 | 三者同版 |
| compileSdk / targetSdk / minSdk | 36 / 36 / 33 | 已配置 |
| ABI | arm64-v8a | 已配置，原生尚未接入 |
| Compose BOM | 2025.04.01 | 管理 UI/Foundation/Material3 版本，不单独混配 |
| Core KTX | 1.16.0 | 已声明 |
| Activity Compose | 1.10.1 | 已声明 |
| Lifecycle runtime / runtime-compose / viewmodel-compose | 2.9.0 | 已声明 |
| Navigation Compose | 2.9.0 | 已声明，未导航接线 |
| Media3 ExoPlayer | 1.7.1 | 已声明，未使用 |
| Coroutines Android | 1.10.2 | 已声明 |
| Serialization JSON | 1.8.1 | 已声明，尚无磁盘持久化 |
| Android Studio | 支持 AGP 8.10 的 Meerkat Feature Drop 2024.3.2 或兼容更新稳定版 | 本地选用，未验证 |
| NDK / CMake | 阶段 5 冻结；优先评估 NDK r28+ | 当前不安装、不配置 externalNativeBuild |
| FFmpeg / LAME | 阶段 5 冻结具体源码版本和校验值 | 当前未引入 |

版本表表示源码选择，不代表依赖解析、构建兼容性已验证。版本目录 `gradle/libs.versions.toml` 为依赖配置来源。Wrapper 从旧仓库完整复制的通用构建工具文件；不复用旧产品层代码。旧仓库检查基点 4144c1ccf6a293a3b4f179c7d9ab7aa3c5209d1b。

## 状态和路由（后续实施）

单 Activity；四个一级入口共用模块状态持有者，首页卡片导航同一路由。模块 ViewModel 跨底栏切换保持队列、歌曲和任务；不得每次重建空列表。全屏裁剪为工作区的子流程，隐藏底栏，系统返回先保存后退出；暂不实现后台任务恢复。进程重开仅恢复唯一最近项目，不能承诺恢复转换任务。

## 片段与时间

`AudioSegment` 记录 sourceId 和原曲半开区间 `[startUs, endUs)`，非负整数微秒，末端必须大于起点。`SongEdit.segments` 顺序即编辑成品顺序。片段总和为成品时长；以累计长度定位目标片段并加原曲起点得到源时间。精确接缝定位右片段起点，成品末端单独处理。

后续算法须检查溢出、时间范围、来源存在和原始时长上限。删除成品区间时拆分交叠片段、保留两侧；不改源文件。撤销重做使用不可变编辑快照；恢复删除内容保留来源及足够历史信息，方案在阶段 4 落实。当前仅数据类型，没有映射、删除、撤销或恢复实现。

项目保存 `schemaVersion`、来源集合、歌曲顺序/片段和导出设置。阶段 3 实现版本校验、缺失引用错误、串行写入和临时 JSON 原子替换，避免乱序覆盖。是否跨会话保存撤销历史在阶段 4 明确，不得以丢失保留片段代替迁移。

## 文件引用和生命周期（设计，未接通）

来源用稳定 ID；外部为持久 URI，内部为应用受控相对路径。导入优先保留 SAF 持久读授权，处理 provider 不支持持久授权、撤销、文件消失；Photo Picker 不假设永久可用。需要长期使用且不能可靠保留时，按需受控内部复制，先检查容量、完成验证、去重及登记，不能每次切页复制。

规划内部 `files/results/` 正式成果、`files/sources/` 受控来源、`files/project/` 唯一项目、`cache/jobs/` 临时产物、可重建波形缓存。内部清理仅允许这些受控目录，不信任反序列化路径，不删除外部 URI。

成果和项目引用同一文件实体；成果记录移除不等于实体立即删除。项目和活动任务持有引用，引用全释放后方可清理。状态变更串行提交，启动时对照项目/成果索引清理无引用残留；先恢复索引再清理，损坏索引需保守保留并报错。阶段 3 建立引用设计，阶段 7 完成成果操作与清理。

任务输出先到临时文件，完成且检查真实媒体/大小后移到内部唯一名称，最后登记索引；异常回滚，不产生假成功。外部另存用 SAF 复制，唯一命名、不覆盖；保存授权仅尽可能复用，不保证 U 盘可用。

## 原生引擎（后续实施）

阶段 5 编写 JNI C++ 引擎，探测、波形、进度与取消；阶段 6 接通转换/渲染。服务层负责 URI 适配、任务调度与文件提交，UI 不拼 FFmpeg 命令。阻塞分析放后台；取消应传至原生任务并清理未完成产物。流复制需验证 MP3 音轨兼容且无音频变换。

loudnorm 按每首歌剪后内容分别分析并处理，分析参数用于处理，最后按顺序拼接编码；不将未归一化试听标为最终响度效果。Media3 片段列表遵循保留顺序，接缝精度受格式、解码和编码延迟影响，需本地验证。

16 KB：AGP 采用未压缩原生库打包策略；阶段 5 使用支持 16 KB 的 NDK/链接配置，检查全部自编与第三方 ELF LOAD 对齐及 APK 对齐，不能仅设 ABI 或 Gradle 标志就声称兼容。当前无 .so，尚无兼容性验证。

## 开源许可边界

Wrapper 保留 Apache-2.0 版权头，官方来源 https://github.com/gradle/gradle/tree/v8.11.1/gradle/wrapper ；JAR 校验记录见 LOCAL_VALIDATION。AndroidX/Compose、Kotlin、Coroutines、Serialization 等依赖的许可与通知在实际分发前按固定版本核对。阶段 1 未纳入任何 FFmpeg/LAME 源码或二进制，不以旧库许可替代未来实际配置的许可；阶段 5 必须随源码版本、编译选项记录 FFmpeg/LAME 及传递原生依赖的完整许可、源码提供与重链接义务，避免未经核实引入 GPL/nonfree 选项。当前没有可发布音频引擎。
