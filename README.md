# Glyph Controller

一款面向 **Nothing Phone (2)** 的 Android Glyph 灯带控制应用。应用通过 root 直接写入设备的 Glyph LED 节点，无需 Nothing 官方 SDK。界面采用 Miuix Compose，提供 33 区亮度控制、场景预设、通知闪烁、充电流光和快捷设置开关。

> 当前版本仅针对 Phone (2) 的 33 区灯带实现。Nothing Phone (1) 的区域数量与映射不同，尚未适配。

## 功能

- **总览：** 一键点亮或熄灭全部灯带，开关通知闪烁与充电流光。
- **详细控制：** 在 Phone (2) 后盖图上选择灯带，分别调节 33 个区域的亮度（0–255），并一次应用整帧。
- **区域名称：** 按 Glyph Developer Kit 的 Phone (2) 顺序显示 A1、A2、B1、C1_1–C1_16、C2–C6、D1_1–D1_8 和 E1。
- **场景：** 提供全部点亮、柔和常亮、C 环、A 与 D/E、全部熄灭预设；还可保存和读取一套自定义场景。
- **自动效果：** 收到通知时依次闪烁三组灯带；通知闪烁开启后可单独设置免打扰时是否暂停。接入充电器时播放由下往上的流光。按下音量加减键时，C 环会立即显示调整后的音量。效果结束后恢复上次应用的手动场景。
- **快捷设置：** 从系统快捷设置开关全部灯带。
- **设备检测与保活：** 启动时检查 Glyph 节点；默认在支持的 root 环境中安装 `service.d` 看门狗，也可在主页一键安装、重启或移除，尝试维持通知监听和充电触发。
- **状态恢复：** 进程重新启动时恢复上次应用的手动场景；详细控制页在旋转或系统重建后恢复编辑草稿和选中区域。
- **故障提示：** 区分未获得 root、节点不存在和检测失败，提供重试；通知状态根据监听服务的实际连接显示。

## 设备与权限要求

- Android 13 或更高版本（`minSdk 33`）。
- Nothing Phone (2) 的 33 区 Glyph 硬件，以及可写的 `/sys/class/leds/led_strips/` 节点。设备没有所需节点时，应用会提示不支持。
- 可用的 `su` root 权限。通知闪烁还需要在系统设置中授予通知使用权。
- 保活功能需要支持 `/data/adb/service.d/` 的 root 环境，例如相应配置的 KernelSU 或 Magisk。不同 ROM 的后台限制可能影响效果。

硬件识别以实际 Glyph 节点为准；仅凭系统显示的品牌或型号无法可靠判断混刷设备的硬件能力。

## 安装与使用

1. 从 [Releases](https://github.com/Chengdong-Yu/Glyph-Controller/releases) 下载 APK 并安装。
2. 打开应用，授予 root 权限。主页显示硬件和灯带状态。
3. 在主页使用总开关，或进入「详细控制 Glyph」选择区域、调节亮度并点按「应用到 Glyph 灯带」。预设和保存的场景在应用前只改变预览。
4. 如需通知闪烁，打开对应开关并点按「授权通知使用权」。如需充电流光，保持对应开关开启。
5. 如需快捷开关，在系统快捷设置编辑页添加 Glyph 图块。

应用会在支持的 root 环境中默认把看门狗脚本安装到 `/data/adb/service.d/glyph_keepalive.sh`。可在主页「后台」区域一键安装、重启或移除脚本；手动移除后应用会记住选择，不再自动重装，直至再次点击安装。

## 构建

需要 **JDK 21** 和 Android SDK（API 37）。构建会从 Maven 仓库获取 Miuix 0.9.4、Compose 和 AndroidX Activity Compose。在 Windows PowerShell 7 中：

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-21'
$env:ANDROID_HOME = 'C:\path\to\Android\Sdk'
.\gradlew.bat assembleRelease
```

生成的 APK 位于 `app/build/outputs/apk/release/app-release.apk`。

在 macOS 或 Linux 中使用随项目提供的 POSIX wrapper：

```sh
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleRelease
```

### Release 签名

release 变体会按以下顺序查找签名配置：

1. 仓库根目录的 `keystore.properties`（已被 `.gitignore` 忽略，**切勿提交**），内容如下：

   ```properties
   storeFile=/path/to/glyph-release.jks
   storePassword=******
   keyAlias=glyph
   keyPassword=******
   ```

2. 环境变量 `GLYPH_KEYSTORE_FILE`、`GLYPH_KEYSTORE_PASSWORD`、`GLYPH_KEY_ALIAS`、`GLYPH_KEY_PASSWORD`。

两者都不存在时会回退到 **debug 签名**，因此贡献者无需密钥也能构建，但这样产出的 APK 与正式发布版签名不同，不能互相覆盖安装。密钥库（`*.jks`、`*.keystore`）和密码不在仓库中，请妥善备份：密钥丢失后将无法发布可覆盖安装的更新。

Release signing (English): `./gradlew assembleRelease` reads `keystore.properties` in the repo root (git-ignored) or the `GLYPH_KEYSTORE_*` environment variables listed above; without them it falls back to debug signing. Never commit the keystore or its passwords.

## 实现概览

- `RootGlyphClient` 通过持久化的 `su` shell 写入 33 值 `frame_brightness`。
- `MainActivity` 和 `GlyphDetailActivity` 使用 Miuix Compose；`GlyphPhonePreview` 在 Compose 画布中绘制真实后盖图和 33 个可点选区域。
- `GlyphEffectCoordinator` 协调手动操作、通知闪烁和充电流光，防止同时写入相互覆盖。
- `GlyphSession` 管理效果优先级和恢复；`GlyphRuntime` 合并后台恢复请求，`GlyphListenerState` 跟踪通知监听连接。
- `GlyphPowerState` 保存已应用的亮度帧；`GlyphSceneStore` 单独保存一套自定义场景。
- `RootKeepAlive` 安装应用内置的 `glyph_keepalive.sh`，脚本在开机后监控应用进程和充电状态。

看门狗的 Android 框架命令有超时限制，失败后逐步延长重试间隔（最多 120 秒）。最近一次失败记录在 `/data/adb/glyphcontroller/last_failure`，日志标签为 `GlyphKA`。进程仍存活时也会定期请求应用检查通知监听连接。

Release 开启 R8 代码压缩、混淆和资源裁剪；图标与后盖图采用保留原尺寸的无损 WebP。发布时应一并保存 `app/build/outputs/mapping/release/mapping.txt`，用于还原混淆后的崩溃堆栈。

Phone (2) 的区域名称和后盖图参考 [Nothing Glyph Developer Kit](https://github.com/Nothing-Developer-Programme/Glyph-Developer-Kit)。

应用界面使用 [Miuix 0.9.4](https://github.com/compose-miuix-ui/miuix)（Apache-2.0）。

## 许可证

[MIT](LICENSE)
