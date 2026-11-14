# 开发接续规则

先阅读 docs/ 下六份长期文档和当前 Git 状态，以 PRODUCT_SPEC.md 为产品依据，不根据旧代码推断需求。
严格按阶段执行；每阶段更新文档、提交，按授权流程推送后停止，等待用户继续。
云端禁止执行任何 Gradle build/test/lint、Android/NDK/CMake 编译、模拟器或等效测试，不为验证安装 SDK/NDK 或下载大型依赖。
允许源码、资源、数据结构及 Git diff 静态检查；每阶段最多一次 git diff --check。
不得将编写完成称为编译或设备验证通过。不得创建虚假音频实现、伪造原生库或成功状态。
保留历史，不强推，不提交构建产物。当前交付仓库为 GZR-zhuanjia/AndroidAudioEditor（GitHub 已从 AndriodAudioEditor 更名），允许阶段性直接推送 main。
