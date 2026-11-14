# 原生开源组件

音频助手使用真实 FFmpeg 7.1.5 与 LAME 3.100，均由用户本地从锁定源码编译，动态链接到 JNI。
源码锁定与构建方法随仓库提供：native/sources.lock.json、scripts/、app/src/main/cpp/。

FFmpeg — Copyright (c) the FFmpeg developers
源码：https://github.com/FFmpeg/FFmpeg/tree/3a0867c2bfda4a4d4309ca1a8cbdc6175e67f587
主页：https://ffmpeg.org/
本配置禁用 GPL、version3、nonfree 和自动探测外部依赖。总体 LGPL 2.1 或以后版本；部分文件有 MIT/BSD 等许可，参见 FFmpeg-LICENSE.md 及对应源码版权头。
完整许可：FFmpeg-LGPL-2.1.txt。

LAME — Copyright (c) the LAME Project / respective contributors
主页：https://lame.sourceforge.io/
版本3.100；原始包 SHA-256：ddfe36cab873794038ae2c1210557ad34857a4b6bdc515785d1da9e175b1da1e
仅使用编码库，禁用命令行前端和解码器。GNU Library General Public License 2 或以后版本。
完整许可：LAME-LGPL-2.txt；GNU GPL 2 的配套文本：GPL-2.txt。

应用自有 JNI 不改变第三方原生库的许可。用户可按许可修改/替换/重新链接这些库，用仓库脚本重建应用；不得限制对库修改调试所需的逆向工程。
构建脚本对 LAME 生成的 libtool 作唯一 Android SONAME 调整（version_type=linux → none），不修改音频算法；全部脚本随源码交付。
分发 APK 时须随该 APK 提供完整对应的第三方源码、许可和实际构建脚本/配置（含此修改），或提供符合所适用许可的对应源码交付方式。上游链接和本说明不能代替该义务。
不应以签名、发布条款等阻止许可允许的库替换与调试。FFmpeg 其他配置或引入额外依赖时须重新核对许可。
NDK libc++ runtime 按其 Apache-2.0 with LLVM exceptions 许可交付；本地构建请一并保留所用 NDK 的 NOTICE 文本与对应 runtime 源码信息。
