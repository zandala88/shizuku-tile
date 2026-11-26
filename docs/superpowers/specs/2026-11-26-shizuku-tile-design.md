# Shizuku 单磁贴

用户需要替代 MacroDroid：一个快捷设置磁贴切换 Shizuku 启停，尽量减少配置。

固定广播包名为 moe.shizuku.privileged.api；动作分别为该包名加 .START 和 .STOP。
已从上游 AuthenticatedReceiver 核实两条广播都使用字符串 extra `auth`。
用户提供的令牌仅写入被忽略的本地构建配置，生成个人安装包；可在应用内修改。

方案：Android 原生 Java，Android 8.0+，无运行时第三方库。使用上游保留的
REQUEST_BINDER 广播回调协议获取真实 Binder，再用 pingBinder 与死亡通知观察运行状态。
这样既能从磁贴冷启动检测，也不需要用户授予 Shizuku 执行权限。
直接集成完整 Shizuku API 会增加无需使用的执行接口；仅保存“上次点击”则会产生错误状态。

仅在磁贴展开或设置页可见时轮询；不设常驻服务，不申请网络、无障碍或 root 权限。
未知状态禁止切换；启动/停止等待真实状态确认，60 秒超时提示并保留实际状态。
连续点击不重复发送；锁屏操作先解锁；配置页提供令牌保存、添加磁贴与打开 Shizuku。
广播负责调用已安装 Shizuku 的启动能力，不另实现 ADB 配对或规避其启动条件。

验证：JVM 状态测试，Robolectric 广播/回调/界面测试，Android Lint，签名 APK 校验。
当前无连接 Android 设备，因此不得把构建通过表述为在用户手机上实测通过。
