# KrScript Miuix 重写进度

目标：用 Compose + Miuix 在 Android 单平台完整重写 kr-scripts。

## 已完成

- [x] Fork 并初始化新工程结构
- [x] 接入 Miuix 依赖（miuix-ui / miuix-preference / miuix-icons）
- [x] 建立 `core` 模块，移植核心 Node 模型
- [x] 建立 `app` 模块，配置 MiuixTheme + Scaffold
- [x] 移植 `PageConfigReader`（XML → Node 模型），通过 `ShellRunner` 解耦 Shell
- [x] 添加轻量 `RootShellRunner`，可执行 `sh` 命令
- [x] 添加 `sample.xml`，App 启动后真实解析并展示 Miuix 列表
- [x] Action 参数弹窗：TextField / Slider / Checkbox / Dropdown / ColorPicker / 文件选择 / 多选
- [x] 子页面导航（返回栈）
- [x] PageNode 外链浏览器打开
- [x] 底部导航（页面 / 关于 / 收藏）
- [x] 收藏夹（SharedPreferences 持久化）
- [x] TextNode 多行富文本渲染
- [x] 初步交互：
  - Action 点击执行脚本
  - Switch 切换执行 setState
  - Picker 选择执行 setState
- [x] 节点列表渲染：
  - Page / Action → ArrowPreference
  - Switch → SwitchPreference
  - Picker → OverlayDropdownPreference
  - Text → SmallTitle
  - Group → 分组小标题

## 进行中 / 待办

- [ ] 移植 `PathAnalysis`（相对路径 / assets / root 文件解析）
- [ ] 移植 `ScriptEnvironmen` / `KeepShell` / `ExtractAssets`
- [ ] 实现真实 ROOT Shell 环境（替换轻量 RootShellRunner，当前已支持 sh + su fallback）
- [ ] 参数表单基本完整，剩余特殊类型按需补充
- [x] Splash 启动页
- [x] 在线页 WebView
- [x] 快捷方式（收藏页创建桌面快捷方式）
- [x] 电源菜单（重启 / Recovery / Bootloader / 关机）
- [ ] 主题、动态取色、深色模式打磨
- [ ] 在 ROOT 设备上回归测试

## 技术栈

- Kotlin 2.4.10
- AGP 9.3.1
- Compose BOM 2025.06.01
- Miuix 0.9.4-rc01
