# Miuix 组件逐项对照文档 Review

> 目标：每个用到的 Miuix 组件都对照本地 `docs/` 和源码核对，不猜 API。

## 核对状态

| 组件 | 文档 | 源码 | 项目用法 | 状态 |
|---|---|---|---|---|
| MiuixTheme / ThemeController | 已读 | 已读 | `MiuixTheme(controller = remember(themeMode){ ThemeController(themeMode) })` 包住整个 UI，Splash 也在内部 | ✅ |
| Scaffold / TopAppBar | 已读 | 已读 | `Scaffold(topBar, bottomBar, floatingActionButton)`；全屏页消费 contentPadding | ✅ |
| NavigationBar / NavigationBarItem | 已读 | 已读 | 4 个 tab，参数 selected/onClick/icon/label | ✅ |
| SearchBar / InputField | 已读 | 已读 | query/onQueryChange/onSearch/expanded/onExpandedChange | ✅ |
| PullToRefresh | 已读 | 已读 | isRefreshing/onRefresh/pullToRefreshState | ✅ |
| OverlayDialog | 已读 | 已读 | PowerMenu/Params 都使用 show 控制 | ✅ |
| ArrowPreference | 已读 | 已读 | title/summary/endActions/onClick | ✅ |
| SwitchPreference | 已读 | 已读 | checked/onCheckedChange/title/endActions | ✅ |
| CheckboxPreference | 已读 | 已读 | title/checked/onCheckedChange/summary | ✅ |
| OverlayDropdownPreference | 已读 | 已读 | items/selectedIndex/onSelectedIndexChange | ✅ |
| SliderPreference | 已读 | 已读 | value/onValueChange/valueRange | ✅ |
| ColorPicker | 已读 | 已读 | color/onColorChanged | ✅ |
| TextField | 已读 | 已读 | TextFieldValue/onValueChange/label/singleLine | ✅ |
| TextButton | 已读 | 已读 | text/onClick/modifier | ✅ |
| IconButton | 已读 | 已读 | onClick + Icon content | ✅ |
| Icon | 已读 | 已读 | imageVector/contentDescription/tint | ✅ |
| Text / SmallTitle | 已读 | 已读 | 按文档使用 | ✅ |
| LinearProgressIndicator | 已读 | 已读 | progress/modifier | ✅ |
| FloatingActionButton | 已读 | 已读 | onClick + Icon | ✅ |

## 待办
- [ ] 等待 GitHub Actions 编译通过
- [ ] 根据编译/运行结果继续修正
