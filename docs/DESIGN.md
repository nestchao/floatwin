# 设计说明 · 小窗（FloatWin）

本文记录这个 App 的设计决策、构建流程、验证结果和后续扩展点，供贡献者参考。

## 1. 背景与目标

需求：在 Redmi Pad SE 8.7（HyperOS 3 / Android 16，KernelSU 临时 root）上，用一种「精致、极简、好用」的方式，
把任意应用开成悬浮窗（freeform 小窗），因为该 ROM 砍掉了分屏/小窗入口。

约束（都已实测确认）：

| 事实 | 验证方式 |
|---|---|
| 系统支持 freeform | `am start --windowingMode 5` 后 `dumpsys activity` 出现 `mode=freeform` |
| 设备是锁 BL + 临时 root（KSU LKM 由 exploit 注入） | `ro.boot.flash.locked=1`、`/proc/modules` 有 kernelsu |
| 普通 App 无法执行 `su` | 未在 KSU 超级用户里允许时 `Runtime.exec("su")` 抛 `error=13` |
| KSU 允许后 `su` 正常 | 在 KernelSU → 本应用 → Superuser 打开后 `su rc=0` |
| 非 root 的 `ActivityOptions.setLaunchWindowingMode` 不可用 | 反射调用抛 `NoSuchMethodException`（hidden API） |
| 可对 freeform 任务改尺寸/位置 | `am task resize <id> <l> <t> <r> <b>`，单位 px |

## 1.5 保活（v1.1）

问题：MIUI 的「清理后台」用 `ProcessSceneCleaner.handleKillAll` 直接 kill 进程（adj 50, OneKeyClean），
前台服务也挡不住，日志里能看到 `removetask ... KillAll`。

方案：root 守护进程（`Watchdog.java`）

- 悬浮球启动时把脚本写到 `/data/local/tmp/.floatwin_watch.sh`，`touch .floatwin_watch.on`，`setsid` 起来；
- 脚本每 4s 检查 `pidof com.local.floatwin`，没了就 `am start-foreground-service -n com.local.floatwin/.BubbleService`；
- 用 `.floatwin_watch.lock/pid` 去重，避免多份；
- 关闭悬浮球时删 flag、kill pid，脚本退出。

实测：`kill -9` 后 ~2s 恢复；`am force-stop` 后 ~8s 恢复；MIUI「Clear all」后进程/悬浮球仍在。
注意：守护进程是 uid 0 的临时 root，重启后随 root 一起消失（重启后需要重新跑 exploit，这是设备本身的限制）。

## 2. 架构

```
MainActivity ──启动/停止──▶ BubbleService（前台服务，TYPE_APPLICATION_OVERLAY 悬浮球）
      │                          │ 点按
      │                          ▼
      │                     PanelWindow（全屏 scrim + 圆角面板 + GridView + 搜索）
      │                          │ 选中
      ▼                          ▼
   Prefs/权限状态           Launcher（root: su → am start --windowingMode 5 → am task resize）
```

- **无 Gradle、无 AndroidX**：纯 framework API，`aapt2 + javac + d8 + apksigner` 直接出包，APK ≈ 50 KB。
- **Shell 封装**：`Shell.su()` 用 `ProcessBuilder("su")` 从 stdin 写入命令，避免任何引号转义问题；
  读输出带超时，失败时回退 `/data/local/tmp/su` 和 `su` 两个候选路径。
- **启动命令**：`am start --windowingMode 5 -n <pkg/act> --user 0`。
  试过追加 `am task resize <taskId> <l> <t> <r> <b>`：任务边界确实改了，但 MIUI 会把应用内容
  一起缩放（Bilibili 从 486×357 变成 205×370），观感更差，因此回退为只用系统默认窗口。
- **悬浮球**：54dp 圆形，`FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_NO_LIMITS`，
  拖动超过 6dp 视为拖拽，松手用 `ValueAnimator` 吸边；点按开面板；长按（>480ms）回主界面。
- **面板**：全屏 scrim（`#4D000000`）+ 圆角面板（570×~860px），`FLAG_LAYOUT_IN_SCREEN`、`SOFT_INPUT_ADJUST_RESIZE`；
  返回键、点空白、X 都能关；出现动画 140/210ms。

## 3. 权限与首次配置

1. `SYSTEM_ALERT_WINDOW`（悬浮球/面板）——可用 root `appops set ... allow` 免手动。
2. KernelSU Superuser 开关——必须手动一次；App 里检测到 root 失败会显示引导 +「打开 KernelSU」按钮。
3. `POST_NOTIFICATIONS`（Android 13+）——启动悬浮球时请求；拒绝也能跑，只是没通知。

## 4. 构建

`build.ps1` 步骤：

1. `aapt2 compile --dir app/res -o build/res.zip`
2. `aapt2 link ... --java build/gen --min-sdk-version 29 --target-sdk-version 36`
3. `javac -source 8 -target 8 -bootclasspath android.jar`（含生成的 R.java）
4. `d8 --release --min-api 29`
5. 用 `ZipArchive` 把 `classes.dex` 塞进 APK
6. `zipalign -f -p 4` → `apksigner sign`（自动生成 debug.keystore）
7. `-Install` 时 `adb install`，若失败改用 `su -c 'pm install -r -t ...'`（MIUI 会拦普通安装）

## 5. 验证清单（已跑通）

- [x] 悬浮球显示/拖动/吸边/长按回主界面
- [x] 面板打开、搜索过滤（bilibili / McDonald's）、键盘不挡搜索框
- [x] 从面板启动 Bilibili / Chrome / CoolApk / 微信 → `mode=freeform`
- [x] 主界面权限状态（悬浮窗 / Root）实时刷新
- [x] `--es action open --es pkg ...` 自动化入口
- [x] 重装后重新授权流程正常
- [x] `am task resize` 实验（最终未启用，见 §2）

## 6. 已知限制

- MIUI 这台平板没有给小窗画顶栏（AOSP freeform 只有圆角/阴影），窗口管理依赖系统手势/最近任务。
- `am task resize` 的尺寸是任务级；个别应用（Chrome）窗口内容可能小于任务边界。
- 非 root 路线（`ActivityOptions`）在 Android 16 上不可用，代码里保留了 `Launcher.openDirect` 作为其他 ROM 的备份，
  UI 不再暴露该选项。
- 悬浮球位置存在 `Prefs`，但屏幕旋转后不重新夹取。

## 7. 后续可以做的

1. **窗口控制**：启动后为每个 freeform 任务加一个我们自己的"迷你控制条"（移动/关闭/resize）——用 overlay 窗口 + `am task resize`，
   或者反过来用 `input` 手势驱动系统 resize。
2. **默认尺寸配置**：目前写死 64%×62%；可做成设置项/记忆每个应用的尺寸。
3. **多窗口**：面板长按 → 固定为"常用"，重复启动即叠窗；再提供"全部关闭"。
4. **Shizuku 模式**：给非 root 但装了 Shizuku 的设备用（`Shizuku.newProcess` 执行同样的 am 命令）。
5. **小窗历史**：记录最近打开的应用，面板顶部展示。
6. **MIUI 原生入口**：如果以后解锁 BL / 上 LSPosed，可把 MaxFreeForm 那套入口接回来，本 App 退化成快捷入口。

## 8. 文件与职责

| 文件 | 职责 |
|---|---|
| `MainActivity.java` | 状态卡、权限引导、开关悬浮球、`action` intent |
| `BubbleService.java` | 前台服务、悬浮球触摸/吸边、启动面板与 App |
| `PanelWindow.java` | 全屏 scrim + 面板、搜索、GridView、出入场动画 |
| `AppRepo.java` | `queryIntentActivities` 拉应用列表，图标/标签缓存 |
| `Launcher.java` | root 启动 + resize；`openDirect` 备份；`hasRoot`/`grantOverlay` |
| `Shell.java` | `su`/`sh` 执行、超时、输出解析 |
| `Prefs.java` | bubble_on、坐标 |
| `BootReceiver.java` | 开机恢复（需已授权） |
| `Tile.java` | 快捷设置磁贴 |
