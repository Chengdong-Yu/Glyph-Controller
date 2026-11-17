# Glyph Controller

一款面向 **Nothing Phone (2)** 的 Android Glyph 灯带控制与编排应用。应用通过持久化 `su` root shell 直接读写设备的 Glyph LED `sysfs` 节点，无需依赖 Nothing 官方系统框架或 SDK。

应用界面全面基于 **[mochi-compose](https://github.com/feice11/mochi-compose) (`0.2.0`)** 与官方 **Nothing 外观模块 (`mochi-nothing`)** 构建，采用纯粹的暖调单色层级、标志性 `#D71921` Nothing 红点缀、系统衬线标题 + Inter 正文排版，以及针对独立数值与硬件编码的点阵字体（`Ndot57`）显示。

> 当前版本针对 Nothing Phone (2) 的 33 区灯带硬件节点实现。Nothing Phone (1) 的区域数量与驱动映射不同，尚未适配。

## 核心特性

- **纯正 Nothing 外观与 Mochi 导航交互架构：**
  - **总览 (`Overview`)：** 搭载可点触交互的 33 区矢量背板预览、点阵滚轮计数器（`MochiCounterText` + `nothingDotTextStyle`）、平均亮度线性仪表（`MochiLinearGauge`）、一键全亮/全灭总控、7 款快捷预设筛选条（`MochiChipGroup`），以及支持容器变形转场（`mochiNavSource`）的自定义场景卡片与运行状态列表。
  - **工坊 (`Studio`)：** 真实 Phone (2) 背板舞台，支持**点按与连续拖动选区**、柔和白光扩散与 Nothing 红选区描边、**单灯段 / 整组联动**双模式（`MochiSegmentedControl`）、6 组解剖分区筛选、带阻尼刻度吸附的 `MochiListSliderRow`、批量帧编辑按钮组（`MochiButtonGroup`：同步本组 / 铺满全区 / 亮度反相 / 亮度减半），以及 `MochiSelectFrame` 可视化预设画廊。
  - **场景槽位页 (`Scene Vault`)：** 4 槽位自定义场景管理（槽位 1 自动兼容旧版存档），支持从总览卡片展开变形或从工坊顶栏推入、共享顶栏按钮变形，以及左滑露出清空操作（`MochiSwipeRow`）。
  - **设置 (`Settings`)：** 集中管理通知三段闪烁（含免打扰自动静默与通知使用权引导）、充电流光、0ms 延迟 16 段 C1 音量指示环、硬件灯效一键试播、`service.d` 保活看门狗（移除操作经 `MochiDialog` 二次确认），以及 Nothing 深浅色揭开转场（`MochiThemeRevealHost`）与衬线标题切换。
- **底层性能与状态架构优化：**
  - **Compose 结构化快照 (`@Immutable GlyphSnapshot`)：** 消除 `IntArray` 引用相等导致的冗余重组。
  - **响应式状态控制器 (`@Stable GlyphWorkbenchController`)：** 合并 `MainActivity` 与 `GlyphDetailActivity` 重复逻辑，使用 `StateFlow` 与系统免打扰广播替代原有的 1 秒轮询协程。
  - **零额外分配硬件灯效 (`GlyphEffects`) 与 `sysfs` 写入去重 (`RootGlyphClient`)：** 单缓冲区复用通知与充电序列，`StringBuilder` 单遍格式化 `frame_brightness` 命令并跳过重复异步帧刷新；33 区背板 `Path` 与采样点升级为进程级单例缓存。
- **快捷设置与状态恢复：** 提供系统快捷设置图块（QS Tile），进程重启或自动灯效播放结束后自动恢复当前常亮手动场景。

## 设备与权限要求

- Android 13 或更高版本（`minSdk 33`）。
- Nothing Phone (2) 的 33 区 Glyph 硬件及可写的 `/sys/class/leds/led_strips/` 节点。
- 可用的 `su` root 权限；通知闪烁需在系统设置中授予通知使用权。
- 后台保活功能需要支持 `/data/adb/service.d/` 的 root 环境（如 KernelSU 或 Magisk）。

## 构建

需要 **JDK 21** 和 Android SDK（API 37）。构建会从 Maven Central 获取 `io.github.feice11:mochi-compose:0.2.0`、`mochi-nothing:0.2.0`、`mochi-icons:0.2.0` 及 AndroidX Activity Compose。

在 macOS 或 Linux 中构建并运行单测与 Lint 检查：

```sh
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=/path/to/android-sdk
./gradlew testDebugUnitTest lintDebug assembleDebug
```

在 Windows PowerShell 7 中构建开发版：

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-21'
$env:ANDROID_HOME = 'C:\path\to\Android\Sdk'
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

生成的开发版 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

### Release 签名

`assembleRelease` 与 `bundleRelease` 按以下顺序读取签名配置：

1. 仓库根目录的 `keystore.properties`（已被 `.gitignore` 忽略，切勿提交）：

   ```properties
   storeFile=/path/to/glyph-release.jks
   storePassword=******
   keyAlias=glyph
   keyPassword=******
   ```

2. 环境变量 `GLYPH_KEYSTORE_FILE`、`GLYPH_KEYSTORE_PASSWORD`、`GLYPH_KEY_ALIAS`、`GLYPH_KEY_PASSWORD`。

两者都不存在时正式版打包会直接中止，开发调试请使用 `assembleDebug`。

## 模块架构概览

- **UI 与状态控制层（Mochi + Nothing）：**
  - `GlyphAppTheme`：唯一根级 `MochiTheme` 入口，接入 `nothingColorScheme`、`nothingControlPalette`、`nothingTextStyles`、纯色 `MochiMaterials()`、`MochiThemeRevealHost` 与 `ProvideMochiOverscroll`。
  - `GlyphWorkbenchController`：跨 Activity 共享的 `@Stable` 状态与硬件交互控制器。
  - `MainActivity` / `GlyphStudioScreen` / `GlyphUiComponents` / `GlyphDetailActivity`：基于 `MochiNavHost`、`MochiTabScaffold` 与 `MochiLazyCompactTitleScaffold` 的总览、工坊、场景槽位与设置页面。
  - `GlyphPhonePreview`：进程级缓存的 33 区 Phone (2) 背板渲染与连续滑动选区画布，以及纯矢量预览器 `GlyphVectorPreview`。
- **硬件与后台服务层：**
  - `GlyphFrame` & `GlyphZones`：`@Immutable GlyphSnapshot` 帧模型、33 区解剖分组、光学统计与 4 槽位场景持久化（`GlyphSceneStore`）。
  - `RootGlyphClient`：持久化 `su` shell 同步/异步写入 33 值 `frame_brightness`，含重复帧过滤与预分配命令缓冲。
  - `GlyphEffects` / `GlyphEffectCoordinator` / `GlyphSession`：零额外分配通知闪烁与充电流光序列，按优先级租约（`NOTIFICATION < CHARGING < VOLUME`）串行管理灯带并恢复常亮帧。
  - `GlyphVolumeCoordinator`：结合 `/dev/input/event*` root 预判与音量广播实现 0ms 延迟 C1 16 段音量环动画。
  - `RootKeepAlive`：管理 `/data/adb/service.d/glyph_keepalive.sh` 看门狗守护进程。

## 致谢与许可证

- Phone (2) 区域命名与背板参考 [Nothing Glyph Developer Kit](https://github.com/Nothing-Developer-Programme/Glyph-Developer-Kit)。
- UI 组件库与 Nothing 外观主题采用 [feice11/mochi-compose (`0.2.0`)](https://github.com/feice11/mochi-compose)（Apache-2.0；内置 Inter / Ndot57 / Ionicons 遵循其各自开源许可）。
- 本项目基于 [MIT](LICENSE) 许可证开源。
