# Shizuku 磁贴

一个原生 Android 小应用，用一个快捷设置磁贴启动、停止 Shizuku，替代 MacroDroid。
Android 8.0 及以上，无第三方运行时依赖，无网络、无障碍、root 或通知权限申请。

1.2.2 将桌面图标改为黑白版本以区别原应用，磁贴使用 Shizuku 图形，启停时保留旋转等待效果。
界面保持相近的靛蓝配色，支持浅色、深色和系统主题图标。
图标来源与 Apache 2.0 许可保存在 [third_party/shizuku](third_party/shizuku/NOTICE)，也随 APK 打包。
本应用是独立的磁贴控制工具，仍需安装支持启停广播的 Shizuku。

## 使用

1. 将提供的 APK 传到手机并安装。
2. 打开“Shizuku 磁贴”，点击“添加 Shizuku 磁贴”；旧系统在快捷设置的编辑界面手动拖入。
3. 点按磁贴切换启停。运行时亮起，停止时熄灭；操作中显示旋转圆环和等待文字，重复点按不会重复发送命令。
4. 长按磁贴打开设置。如果 Shizuku 的授权码变更，只需更新 auth 一项。

## 停止时关闭 USB 调试

1.2.0 新增“停止行为 → 同时关闭 USB 调试”，默认关闭。
开启后，通过本磁贴发起停止并确认 Shizuku 已停止，才会关闭 USB 调试。
关闭开关后保留原行为；启动仍使用 Shizuku 原来的启动机制，不改变无线调试设置。

该功能需要一次 ADB 授权：

```sh
adb shell pm grant io.github.local.shizukutoggle android.permission.WRITE_SECURE_SETTINGS
```

未授权时不能开启选项，页面提供复制命令按钮。授权只控制是否能修改系统设置，
是否执行关闭仍由开关决定。正常重启保留授权；卸载重装后需要重新授权。

关闭 USB 调试会断开 USB ADB 连接。重新连接电脑需要在手机上打开 USB 调试，
或按当前 Shizuku 分支原有启动流程重新开启。
外部关闭 Shizuku、启动操作、停止超时和广播失败均不触发此动作。
如果停止期间取消开关，将取消尚未执行的 USB 关闭；中途开启只对下一次停止生效。

个人安装包已预置本次提供的 auth。源码分发不包含个人授权码和签名私钥；自行编译时可在安装后填写。

## 兼容范围

面向用户当前使用的、提供 START / STOP 广播的 Shizuku 分支。
普通官方版或其他分支若未实现这些广播，不能由本应用凭空增加该能力。
应用沿用 Shizuku 的无线调试、配对、root 等原有启动机制。
如启动需要 Wi-Fi，或重启后需要重新配对，仍需满足 Shizuku 的条件。

两条命令均为 Broadcast Receiver：

| 操作 | Action | Package | 字符串 Extra |
|---|---|---|---|
| 启动 | moe.shizuku.privileged.api.START | moe.shizuku.privileged.api | auth |
| 停止 | moe.shizuku.privileged.api.STOP | moe.shizuku.privileged.api | auth |

状态通过 `rikka.shizuku.intent.action.REQUEST_BINDER` 的 Binder 回调与死亡通知读取。
通常只在设置页可见或快捷设置面板展开时轮询；开启 USB 联动并已发起停止时，
即使收起面板也会继续短暂确认，最长不超过该次操作的 60 秒期限。
广播已发送不等于启停成功；60 秒未确认则提示超时，且不再关闭 USB 调试。
读不到状态时保持未知，不假装已停止。启停中的状态可跨服务/进程重建恢复，避免连续发送。

## 构建

使用 JDK 17、Android SDK 35、Gradle 8.9。首次使用 Gradle Wrapper 需联网下载工具和依赖。

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

本机已配置工具时可以用 `./scripts/build-local.sh` 替代 `./gradlew`。

生成签名正式包：

```sh
python3 scripts/prepare-signing.py
./scripts/build-local.sh :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease
```

可选：本地创建 `auth.properties`，内容为 `auth=你的授权码`，预填个人安装包。
该文件与 `signing.properties` 均被忽略，请勿把个人安装包当作不含授权信息的通用版本分发。
签名私钥保存在 `~/.local/share/shizuku-tile/`，后续覆盖安装须继续使用同一私钥。

## 验证边界

自动测试覆盖广播内容、Binder 回调、状态未知、重复点击、超时、进程重建、停止轮询和设置保存。
Android Lint 检查 API 版本兼容。1.1.0 已通过 ADB 安装到 PJZ110（Android SDK 37），检查深色界面，并验证磁贴停止、启动及等待状态显示；其他系统版本仍以自动测试和 Lint 为验证依据。

## 接口依据

- [Shizuku 分支的启停功能说明](https://github.com/thedjchi/Shizuku)
- [鉴权广播实现](https://github.com/thedjchi/Shizuku/blob/master/manager/src/main/java/moe/shizuku/manager/receiver/AuthenticatedReceiver.kt)
- [Binder 回调协议](https://github.com/thedjchi/Shizuku/blob/master/manager/src/main/java/moe/shizuku/manager/shell/ShellBinderRequestHandler.kt)
- [Android 快捷设置磁贴](https://developer.android.com/develop/ui/views/quicksettings-tiles)
- [Android USB 调试系统设置](https://developer.android.com/reference/android/provider/Settings.Global#ADB_ENABLED)
