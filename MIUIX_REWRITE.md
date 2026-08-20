# KrScript Miuix 重写进度

目标：用 Compose + Miuix 在 Android 单平台完整重写 kr-scripts。

## 已完成

- [x] Fork 并初始化新工程结构
- [x] 接入 Miuix 依赖（miuix-ui / miuix-preference / miuix-icons）
- [x] 建立 `core` 模块，移植核心 Node 模型
- [x] 移植 `PageConfigReader`（XML → Node 模型），通过 `ShellRunner` 解耦 Shell
- [x] 移植简化版 `PathAnalysis`（assets / 相对路径）
- [x] 移植简化版 `ExtractAssets`（assets 资源解压）
- [x] 添加 `RootShellRunner`（sh + su fallback）
- [x] 添加 `sample.xml`，App 启动后真实解析并展示 Miuix 列表
- [x] 节点列表渲染：
  - Page / Action → ArrowPreference
  - Switch → SwitchPreference
  - Picker → OverlayDropdownPreference
  - Text → SmallTitle / 富文本行
  - Group → 分组小标题
- [x] 子页面导航（返回栈）
- [x] 页面搜索（SearchBar）
- [x] PageNode 外链浏览器打开
- [x] 在线页 WebView
- [x] 底部导航（首页 / 页面 / 收藏 / 关于）
- [x] 首页设备信息（型号 / Android / CPU / 每核频率 / 内存 / 存储 / 电池 / 温度 / 运行时间 / 使用率进度条）
- [x] 收藏夹（SharedPreferences 持久化）
- [x] 桌面快捷方式（收藏页创建）
- [x] 自定义文件选择器（首页入口）
- [x] 电源菜单（重启 / Recovery / Bootloader / 关机）
- [x] Splash 启动页
- [x] Action 参数弹窗：
  - TextField
  - SliderPreference
  - CheckboxPreference
  - OverlayDropdownPreference
  - ColorPicker
  - 文件选择
  - 多选

## 进行中 / 待办

- [x] 移植简化版 `ScriptEnvironment`（executor.sh 环境变量 + 缓存脚本执行）
- [x] 简易 `KeepShellRunner` 常驻 Shell（su/sh + 标记分割输出）
- [x] 文件选择器已接入 Action 参数
- [x] 实时 CPU / 电池监控页 + 频率趋势图
- [x] 悬浮窗监控（Service + Overlay）
- [ ] 更完整的 PIO 首页仪表盘 / 悬浮窗
- [x] 主题设置（跟随系统 / 浅色 / 深色 / Monet 动态取色，持久化）
- [ ] 在 ROOT 设备上回归测试
- [ ] Push 到 GitHub fork

## 技术栈

- Kotlin 2.4.10
- AGP 9.3.1
- Compose BOM 2025.06.01
- Miuix 0.9.4-rc01
