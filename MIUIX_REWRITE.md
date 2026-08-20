# KrScript Miuix 重写进度

目标：用 Compose + Miuix 在 Android 单平台完整重写 kr-scripts。

## 已完成

- [x] Fork 并初始化新工程结构
- [x] 接入 Miuix 依赖（miuix-ui / miuix-preference / miuix-icons）
- [x] 建立 `core` 模块，移植核心 Node 模型
- [x] 建立 `app` 模块，配置 MiuixTheme + Scaffold
- [x] 初步实现节点列表渲染：
  - Page / Action → ArrowPreference
  - Switch → SwitchPreference
  - Picker → OverlayDropdownPreference
  - Text → SmallTitle
  - Group → 分组小标题

## 进行中 / 待办

- [ ] 移植 PageConfigReader / ShellExecutor / ScriptEnvironmen
- [ ] 实现真实 XML 配置解析
- [ ] 实现 Shell 执行与状态刷新
- [ ] 实现参数表单（TextField / Slider / Checkbox / ColorPicker）
- [ ] 重写 MainActivity / Splash / ActionPage / 收藏 / 在线页
- [ ] 主题、动态取色、深色模式打磨
- [ ] 在 ROOT 设备上回归测试

## 技术栈

- Kotlin 2.4.10
- AGP 9.3.1
- Compose BOM 2025.06.01
- Miuix 0.9.4-rc01
