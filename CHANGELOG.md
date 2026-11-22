# Changelog

## v1.1.1

- 开机自启前先检查 root：临时 root 重启后消失时，不再启动一个「点不开应用」的假悬浮球
- 文档：截图裁掉状态栏、CI 徽章固定 main 分支

## v1.1

- 新增 **root 守护进程**：MIUI「清理后台」后悬浮球自动恢复（`/data/local/tmp/.floatwin_watch.sh`）
- 关闭悬浮球时会同时停止守护进程，不会自动复活
- 移除实验性的「免 Root」启动方式（Android 16 hidden API 不可用）
- 启动后不再强制 `am task resize`，窗口尺寸交给系统（MIUI 会给内容一起缩放，观感更差）
- 主界面：Root 未授权时展示 KernelSU 引导与「打开 KernelSU」按钮；Root 状态随页面刷新
- 修复按钮状态与实际服务状态不同步的问题

## v1.0

- 初始版本：悬浮球、应用面板、搜索、`freeform` 启动、常驻通知、QS 磁贴
- 支持 `am` 自动化 intent（`start` / `stop` / `open` + `pkg`）
