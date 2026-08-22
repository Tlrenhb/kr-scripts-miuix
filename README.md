# Kr Script (Miuix)

基于 [KrScript](https://github.com/helloklf/kr-scripts) 引擎的 ROOT 玩机工具，使用 **Kotlin + Jetpack Compose + [Miuix](https://github.com/compose-miuix-ui/miuix)**（HyperOS 设计语言）全新重写。

[![Android Build](https://github.com/Tlrenhb/kr-scripts-miuix/actions/workflows/android-build.yml/badge.svg)](https://github.com/Tlrenhb/kr-scripts-miuix/actions/workflows/android-build.yml)

## 功能

- **首页仪表盘** — 设备信息、CPU 使用率/内存趋势图、每核实时频率、电池状态
- **电源菜单** — 重启 / 热重启 / Recovery / Bootloader / 关机
- **悬浮窗监控** — 桌面可拖动 CPU/RAM 悬浮窗（需「显示在应用上层」权限）
- **脚本页面** — 渲染 KrScript XML 配置（`page/action/switch/picker/text/group` 六种节点），支持下拉刷新与搜索
- **动作参数** — 文本 / 单选 / 多选 / 开关 / 滑块 / 取色器 / 文件选择 七种参数类型
- **后台任务** — `bg-task` 模式脱离界面执行脚本
- **收藏夹** — 星标收藏功能项，支持为收藏创建桌面快捷方式
- **在线页面** — WebView 页面，内置 `window.KrScriptCore` JS 桥（`rootCheck` / `executeShell` / `extractAssets`）

## 兼容性

- 完整保留原版 KrScript XML 配置格式与 `executor.sh` 协议，社区既有脚本包可直接使用
- 包名保持 `com.projectkr.shell`，可直接覆盖安装原版

## KrScript 配置文档

1. 功能节点：功能组成
    - [page](./docs/Page.md) 设置功能页面
    - [action](./docs/Action.md) 设置动作节点
    - [switch](./docs/Switch.md) 设置开关节点
    - [picker](./docs/Picker.md) 设置单选列表节点

2. 外观节点：让界面更美观
    - [text](./docs/Text.md) 显示格式化的文本
    - [group](./docs/Group.md) 对功能进行分组

3. 重要建议
    - [脚本使用](./docs/Script.md) 将脚本作为独立文件
    - [resource](./docs/Resource.md) 使用添加到 `assets` 中的文件
    - [visible属性](./docs/Property_Visible.md) 功能的显示和隐藏

4. 额外拓展（附加内容）
    - [其它](./docs/Extra.md) 了解框架对 Shell 所做的额外补充

5. HTML 网页（附加内容）
    - [KrScriptCore](./docs/js-engine/WebBrowser.md "网页上运行脚本 说明章节")

6. 其它说明
    - [其它提示](./docs/Other.md)
    - [kr-script.conf](./docs/kr-script.conf.md) 深入了解启动过程

## 构建

GitHub Actions 自动构建（`.github/workflows/android-build.yml`）：

- `core-tests` — `:core` 纯 JVM 单元测试
- `build-apk` — 产出 `app-debug.apk` artifact

本地命令：

```bash
./gradlew :core:test          # 引擎单元测试（无需 Android SDK）
./gradlew :app:assembleDebug  # APK 构建（需要 Android SDK）
```

## 工程结构

```
├── app/    # Compose + Miuix 应用（UI、导航、服务、Android 桥接）
├── core/   # KrScript 引擎 —— 纯 JVM Kotlin（XML 解析、路径分析、Shell 协议）
└── docs/   # KrScript XML 配置格式文档
```

| 技术栈 | 版本 |
|--------|------|
| Kotlin | 2.4.10 |
| AGP | 9.3.1（Gradle 9.6.1） |
| Compose BOM | 2025.06.01 |
| Miuix | 0.9.4-rc01（miuix-ui / preference / icons / nav） |

## 许可

GPL-3.0（继承原版 kr-scripts）
