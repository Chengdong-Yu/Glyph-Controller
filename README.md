# Glyph Controller

一款面向 **Nothing Phone (2)** 的 Android Glyph 灯带控制与编排应用。应用通过持久化 `su` root shell 直接读写设备的 Glyph LED `sysfs` 节点，无需依赖 Nothing 官方系统框架或 SDK。

应用界面全面基于 **[mochi-compose](https://github.com/feice11/mochi-compose) (`0.2.0`)** 与 **Nothing 排印及组件体系 (`mochi-nothing`)** 构建，支持 **Material Design 3 莫奈动态壁纸取色（Monet）**、自定义种子色板与全色域取色器（`MochiSeedPalette` + `MochiColorPickerSheet`）、多种配色风格（柔和色调 / 灵动表现 / 鲜明活力 / 极简单色），以及针对独立数值与硬件编码的点阵字体（`Ndot57`）显示。

> 当前版本针对 Nothing Phone (2) 的 33 区灯带硬件节点实现。Nothing Phone (1) 的区域数量与驱动映射不同，尚未适配。

## 核心特性

- **动态莫奈取色与 Mochi 导航交互架构：**
  - **总览 (`Overview`)：** 搭载主题光晕联动的 33 区矢量背板舞台、点阵滚轮计数器（`MochiCounterText` + `nothingDotTextStyle`）、平均亮度线性仪表（`MochiLinearGauge`）、一键全亮/全灭总控，以及**自动灯效控制区**（通知闪烁、免打扰自动静默、充电流光、0ms 延迟 16 段 C1 音量指示环；首次开启通知闪烁时自动弹窗引导开启系统通知使用权）。
  - **工坊 (`Studio`)：** 真实 Phone (2) 背板舞台，支持**点按与连续拖动选区**、主题色光晕与选区描边、**单灯段 / 整组联动**双模式（`MochiSegmentedControl`）、6 组解剖分区筛选、带阻尼刻度吸附的 `MochiListSliderRow`、批量帧编辑按钮组（`MochiButtonGroup`）、**可视化快捷预设画廊**（`MochiSelectFrame`），以及内置 4 槽位缩略预览并支持容器变形转场（`mochiNavSource`）的**自定义存档卡片**。
  - **场景槽位页 (`Scene Vault`)：** 4 槽位自定义场景管理（槽位 1 自动兼容旧版存档），支持从工坊存档卡片展开变形或从顶栏推入、共享顶栏按钮变形，以及左滑露出清空操作（`MochiSwipeRow`）。
  - **设置 (`Settings`)：** 集中呈现**运行状态**（Glyph 硬件、通知使用权、`service.d` 看门狗）、**主题与配色**（深浅色揭开转场 `MochiThemeRevealHost`、Material Design 3 莫奈动态取色开关、4 种配色风格、9 款精选色块 + 全色域自定义取色器、衬线标题开关）、硬件灯效一键试播，以及保活看门狗管理。
- **底层性能与 120Hz 渲染架构优化：**
  - **纯 GPU 灯带光晕与双层背板舞台 (`GlyphPhonePreview`)：** 彻底移除 CPU 光栅化的 `BlurMaskFilter`，采用纯 GPU 硬件多层半透明描边合成柔光光晕；背板底图一次性预裁剪并缩放为 `RGB_565` 缓存于独立硬件 `RenderNode` 层，33 区灯带叠加层仅在 Compose `Draw` 阶段惰性读取状态，实现 120Hz 滑块拖动 **0 重组、0 布局、0 底图重绘**；预设与存档缩略图通过 `graphicsLayer()` 缓存显示列表，滚动零重绘。
  - **零离屏录制导航与原生 120Hz 锁定 (`GlyphTabScaffold` / `GlyphAppTheme`)：** 关闭纯色主题下冗余的全屏 `LayerBackdrop` 离屏玻璃录制（`MochiNavHost(windowGlass = false)` + 定制纯色 `GlyphTabScaffold`），仅挂载当前活跃标签页并保留滚动位置；在不放大模糊半径的前提下自动匹配屏幕当前分辨率最高刷新率（120Hz LTPO）。
  - **叶子级状态隔离与结构化快照 (`@Immutable GlyphSnapshot` / `GlyphStudioScreen`)：** `GlyphSnapshot` 提供单遍扫描 `withLevel` / `withRange` 无变化零分配快路径；工坊页各胶囊与预设卡片通过独立 `derivedStateOf` 按项订阅，消除整页重组与模糊滚轮风暴；滚动项统一采用免裁切背景或硬件 `Outline.Rounded` 裁切。
  - **低延迟 Root `sysfs` 管道与协程合并 (`GlyphEffectCoordinator` / `RootGlyphClient`)：** 缓存硬件验证状态省去每次写灯前的二次 `su` 探测（IPC 延迟减半），合并 `onResume` 恢复检查窗口；单缓冲区复用通知与充电序列，`StringBuilder` 单遍格式化 `frame_brightness` 命令并跳过重复异步帧刷新。
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
  - `GlyphAppTheme`：唯一根级 `MochiTheme` 入口，接入 MD3 莫奈动态取色与自定义种子色板（`ThemeController`）、`nothingColorScheme`、`nothingControlPalette`、`nothingTextStyles`、纯色 `MochiMaterials()`、`GlyphHighRefreshRateEffect`、`MochiThemeRevealHost` 与 `ProvideMochiOverscroll`。
  - `GlyphWorkbenchController`：跨 Activity 共享的 `@Stable` 状态与硬件交互控制器。
  - `MainActivity` / `GlyphStudioScreen` / `GlyphUiComponents` / `GlyphDetailActivity`：基于 `MochiNavHost(windowGlass = false)`、零离屏录制的 `GlyphTabScaffold` 与 `MochiLazyCompactTitleScaffold` 的总览、工坊、场景槽位与设置页面。
  - `GlyphPhonePreview`：`RGB_565` 预裁剪双层背板渲染与连续滑动选区舞台，以及带 `RenderNode` 列表缓存的纯矢量预览器 `GlyphVectorPreview`。
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
