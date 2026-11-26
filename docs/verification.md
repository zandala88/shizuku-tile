# 验证记录 — 2026-11-26

- `:app:testDebugUnitTest`：29 项，0 失败，0 错误。
- `:app:testReleaseUnitTest`：29 项，0 失败，0 错误。
- `:app:lintRelease`：通过，无问题。
- `:app:assembleRelease`：通过，R8 优化的正式包。
- `apksigner verify --verbose`：APK Signature Scheme v2 校验通过。
- `aapt dump badging`：包名 io.github.local.shizukutoggle，版本 1.0.0，minSdk 26，targetSdk 35。
- `aapt dump permissions`：无申请权限。

独立审查修正：

1. 冷启动管理器的首次空 Binder 保持未知，下次查询再次确认为空后才判定停止。
   如果已有仍能 ping 通的服务 Binder，空管理器回调不会将其覆盖。
2. 监视 Binder 死亡时注册失败，直接处理为已死亡，不取消从未注册的监听。
3. 未配置 auth 的磁贴允许点按打开设置，同时控制器禁止发送启停命令。
4. Shizuku 从未安装变为已安装后，清除过时的检测错误。

上述行为均有修正前失败、修正后通过的回归测试。

兼容性处理：Android 8.0 使用适配的导航栏样式；Android 14+ 使用 PendingIntent 折叠面板打开设置。
采用上游已导出的旧版 Binder 回调协议，避免申请 Shizuku 执行权限。

限制：未连接 Android 手机，没有进行实际下拉面板、无线调试启动、锁屏解锁和停止的端到端验证。
初次连续空 Binder 的确认是针对异步初始化的重试策略，最终兼容性仍需用户安装的分支和设备验证。
