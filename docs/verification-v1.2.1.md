# 1.2.1 验证记录 — 2026-11-26

图标使用 RikkaApps/Shizuku 固定版本 b844bc491f1790c72328e1a8e5b2349f8978f0ea 的原始资源，
浅色主色为 #3F51B5，深色主色为 #B1B8DF。适配图标支持普通和系统主题模式；设置页标识不再被染色。
图标来源、变更说明与 Apache 2.0 LICENSE 位于 third_party/shizuku，并随 APK 打包。

- 干净构建通过，Debug 和 Release 各 54 项测试，0 失败，0 错误。
- Android Lint 无问题，APK 签名验证通过。
- APK 内确认含 assets/shizuku/LICENSE 与 assets/shizuku/NOTICE。
- 独立审查核对 12 个 PNG、单色矢量和许可证与固定上游版本一致，无实质问题。
- 原签名覆盖安装到 PJZ110（Android SDK 37），确认 versionCode=4、versionName=1.2.1。
- 真机深色界面截图确认：原版图标、蓝紫色主按钮、状态、卡片、开关及边框显示正常。
- 配色计算：浅色/深色正文对比度 17.11/13.03，次要文字 4.91/6.95，主按钮 6.87/6.74。

本次仅调整图标、主题和版本；未在真机测试中操作启停或 USB 调试开关。
个人 APK 预填授权码，仅在本地提供；公有 GitHub 仓库只推送无本地授权码和签名配置的源码。
