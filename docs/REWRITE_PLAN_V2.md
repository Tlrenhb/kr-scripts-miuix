# PIO 框架 Miuix 重写计划 v2（2026-10-20）

## 原则
1. 一切 API 用法以 Miuix 官方 GitHub 仓库（compose-miuix-ui/miuix）的文档/示例为准，禁止凭记忆编造 API。
2. 本地只做 git 与文本编辑；不跑 Gradle、不跑模拟器、不跑任何重负载。构建验证只走 GitHub Actions（workflow 已改为任意 push 触发 + 支持手动 dispatch）。
3. 框架兼容性优先：原版 pio/krscript 的 XML 协议、脚本执行协议、参数协议、菜单协议、WebView JS 桥必须逐项对齐（见 docs/FRAMEWORK_PARITY.md）。
4. 原版 UI 只是 demo，不追求界面复刻；但框架能力（首页可关、配置驱动）必须保留。

## 分层目标
- `core` 模块（纯 Kotlin/Android 库，无 Compose 依赖）：完整框架契约
  - model/：Node 模型全集（含 TextNode rows、RunnableNode、AutoRunTask、PageMenuOption、ActionParamInfo 全属性）
  - config/：PageConfigReader（XML→Node）、PageConfigSh（sh 输出配置）、IconPathAnalysis、PathAnalysis、Suffix2Mime
  - executor/：ScriptEnvironment（executor.sh/before_start.sh/toolkit 解压、环境变量）、ShellRunner 抽象、KeepShell 实现、流式日志协议、bg-task/hidden-task 协议、中断
  - KrScriptConfig：kr-script.conf 全部键
  - menu/：PageMenuLoader（options-sh 替换静态选项、menu_id/state/file/folder 回调参数）
  - web/：KrScriptCore WebView JS 桥（对齐原版 WebViewInjector 方法表）
  - shortcut/：ActionShortcutManager 协议
- `app` 模块（Compose + Miuix）：demo shell
  - ui/KrScriptApp.kt：MiuixTheme + Scaffold + 导航壳（按官方文档组织）
  - ui/pages/：NodeList 渲染器（Page→Arrow、Switch→SwitchPreference、Picker→下拉、Text→富文本、Group→分组）、页面详情、执行控制器（日志流弹窗、确认弹窗、bg-task、中断）
  - ui/dialogs/：参数弹窗（text/seekbar/checkbox/single/multi(separator)/color/file/app）
  - ui/home/、ui/monitor/、ui/fileselector/、ui/about/
  - MainActivity：启动初始化（ScriptEnvironment.init、root 检测）

## 执行顺序
1. [进行中] Miuix 文档调研（版本、组件签名、导航、弹窗、主题）
2. [进行中] 原版框架契约审计 → docs/FRAMEWORK_PARITY.md
3. 按文档重写 core（缺什么补什么）
4. 按文档重写 app UI（多文件结构）
5. 逐批 commit；向用户索取推送凭据 → push → GitHub Actions 验证 → 修复直至绿
