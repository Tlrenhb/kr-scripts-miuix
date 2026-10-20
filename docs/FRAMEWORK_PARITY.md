# KrScript Framework Parity Contract

**Purpose.** This document is the authoritative feature-parity contract for completing the Miuix/Compose rewrite on branch `miuix-rewrite-2`. It freezes the behavior of the original framework at git commit `b90f2cc` (modules `pio` = `com.projectkr.shell`, `krscript` = `com.omarea.krscript`, `common` = `com.omarea.common`) and scores the rewrite (modules `core` = `com.projectkr.shell.core`, `app` = `com.projectkr.shell`) against it. Produced for step 2 of `docs/REWRITE_PLAN_V2.md`; steps 3–4 (rebuild `core`, then `app`) must implement against §1–§5 and close the gaps in §6.

**Method.** Read-only audit. Original code was read via `git show b90f2cc:<path>`; rewrite code is the working tree. No builds were run. Where documentation (`pio/src/main/assets/docs/*.md`) and code disagree, **code is authoritative** (noted inline).

**Status marks used throughout:**
- **[DONE]** — rewrite behavior matches the contract.
- **[PARTIAL]** — exists but deviates; the deviation is described exactly.
- **[MISSING]** — not implemented in the rewrite.

**Module map:**

| Layer | Original @ b90f2cc | Rewrite (working tree) |
|---|---|---|
| Framework library | `krscript/` (parser, models, executor, UI renders, shortcut, webview, downloader) + `common/` (shell, dialogs, storage) | `core/` (models, parser, path/suffix analysis, ScriptEnvironment, ExtractAssets, ShellRunner) |
| App shell | `pio/` (SplashActivity, MainActivity, ActionPage, ActionPageOnline, ActivityFileSelector, FragmentHome, FloatMonitor, DialogPower, theme, utils) | `app/` (MainActivity, `ui/KrScriptApp.kt` single-file Compose UI, `shell/*Runner`, `service/FloatMonitorService`, `shortcut/ShortcutHelper`) |
| Docs | `pio/src/main/assets/docs/*.md` | copied into `docs/` |

Note: legacy modules `pio/`, `krscript/`, `common/` still exist in the working tree but are excluded from the build (`settings.gradle.kts` includes only `:app`, `:core`) — they are dead code, not the rewrite.

---
# §1. XML Node Schema Contract

Source of truth: `krscript/src/main/java/com/omarea/krscript/config/PageConfigReader.kt` @ b90f2cc (the whole parser), plus `config/PageConfigSh.kt`, `config/PathAnalysis.kt`, `config/Suffix2Mime.kt`, all of `krscript/.../model/*`, and docs `pio/src/main/assets/docs/{Action,Switch,Picker,Text,Page,Group,Extra,Other,Property_Other,Resource,Path,Index,Script}.md`.

## 1.0 Parser mechanics (contract-relevant)

- XmlPullParser over a UTF-8 stream. **Root tag name is ignored** ("PIO并不关心根节点" — `docs/Action.md`); the first START_TAG clears `isRootNode`, and a `<page>` that IS the root element is not parsed as a PageNode.
- START_TAG dispatch order: `group` → `page` → `action` → `switch` → `picker` → `text` → (open node's sub-tag handler) → `resource`.
- `<group>` quirk: a new `<group>` while one is open **flushes the previous group** to the main list — nested groups flatten; there is no true nesting.
- A group whose `support`/`visible` script output ≠ `"1"` gets `supported=false` and **everything inside it is skipped** (including `<resource>` extraction).
- A non-group node with `support`/`visible` ≠ `"1"` is dropped (`mainNode` returns null). **Code/doc discrepancy**: `docs/Resource.md` claims resources inside invisible nodes are not extracted, but the code falls through to the top-level `resource` branch and **does** extract them. Code is authoritative.
- Layered attribute scans of the same start-tag: `mainNode` (all nodes) → `clickbleNode` (page/action/switch/picker + menu options) → `runnableNode` (action/switch/picker + menu options) → type-specific handlers. Unknown attributes are ignored; later duplicates win.
- `visible`/`support`/`desc-sh`/`summary-sh` scripts run **at parse time** through `executeResultRoot` with a virtual node carrying the current page-config path (feeds `PAGE_CONFIG_*` env vars, §3.2).
- Syntactic tolerance (`docs/Other.md`): wrapper tags (`<params>`, `<options>`, `<slices>`) are ignored tags; `<option>` after `</param>` still attaches to the last param (parser never nulls `actionParamInfo` — sticky-last-param quirk; preserve for parity).
- Parse failure → Toast `解析配置文件失败\n<msg>` + logcat `KrConfig Fail！`, returns `null`.

## 1.1 Node types and allowed placements

| Node | Root level | Inside `<group>` | Sub-tags accepted |
|---|---|---|---|
| `<group>` | ✅ | flattens previous group | page, action, switch, picker, text, resource (start-tag only) |
| `<page>` | ✅ (except as root tag) | ✅ | title, desc, summary, resource, html, config, handler-sh/handler/set/getstate/script, lock/lock-state, option/page-option/menu/menu-item |
| `<action>` | ✅ | ✅ | title, desc, summary, script/set/setstate, lock/lock-state, param (→option), resource |
| `<switch>` | ✅ | ✅ | title, desc, summary, get/getstate, set/setstate, lock/lock-state, resource |
| `<picker>` | ✅ | ✅ | title, desc, summary, option, getstate/get, setstate/set, lock/lock-state, resource |
| `<text>` | ✅ | ✅ | title, desc, summary, slice, resource |
| `<resource>` | ✅ | ✅ (start-tag) | also inside page/action/switch/picker/text |

No standalone `<html>` node exists (page attribute/sub-tag only). Unknown tags are ignored. Docs per node: `Page.md`, `Action.md`, `Switch.md`, `Picker.md`, `Text.md`, `Group.md`, `Resource.md`.

## 1.2 Common attributes — Layer A (`mainNode` → `NodeInfoBase`, ALL node types)

| Attribute (exact spellings) | Field | Default | Semantics |
|---|---|---|---|
| `key`, `index`, `id` | `key` (trimmed) | `""` | Unique id; needed for shortcuts/favorites/auto-run/reload targeting; `@`-prefixed key ⇒ `allowShortcut=false` unless explicitly set (Layer B) |
| `title` | `title` | `""` | Display title |
| `desc` | `desc` | `""` | Small text under title |
| `summary` | `summary` | `""` | Highlighted summary text |
| `support`, `visible` | — (node dropped if exec ≠ `"1"`) | shown | Script executed at parse time; output must be exactly `1` |
| `desc-sh` | `descSh` + `desc` = exec output | `""` | Dynamic desc script; re-executed after each run (`updateViewByShell`, §4.8) |
| `summary-sh` | `summarySh` + `summary` = exec output | `""` | Dynamic summary script |

`NodeInfoBase.index` is a random UUID (NOT the `index` attribute — the attr maps to `key`). `pageConfigDir` = `File(currentPageConfigPath).parent` with `file:/android_asset/` → `file:///android_asset/` normalization. Child elements available on all node types: `<title>` (text), `<desc>` (attrs `su`|`sh`|`desc-sh` → script, else element text fallback when desc empty), `<summary>` (attrs `su`|`sh`|`summary-sh`, same fallback).

## 1.3 Clickable attributes — Layer B (`clickbleNode` → `ClickableNode`: page/action/switch/picker/menu-option)

| Attribute | Field | Default | Semantics |
|---|---|---|---|
| `lock`, `lock-state`, `locked` | `locked` | `false` | Static lock; true if value ∈ {`1`,`true`,`locked`} |
| `min-sdk`, `sdk-min` | `minSdkVersion` | `0` | Min Android SDK (click denied with dialog otherwise, §4.8) |
| `max-sdk`, `sdk-max` | `maxSdkVersion` | `100` | Max SDK |
| `target-sdk`, `sdk-target` | `targetSdkVersion` | `0` | Exact SDK required |
| `icon`, `icon-path` | `iconPath` (trimmed) | `""` | List-row icon (assets or disk path) — **list rows use iconPath ONLY** (§4.3) |
| `logo`, `logo-path` | `logoPath` (trimmed) | `""` | Shortcut/FAB icon |
| `allow-shortcut` | `allowShortcut` | `null` (→ auto) | True if ∈ {`allow`,`allow-shortcut`,`true`,`1`}; long-press shortcut requires `key.isNotEmpty() && allowShortcut != false` |

Dynamic lock: child element `<lock>`/`<lock-state>` text → `lockShell`, executed **on click**; node is unlocked iff trimmed output ∈ {`unlock`, `unlocked`, `false`, `0`} (or script empty and `locked=false`); any other non-empty output is toasted as the refusal message (`docs/Property_Other.md`, `ActionListFragment.nodeUnlocked`).

## 1.4 Runnable attributes — Layer C (`runnableNode` → `RunnableNode`: action/switch/picker/menu-option)

| Attribute | Field | Default | Truthy values / semantics |
|---|---|---|---|
| `confirm` | `confirm` | `false` | {`confirm`,`true`,`1`} — confirmation dialog (title+desc) before run |
| `warn`, `warning` | `warning` | `""` | Custom warning text; standalone dialog only when the action has **no params**, else shown atop the params dialog |
| `auto-off`, `auto-close` | `autoOff` | `false` | {`auto-close`,`auto-off`,`true`,`1`} — auto-close log view on success (suppressed if any stderr line arrived) |
| `auto-finish` | `autoFinish` | `false` | {`auto-finish`,`true`,`1`} — close the whole page/activity after completion |
| `interruptible`, `interruptable` | `interruptable` | `true` | true if value empty or ∈ {`interruptable` (sic, duplicated in code), `true`, `1`}. QUIRK: the literal value `interruptible` → **false**; `false`/`0` → false. Preserve as-is. |
| `reload-page` | `reloadPage` | `false` | {`reload-page`,`reload`,`page`,`true`,`1`} → reload entire page config after run |
| `reload` | `reloadPage` or `updateBlocks` | `false`/`null` | Truthy set as above → reloadPage; any **other** non-empty value → `updateBlocks = value.split(",").map{trim}.dropLastWhile{empty}` (keys of nodes to refresh) |
| `shell` | `shell` | `"default"` | Execution UI mode: `default` (streaming log dialog), `hidden` (silent; errors toasted at exit), `bg-task` (notification task) |
| `bg-task`, `background-task`, `async-task` | `shell="bg-task"` | — | Legacy alias; if ∈ {`async-task`,`async`,`bg-task`,`background`,`background-task`,`true`,`1`} |

Model defaults (`model/RunnableNode.kt`): `confirm=false`, `warning=""`, `autoOff=false`, `interruptable=true`, `reloadPage=false`, `updateBlocks=null`, `autoFinish=false`, `shell="default"`, `setState=null`; constants `shellModeDefault="default"`, `shellModeBgTask="bg-task"`, `shellModeHidden="hidden"`.

## 1.5 `<action>` and `<param>`

`<action>` accepts Layers A+B+C. Sub-tags: `title`, `desc`, `summary`, `script`|`set`|`setstate` → `setState` (**trimmed — only action trims**), `lock`|`lock-state` → `lockShell`, `param`, `option`, `resource`. At END_TAG: `setState` null → `""`; `params` = collected list (null if none were added).

### `<param>` attributes (added only if `supported && name` non-empty)

| Attribute | Field | Default | Notes |
|---|---|---|---|
| `name` | `name` | `null` | **Required**; becomes the shell env var name at execution (§3.3) |
| `label` | `label` | `null` | Caption left of input (hidden for bool/checkbox/switch — there it becomes widget text) |
| `title` | `title` | `null` | Caption above input |
| `desc` | `desc` | `null` | Caption below input |
| `placeholder` | `placeholder` | `""` | Text-input watermark |
| `value` | `value` | `null` | Static initial value |
| `value-sh`, `value-su` | `valueShell` | `null` | Script echoing the initial value → `valueFromShell` (overrides `value`) |
| `type` | `type` (lowercased+trimmed) | `null` | Any string; dispatch table below |
| `readonly` | `readonly` | `false` | value lowercased ∈ {`readonly`,`true`,`1`}; disables input |
| `maxlength` | `maxLength` | `-1` | Text length cap, enforced by `ParamInfoFilter` |
| `min` | `min` | `Int.MIN_VALUE` | seekbar/numeric lower bound |
| `max` | `max` | `Int.MAX_VALUE` | Upper bound |
| `required` | `required` | `false` | ∈ {`true`,`1`,`required`} — **case-sensitive** (no lowercase conversion, unlike `readonly`) |
| `options-sh`, `option-sh`, `options-su` | `optionsSh` (+ inits options list) | `""` | Script echoing options, one per line, `value|label` |
| `support`, `visible` | `supported` | `true` | exec ≠ `1` ⇒ param excluded |
| `multiple` | `multiple` | `false` | ∈ {`multiple`,`true`,`1`} — multi-select (options/app) |
| `editable` | `editable` | `false` | ∈ {`editable`,`true`,`1`} — allow manual path entry for file/folder |
| `separator` | `separator` | `"\n"` | Multi-select join separator |
| `suffix` | `suffix` (+ `mime` via `Suffix2Mime` if mime empty) | `""` | File filter; lowercased+trimmed; drives the internal selector |
| `mime` | `mime` (lowercased) | `""` | MIME filter for SAF picker |

**No `step`/`unit` attributes exist** (verified against model + parser).

`<option>` inside param/picker: attrs `val`|`value` → `value`; tag text → `title`; absent value defaults to the title text.

### Param `type` dispatch (original `ActionParamsLayoutRender.renderList`, first match wins)

1. `optionsFromShell != null && type ∉ {app, packages}` → dropdown (`multiple` → multi-select, else single-select). **Dropdown-ness is decided by the presence of options, not by `type`.**
2. `bool` | `checkbox` → checkbox widget, value `1`/`0`.
3. `switch` → switch widget, value `1`/`0`.
4. `seekbar` → slider (needs `min`+`max`).
5. `file` | `folder` → path chooser (`suffix`/`mime` filters; `editable` allows typing).
6. `app` | `packages` → app chooser (`packages` additionally lists not-installed apps named in options).
7. `color` → color picker + validated hex input (`#RRGGBB`/`#AARRGGBB`).
8. else → text EditText; `int`/`number` get numeric filtering via `ParamInfoFilter` regexes `^[0-9]*$` / `^[\-.,0-9]*$` and min/max validation on confirm.

Docs list (`docs/Action.md`): int, number, checkbox, switch, seekbar, file, folder, color, app, packages, text (default).

## 1.6 `<switch>`

Layers A+B+C. Sub-tags: `get`|`getstate` → `getState` (NOT trimmed, default `""`), `set`|`setstate` → `setState` (NOT trimmed, null → `""` at close), `title`/`desc`/`summary`/`resource`/`lock`|`lock-state`.
- At END_TAG (parse time): `getState` executes immediately; `checked = (result != "error") && (result == "1" || result.lowercase() == "true")`.
- On click: confirm/warning/lock gating, then `setState` executes with env `state=1|0` (target state); afterwards the row re-executes `getState` (`updateViewByShell`).

## 1.7 `<picker>`

Layers A+B+C plus own attributes: `option-sh`|`options-sh`|`options-su` → `optionsSh`; `multiple` (∈ {`multiple`,`true`,`1`}, default `false`); `separator` (default `"\n"`). Sub-tags: `option`, `getstate`|`get` → `getState`, `setstate`|`set` → `setState`, `title`/`desc`/`summary`/`resource`/`lock`|`lock-state`.
- At END_TAG: `getState` null → `""`, else executed → `value` (current selection, used to preselect); `setState` null → `""`.
- On click: gating → worker thread re-runs `get` and loads options (`options-sh` output wins only if non-empty and ∉ {`error`,`null`}; lines `value|label`; else static `<option>`s) → `DialogItemChooser` (multi per `multiple`) → `setState` with env `state` = single value, or values joined by `separator`. Model has a `name` field that the parser never sets.

## 1.8 `<text>` and `<slice>`

Layer A only (key/title/desc/summary/desc-sh/summary-sh/support/visible). Sub-tags: `title`, `desc`, `summary`, `slice`, `resource`. Each `<slice>` → `TextNode.TextRow`; attribute names are lowercased before matching; each attribute parsed inside its own try/catch (bad values ignored):

| Attribute | Field | Default | Notes |
|---|---|---|---|
| `bold`, `b` | `bold` | `false` | ∈ {`1`,`true`,`bold`} |
| `italic`, `i` | `italic` | `false` | ∈ {`1`,`true`,`italic`} |
| `underline`, `u` | `underline` | `false` | ∈ {`1`,`true`,`underline`} |
| `foreground`, `color` | `color` (`Color.parseColor`) | `-1` | Text color |
| `bg`, `background`, `bgcolor` | `bgColor` | `-1` | Background color |
| `size` | `size` (Int, dp) | `-1` | Absolute size span |
| `break` | `breakRow` | `false` | ∈ {`1`,`true`,`break`} — newline before this slice (also auto-newline when align ≠ normal) |
| `align` | `align` | `ALIGN_NORMAL` | `left`→ALIGN_LEFT, `right`→ALIGN_RIGHT (both **API ≥ 28 only**), `center`→ALIGN_CENTER, `normal`→ALIGN_NORMAL |
| `link`, `href` | `link` | `""` | Click → browser (`ACTION_VIEW`, NEW_TASK), link-colored |
| `activity`, `a`, `intent` | `activity` | `""` | Click → TryOpenActivity (§4.6) |
| `script`, `run` | `onClickScript` | `""` | Click → execute; non-empty output shown in a dialog |
| `sh` | `dynamicTextSh` | `""` | Script providing the text content (stored on the row) |

Slice text = element text. Rendering: all slices appended into one TextView as spans (`ListItemText`); when a text node has no slices the rows view is GONE (title/desc still render).

## 1.9 `<page>` and page menu items

Own attributes (`pageNode` layer → `PageNode`):

| Attribute | Field | Default | Semantics |
|---|---|---|---|
| `config` | `pageConfigPath` | `""` | Sub-page XML (PathAnalysis resolution, §1.11) |
| `config-sh` | `pageConfigSh` | `""` | Script outputting a config path or full XML (PageConfigSh, §1.12) |
| `html` | `onlineHtmlPage` | `""` | Sub-page is a web page (built-in WebView + KrScriptCore bridge, §4.7) |
| `link`, `href` | `link` | `""` | External browser URL; click priority: link → activity → sub-page |
| `activity`, `a`, `intent` | `activity` | `""` | Intent action (e.g. `android.settings.APN_SETTINGS`) or `package/Class` |
| `before-load`, `before-read` | `beforeRead` | `""` | Script run before reading the page config |
| `after-load`, `after-read` | `afterRead` | `""` | Script run after config read |
| `load-ok`, `load-success` | `loadSuccess` | `""` | Script run on page load success (non-empty list) |
| `load-fail`, `load-error` | `loadFail` | `""` | Script run on page load failure |
| `option-sh`, `option-su`, `options-sh` | `pageMenuOptionsSh` | `""` | Script emitting menu options, one per line `key|title` |
| `handler-sh`, `handler`, `set`, `getstate`, `script` | `pageHandlerSh` | `""` | Menu/FAB click handler; receives env `$menu_id`, `$state` (= menu key), `$file`/`$folder` (file menus) |

Sub-tags: `title`/`desc`/`summary`, `resource`, `html` (text), `config` (text), `handler-sh`|`handler`|`set`|`getstate`|`script` (text), `lock`|`lock-state`, and menu items.

### Page menu item — tags `<menu>` | `<menu-item>` | `<option>` | `<page-option>` inside `<page>`

Parsed via the full runnableNode layers (so it accepts key/id, desc, confirm, warning, auto-off, auto-finish, reload-page, interruptible, shell, icon/logo, lock, sdk gates, …) plus:

| Attribute | Field | Default | Semantics |
|---|---|---|---|
| `type` | `type` | `""` | `default` (send key to handler) · `refresh`/`reload` (recreate page, no handler) · `exit`/`finish`/`close` (finish page, no handler) · `file`/`folder` (choose path first, then handler with `$file`+`$folder`) |
| `style` | `isFab` | `false` | `fab` ⇒ floating action button (at most ONE fab per page, by docs convention) |
| `suffix` | `suffix` (+`mime` via Suffix2Mime if empty) | `""` | type=file filter |
| `mime` | `mime` (lowercased) | `""` | type=file filter |

Tag text → `title`; empty `key` → `key = title`. **The option's own `setState` is never parsed** — clicks always run the page's `pageHandlerSh` (§4.2).

## 1.10 `<group>` and `<resource>`

`<group>` attributes: `key`|`index`|`id` (trim → `key`), `title`, `support`|`visible` (script output `1` ⇒ `supported`, default `true`). No desc/summary/icon parsing.

`<resource>` attributes: `file` (extract one asset file), `dir` (extract asset file or directory recursively) — via `ExtractAssets`; the `file:///android_asset/` prefix is optional; each extraction happens **once per process** (memoized). Extraction target = app private files dir mirroring the assets-relative path. Docs warn against extracting over the toolkit dir (`docs/Resource.md`).

## 1.11 Path resolution contract (`config/PathAnalysis.kt`)

- `file:///android_asset/…` → `context.assets.open(rest)`; absPath recorded verbatim.
- Absolute `/…` → direct `File` read; on failure **root fallback** `useRootOpenFile` (root `cp -f` to private `kr-script/outside_file.cache`, `chmod 777`, `chown <u0_aNNN>`, then read).
- Relative with assets parent → `pathConcat(parentDir, target)` in assets; fallback `assets.open(target)` with absPath = prefix+target.
- Relative with disk/empty parent → parent-concat (direct, then root fallback); then app private dir concat (`FileWrite.getPrivateFileDir`, direct, then root fallback).
- `pathConcat` resolves `../` by popping parent segments (recursive), strips leading `./`, preserves the assets prefix, inserts a single `/`.

## 1.12 Page-list enumeration via script (`config/PageConfigSh.kt`)

`PageConfigSh(activity, pageConfigSh, parentConfig).execute()`: run the script as root through the executor, trim output:
- ends with `.xml` → treated as a config file path, read with `PageConfigReader(path, parentConfig?.pageConfigDir)` (null read → toast `kr_page_sh_file_permission`);
- starts with `<?xml` and ends with `>` → parsed as inline XML;
- any other non-empty output → toast `kr_page_sh_invalid`;
- empty/null → caller falls back to the static `pageConfigPath`.

## 1.13 Script text vs script file

Wherever a script is expected (`set`, `get`, `desc-sh`, `visible`, handlers, …): text starting with `file:///android_asset/` is **extracted to the private dir and executed as a file** (`ScriptEnvironmen.extractScript`); anything else is cached to `kr-script/cache/<md5(script)>.sh` (with `#!/system/bin/sh` shebang + CR→LF normalization) and executed through `executor.sh` (§3). Long scripts should be shipped as assets and pulled in with `<resource>` (`docs/Script.md`).

---
# §2. Page Config & `kr-script.conf` Contract

Source of truth: `pio/src/main/java/com/projectkr/shell/KrScriptConfig.java`, `pio/src/main/assets/docs/kr-script.conf.md`, consumers in `pio/.../SplashActivity.kt` + `pio/.../MainActivity.kt`.

## 2.1 File discovery & parsing contract

- The config file name is indirected through a string resource: `pio/src/main/res/values/strings.xml` → `<string name="kr_script_config">kr-script.conf</string>`. The loader strips a leading `file:///android_asset/` prefix and opens the file from **assets only** (`pio/src/main/assets/assets说明.txt`: "固定配置文件路径不可更改").
- Format: lines of `key="value"`. Lines starting with `#` or lacking `=` are skipped. Split at the **first** `=`; value = `rowText.substring(separator + 2, rowText.length() - 1).trim()` — the parser blindly strips the character after `=` and the final character, which *is* the quote stripping. Consequences (warned in the shipped file's own header): trailing comments corrupt the value; unquoted values are mangled; a malformed line aborts parsing of the remaining lines (outer `catch (Exception)` swallows it).
- Seeding & override: `init()` first seeds 6 defaults into a **static** `HashMap`, then parsed keys overwrite them (`remove` + `put`). Because the map is static, parsing happens once per process: `SplashActivity` runs `KrScriptConfig().init(this)`; `MainActivity` later just instantiates `KrScriptConfig()` and reads the populated map.
- **Unknown keys are kept**: `getVariables()` exposes the whole map, and `SplashActivity.BeforeStartThread` passes it to `ScriptEnvironmen.executeShell(...)`, which exports every entry as `export key='value'` into the `before_start_sh` process environment. ⇒ kr-script.conf doubles as an env-var injection mechanism for the pre-start script.
- Init ordering (doc "应用启动过程", matches code): root check → parse kr-script.conf → `executor_core` placeholder substitution + `toolkit_dir` extraction (inside `ScriptEnvironmen.init`) → run `before_start_sh` (splash log, blocks entry) → enumerate pages via `favorite_config_sh`/`page_list_config_sh` or `favorite_config`/`page_list_config` (in `MainActivity`).

## 2.2 Key contract table (exactly 8 keys — code == doc)

| Key | Default (code) | Semantics / consumer |
|---|---|---|
| `executor_core` | `file:///android_asset/kr-script/executor.sh` | Consumed at `KrScriptConfig.init()` → `ScriptEnvironmen.init(context, executor, toolkitDir)`. The executor script is read from assets; `$({VAR})` placeholders substituted (§3.2); written to the private files dir; its path becomes `environmentPath`. **Every** later script execution is `"$environmentPath" "$scriptPath" ["$tag"]`. Persisted into SharedPreferences `kr-script-config` (`executor`) so no-arg `ScriptEnvironmen.init(context)` reuses the last value. |
| `toolkit_dir` | `file:///android_asset/kr-script/toolkit` | `ScriptEnvironmen.init` → `ExtractAssets.extractResources(toolkitDir)`: whole assets dir extracted to private storage; the resulting abs path becomes `TOOLKIT` and is substituted into executor.sh, which appends it to `PATH` and runs `$TOOLKIT/install_busybox.sh` if present. Original ships busybox/toybox/zip/install_busybox.sh there. Also persisted (`toolkitDir`). |
| `page_list_config` | `file:///android_asset/kr-script/pages/more.xml` (note `pages/`; the shipped conf overrides to `file:///android_asset/kr-script/more.xml`) | `getPageListConfig()` → `PageNode.pageConfigPath`; `MainActivity` loads it on a worker thread; non-empty result ⇒ the **全部 (pages)** tab is created, else that tab is `GONE`. Re-loaded when a `reloadPage` node completes. May be configured empty ⇒ tab hidden. |
| `page_list_config_sh` | *(none — not seeded)* | If present, `getPageListConfig()` sets `pageConfigSh`; `MainActivity.getItems()` runs it **first** via `PageConfigSh.execute()` (§1.12 protocol: `.xml` suffix ⇒ path, `<?xml…>` ⇒ inline XML); only if the sh yields null does it fall back to `page_list_config`. |
| `favorite_config` | `file:///android_asset/kr-script/pages/favorites.xml` (shipped conf overrides to `…/kr-script/favorites.xml`) | Same as `page_list_config` but drives the **收藏夹 (favorites)** tab. NOTE: the runtime "add to favorites" action does NOT edit this XML — it creates a launcher pinned shortcut (§4.4). |
| `favorite_config_sh` | *(none — not seeded)* | Same sh-first/path-fallback protocol for the favorites tab. |
| `allow_home_page` | `"1"`; true iff value equals `"1"` | `MainActivity`: the home/performance tab (`FragmentHome`) is added only `if (CheckRootStatus.lastCheckResult && allowHomePage)`, else hidden; also gates the float-monitor menu item visibility (`action_graph` visible ⇔ home tab visible). Shipped pio conf sets `"0"`. |
| `before_start_sh` | `""` (empty = skip) | `SplashActivity.startToFinish()`: after root check + config init, if non-empty, `BeforeStartThread` opens `su` (if rooted) else `sh` and runs the script **through the executor** with ALL conf keys exported as env (`config.getVariables()`), tag `"pio-splash"`. stdout+stderr stream line-by-line to the splash TextView (rolling 6-row window, `……` elision). Navigation to `MainActivity` is blocked until process exit — but failure semantics are soft: process-null or any exception still proceeds home, so before_start can never hard-block startup. Doc use-case: forced update before entering. |

## 2.3 Cross-check notes

- Doc lists exactly these 8 keys; code implements exactly these 8. No doc-only or code-only keys.
- **Explicitly absent** (verified by grep): no `page_menu_sh` (menus are per-page, §1.9/§4.2); no `allow_root`/`check_root` (root policy is the bool resource `force_root`, `pio/src/main/res/values/strings.xml`, consumed by `CheckRootStatus`); no `update_url` (no update check exists in the original app at all); no `white_list`/`black_list`, `log_*`, or theme keys (transparent-UI is `ThemeConfig` SharedPreferences, unrelated).
- Adjacent config surface: `R.string.kr_script_config` (conf file name); SharedPreferences `kr-script-config` (`executor`, `toolkitDir`) — ScriptEnvironmen's re-init cache; `R.bool.force_root` (`false` shipped) — controls whether the root-denied dialog offers "skip".
- Shipped default pages: `pio/src/main/assets/kr-script/favorites.xml` and `more.xml`; `pio/src/main/assets/kr-script示例/kr-script-online.conf` demonstrates the sh-driven mode (`before_start_sh` + `page_list_config_sh` + `favorite_config_sh`).

---
# §3. Execution Contract

Source of truth: `krscript/src/main/java/com/omarea/krscript/executor/{ScriptEnvironmen.java, ShellExecutor.java, SimpleShellWatcher.java, ExtractAssets.java}`, `krscript/.../model/{ShellHandlerBase.java, RunnableNode.kt, AutoRunTask.kt, KrScriptActionHandler.kt}`, `krscript/.../{BgTaskThread.kt, HiddenTaskThread.kt}`, `krscript/.../ui/{DialogLogFragment.kt, ActionListFragment.kt}`, `common/src/main/java/com/omarea/common/shell/*`, `pio/src/main/assets/docs/{Script.md, Extra.md}`, `pio/src/main/assets/kr-script/executor.sh`.

## 3.1 Environment initialization (`ScriptEnvironmen`)

One-time static init (`inited` flag), normally triggered by `KrScriptConfig.init()` (§2):

1. `rooted = KeepShellPublic.checkRoot()`.
2. `TOOKIT_DIR = ExtractAssets.extractResources(toolkitDir)` — recursive asset copy to the private dir (memoized per process in `extractHisotry`).
3. Read `executor.sh` from assets, strip `\r`, substitute every `$({KEY})` placeholder from `getEnvironment()` plus `$({EXECUTOR_PATH})` (the executor's own private abs path, substituted after the output path is known).
4. Write via `FileWrite.writePrivateFile` → `environmentPath` set.
5. Persist SP `kr-script-config` (`executor`, `toolkitDir`) for no-arg re-init.
6. `privateShell = KeepShellPublic.getDefaultInstance()` if rooted else `new KeepShell(false)`.

`ExtractAssets`: `extractResource(file)` (`.sh` → `writePrivateShellFile`, other → `writePrivateFile`), `extractResources(dir)` (recursive; falls back to single-file), `getExtractPath(file)` (pure path mapping, no I/O).

## 3.2 Environment variables

Baked into executor.sh at init (`getEnvironment()`), plus per-execution exports (`executeShell` → `getVariables()` → `export K='v'` lines, single quotes escaped `'` → `'\''`):

| Variable | Value rule |
|---|---|
| `EXECUTOR_PATH` | Abs private path of the written executor.sh |
| `START_DIR` | App private files dir (trailing `/` stripped); executor.sh `cd "$START_DIR"` |
| `TEMP_DIR` | `context.cacheDir` (executor.sh also exports `TMPDIR=$TEMP_DIR` — fixes unrooted `/data/local/tmp` writes) |
| `TOOLKIT` | Extracted toolkit abs path; executor.sh appends to `PATH` and runs `$TOOLKIT/install_busybox.sh` if present |
| `MAGISK_PATH` | `MagiskExtend.MAGISK_PATH` minus trailing `/` if the module is installed, else `""` |
| `ANDROID_UID` | `UserManager.getSerialNumberForUser(Process.myUserHandle())` — user **serial** (e.g. `0`) |
| `APP_USER_ID` | `"u<serial>_a<(uid%100000)-10000>"`, e.g. `u0_a123` — documented for `chown $APP_USER_ID` (`docs/Script.md`, `docs/Resource.md`) |
| `ANDROID_SDK` | `Build.VERSION.SDK_INT` |
| `ROOT_PERMISSION` | `"true"`/`"false"` from the real `checkRoot()` |
| `SDCARD_PATH` | `Environment.getExternalStorageDirectory()` |
| `BUSYBOX` | Private `busybox` abs path if that file exists, else literal `busybox` |
| `PACKAGE_NAME` / `PACKAGE_VERSION_NAME` / `PACKAGE_VERSION_CODE` | PackageManager (long version code on API ≥ P) |
| `PAGE_CONFIG_DIR` / `PAGE_CONFIG_FILE` | **Per-execution** exports from the node's `pageConfigDir`/`currentPageConfigPath` (`file:///android_asset/`-normalized) |
| `PAGE_WORK_DIR` / `PAGE_WORK_FILE` | Extracted private path if the config came from assets, else = CONFIG values |
| `state` | Switch/picker target state (`1`/`0`/value); menu handler: = menu key |
| `menu_id` | Menu option key (page handler only) |
| `file` / `folder` | Chosen path (menu `type="file"`) |
| each `<param name>` | Action param values, exported as env vars of the same name |

The stock `pio/src/main/assets/kr-script/executor.sh` receives `$1` = script path, `$2` = session tag; it exports the vars above, appends TOOLKIT to PATH, optionally installs busybox, `cd`s to START_DIR, then runs the script.

## 3.3 Execution flow (end-to-end)

1. `ActionListFragment.actionExecute` dispatches on `RunnableNode.shell`: `"bg-task"` → `BgTaskThread.startTask`; `"hidden"` → `HiddenTaskThread.startTask` (singleton guard `hiddenTaskRunning`, toast `kr_hidden_task_running` if busy); else → `DialogLogFragment` (modal, `isCancelable=false`). Before that: `nodeUnlocked()` (SDK-range dialogs + `lockShell` output ∈ {`unlock`,`unlocked`,`false`,`0`}), `confirm`/`warning` dialogs; switch/picker/menu executions put `state` etc. into the params map (§1).
2. `ShellExecutor.execute` (single-use via `started` flag):
   - New process per action: `Runtime.exec("su")` if rooted else `exec("sh")` (`ScriptEnvironmen.getRuntime`).
   - `sessionTag = "pio_" + System.currentTimeMillis()`, passed as `$2` to executor.sh (unused by the script; exists so `pgrep -f <tag>` can find the tree).
   - Sends `EVENT_START` twice (`"shell@android:\n"`, then the script text), then `onStart(forceStopRunnable)`.
   - Stdin writes: `sleep 0.2;` → all `export K='v'` lines → `<executorPath> "<cachePath>" "<tag>"` → `\n\n`, `sleep 0.2;`, `exit`, `exit`.
   - Script caching: inline scripts → `kr-script/cache/<md5(script)>.sh` with `#!/system/bin/sh` shebang and CR/LF normalization; `file:///android_asset/…` scripts extracted to the private dir.
3. `SimpleShellWatcher.setHandler`: stdout thread → `EVENT_REDE` per line (+`\n`, after `ShellTranslation.resolveRow`); stderr thread → `EVENT_READ_ERROR`; wait thread → `EVENT_EXIT` carrying `process.waitFor()` status, interrupts both readers, runs the outer `onExit` Runnable.
4. **Callback semantics**:
   - `DialogLogFragment.MyShellHandler.onExit`: ignores the code value; appends localized "completed"; `onCompleted()` (hide btn_hide, show btn_exit, hide progress, `isCancelable=true`, run outer `onExit`); `onSuccess()` → `autoOff` close **only if no stderr line ever arrived** (`hasError`).
   - `BgTaskThread.ServiceShellHandler.onExit`: `msg == 0` → "completed" row; else "finish error <code>".
   - Dialog `onDismiss` → `KrScriptActionHandler.onActionCompleted(node)` → `autoFinish` → `finishAndRemoveTask()`; `reloadPage` → reload page config; `updateBlocks` → `rootGroup.triggerUpdateByKey(...)` (re-executes those nodes' desc-sh/summary-sh/get-state).

`KrScriptActionHandler` interface surface (host contract): `openFileChooser(FileSelectedInterface): Boolean`, `onSubPageClick(PageNode)`, `onActionCompleted(RunnableNode)`, `addToFavorites(ClickableNode, AddToFavoritesHandler)` (with `onAddToFavorites(node, intent)`), `openParamsPage(ActionNode, View, Runnable): Boolean` (default false — lets the host replace the built-in params dialog).

## 3.4 Log streaming protocol

`ShellHandlerBase` message constants: `EVENT_START=0`, `EVENT_REDE=2`, `EVENT_READ_ERROR=4`, `EVENT_WRITE=6`, `EVENT_EXIT=-2`. (Producers emit only START/REDE/READ_ERROR/EXIT; EVENT_WRITE has a handler but no live producer.)

- **The only special script-output directive is `progress:[<current>/<total>]`** — a trimmed stdout line matching regex `^progress:\[[\-0-9\\]{1,}/[0-9\\]{1,}\]$` is consumed (not appended to the log) and routed to `onProgress(current,total)`: `current==-1` → indeterminate bar; `current==total` → hide bar; else determinate (`max=total`, `progress=current`). Documented in `docs/Extra.md`. No warn/success prefixes exist; "error" semantics = **stderr routing** (red text; sets `hasError`, blocking `autoOff`).
- Output translation: lines matching `@string:name` / `@string/name` (or `@dimen`) are resolved against app resources by `ShellTranslation` before display; fallback extracts `[(...)]` content from the line.
- Log colors (base handler): stdout `#00cc55`, stderr `#ff0000`, written commands `#808080`; `DialogLogFragment` remaps to theme colors (`kr_shell_log_basic/error/script/end`).
- **Bg-task UI** (`BgTaskThread.ServiceShellHandler`): ongoing notification (channel `kr_script_task_notification`; ids from `34050+`; `FLAG_NO_CLEAR|FLAG_ONGOING_EVENT` while running); custom big view (`kr_task_notification` layout) with `title(id)`, last ≤8 log rows (then `……`), progress bar (hidden when `total==current`, indeterminate when `total<0`), stop button visible iff `interruptable && forceStop!=null && !isFinished`; stop = broadcast `<package>.TaskStop.N<id>` → `forceStop.run()`. On finish: clearable notification, short msg "finished"/"has error". A "bg task started" help dialog shows at launch.
- **Hidden task** (`HiddenTaskThread.ServiceShellHandler`): zero UI; stderr rows collected; on exit a Toast with all error rows if any.
- "Hide to background" in the log dialog: `btn_hide` merely dismisses the dialog — the process keeps running with no notification; `btn_hide` is hidden when `reloadPage=true` (the UI must stay to reload). There is no dynamic dialog→notification conversion; bg/hidden is fixed by the `shell` attribute.

## 3.5 Interrupt handling

- `interruptable` (XML `interruptible`/`interruptable`, default `true`).
- `forceStopRunnable` is created iff `interruptable || shell=="bg-task"`. It runs `kill -s 1 \`pgrep -f <sessionTag>\`` (SIGHUP to every process whose cmdline contains the tag) via a one-shot root shell, closes all three process streams, then `destroyForcibly()` (API ≥ 26) else `destroy()`.
- Log dialog: `btn_exit` runs forceStop then dismisses; non-interruptible tasks get `forceStopRunnable=null` → `btn_exit` hidden until completion; dialog non-cancelable while running. Bg-task stop uses the same runnable from the notification broadcast.

## 3.6 before_start.sh and auto-run

- **before_start.sh** — §2.2 (`SplashActivity.BeforeStartThread`): su/sh process by root state, script run through the executor with the whole conf map as env, tag `"pio-splash"`, output streamed to splash (≤6 rows), `waitFor()` → `gotoHome()`; any failure still proceeds home.
- **Auto-run tasks**: `ActionPage` reads intent extra `"autoRunItemId"` (set when a RunnableNode shortcut is created, §4.4); after the first successful page load it builds `AutoRunTask{key=autoRunItemId, onCompleted}` → `ActionListFragment.triggerAction` → `rootGroup.triggerActionByKey(key)` (recursive; programmatically clicks the matching row) → result `false` → Toast `kr_auto_run_item_losted`. Interface: `onCompleted(result: Boolean?)`, `val key: String?`.

## 3.7 KeepShell API surface (`common` module)

- `KeepShell(rootMode=true)`: persistent su/sh. `doCmdSync(cmd)`: wraps cmd between `echo '|SH>>|'` / `echo '|<<SH|'` markers, returns trimmed captured stdout between markers, `"error"` on exception; `ReentrantLock` with 10 s lock-timeout → `tryExit()`; writes dispatched on an IO coroutine; stderr drained to logcat. `doCmdSync(cmd, ShellTranslation)` adds per-line translation. `checkRoot()`: `id -u`/`whoami`/`USER_ID=0` checks + `/cache` write probe; failure in rootMode → `tryExit()`. Also `tryExit()`, `isIdle`. No exit-code channel. Process spawn has a 10 s join timeout.
- `KeepShellPublic`: named instances `getInstance(key, rootMode)` / `destroyInstance` / `destroyAll`; `defaultKeepShell` + `secondaryKeepShell`, `getDefaultInstance()` picks the idle one; static `doCmdSync(String): String`, `doCmdSync(List<String>): Boolean` (`!= "error"`), `checkRoot()`, `tryExit()`.
- `KeepShellAsync(context, rootMode)` extends `ShellEvents` (`PROCESS_EVENT_STAR=0`, `CONTENT=1`, `ERROR_CONTENT=2`, `EXIT=-1` — EXIT declared but never sent): named instances, `setHandler`, `doCmd` with one internal retry, 20 s root-acquisition timeout.
- `AsynSuShellUnit(handler)`: one-shot root shell; messages what=0 (start ok/fail), 1 (stdout line), 5 (stderr line), 10 (exit bool); `exec`, `waitFor()`/`waitFor(next)`, `destroy`.
- common `ShellExecutor.java`: `getSuperUserRuntime()` / `getRuntime()`; static `setExtraEnvPath()` → `export PATH=<current sh PATH>:<extra>` written into the process via stdin (deliberately not exec env array — device crash workaround).
- pio dependents: `FragmentHome`, `DialogPower`, `CheckRootStatus`, `BatteryUnit`, `CpuFrequencyUtils`.

---
# §4. UI Interaction Contract

Source of truth: `krscript/src/main/java/com/omarea/krscript/ui/*` (ActionParamsLayoutRender, Params* widgets, PageMenuLoader, PageLayoutRender, ListItem*, ActionListFragment, DialogLogFragment), `krscript/.../config/IconPathAnalysis.kt`, `krscript/.../shortcut/ActionShortcutManager.kt`, `krscript/.../TryOpenActivity.kt`, `krscript/.../WebViewInjector.java`, `pio/.../ActivityFileSelector.kt` + `pio/.../ui/AdapterFileSelector.java`, `pio/.../ActionPage.kt`.

## 4.1 Action params — render, validate, collect, export

Dispatch (`ActionParamsLayoutRender.renderList`, first match wins): (1) `optionsFromShell != null && type ∉ {app,packages}` → dropdown (multi if `multiple`); (2) `bool`|`checkbox` → ParamsCheckbox; (3) `switch` → ParamsSwitch; (4) `seekbar` → ParamsSeekBar; (5) `file`|`folder` → ParamsFileChooserRender; (6) `app`|`packages` → ParamsAppChooserRender; (7) `color` → ParamsColorPicker; (8) else → ParamsEditText. Row wrapper `R.layout.kr_param_row`: `title` (top), `label` (left — hidden for bool/checkbox/switch where it becomes the widget's own text), `desc` (bottom); empty fields GONE.

**Execution timing** (`ActionListFragment.actionExecute`): on action click, per param, on a worker thread with a progress dialog: first `value-sh` → `valueFromShell`, then `options-sh` → `optionsFromShell` (shell result wins **only if** non-empty and ∉ {`error`,`null`}; lines split by `\n`; `value|label` split by `|`; otherwise static `options`). Then the params dialog renders (`>4` params → full-screen blur dialog `kr_dialog_params`, else `kr_dialog_params_small`; action `warning` shown at dialog top, `desc` under title).

Per-widget behavior:
- **ParamsEditText**: prefill `valueFromShell` else `value`; `readonly` → `isEnabled=false`; hint = `placeholder`, else `"min ~ max"` for int/number when min/max set. `ParamInfoFilter` InputFilter: `maxlength>=0` truncates; type `int` → `^[0-9]*$`; `number` → `^[\-.,0-9]*$`.
- **ParamsSeekBar**: `seekbar.max = max - min` (offset-based), progress = value − min; minus/plus buttons step **±1** (no `step` attr); value text = `progress + min`.
- **ParamsCheckbox / ParamsSwitch**: checked iff value(FromShell) `== "1"` **or** `toLowerCase() == "true"`; `label` attr = widget text.
- **ParamsSingleSelect**: `>5` options → dialog-style row (tap opens `DialogItemChooser` single); `≤5` → `Spinner` (disabled when readonly). Current index = first match of `valueFromShell` then `value` against `option.value` (`getParamOptionsCurrentIndex`).
- **ParamsMultipleSelect**: tap → `DialogItemChooser(multi=true)`; display row shows labels joined by `，` (full-width comma), value view = values joined by `separator`, plus a count; initial checked set = `value(FromShell).split(separator)`.
- **ParamsColorPicker**: EditText + live validity icon + color preview; separate ARGB-seekbar dialog; confirm writes `#%02x%02x%02x%02x` (A,R,G,B).
- **ParamsFileChooserRender**: display TextView + path EditText (both prefilled from valueFromShell/value; tagged with `name`). `editable=true` → EditText enabled with hint; else read-only. Browse → host `FileChooserInterface.openFileChooser(FileSelectedInterface)`; the interface exposes `mimeType()` (param.mime or null), `suffix()` (param.suffix or null), `type()` (`TYPE_FILE=0`/`TYPE_FOLDER=1`), `onFileSelected(path?)`.
- **ParamsAppChooserRender**: installed packages (`GET_UNINSTALLED_PACKAGES`, filtered by static/options-sh values when present; type `packages` additionally re-adds missing option entries). `DialogAppChooser(darkMode, packages, multiple)`. Single → value = packageName; multi → values joined by `separator`, labels by `，`.

**Collection/validation** (`readParamsValue`): EditText → text (int/number: range-check `min ≤ v ≤ max` — but NumberFormatException is swallowed, so non-numeric passes; color: `Color.parseColor` else throw "invalid color"); CheckBox/Switch → `"1"`/`"0"`; SeekBar → `progress + min`; TextView → its text (file/app/single-select value views); Spinner → `SelectItem.value`. Empty + `required` → throw `"<title> <label> (<name>) " + do_not_empty` (aborts the run with a toast); empty non-required → exported as `""`.

**Export to script**: `HashMap<name,value>` → `ScriptEnvironmen.executeShell` → `export key='value'` lines into the process stdin before the script (§3.2/§3.3). The framework never does textual `$name` substitution.

## 4.2 Page menu semantics

- Parse: §1.9 (menu items inside `<page>`; `pageMenuOptionsSh`; `pageHandlerSh`).
- Load precedence (`PageMenuLoader.load()`, exact): `if (pageMenuOptionsSh.isNotEmpty()) { execute; parse lines "key|title" … } else if (pageMenuOptions != null) { menuOptions = pageMenuOptions }`. **options-sh takes precedence over static options — but the original has a bug: the created `PageMenuOption`s are never added to `menuOptions`, so an options-sh page yields an EMPTY menu in practice.** Preserve or fix deliberately; do not "fix" silently if byte-level parity is the goal (recommend: implement the documented precedence and note the deviation).
- Invocation (`ActionPage`): `onCreateOptionsMenu` adds each option (`menu.add(-1, i, i, title)`); `isFab` options instead drive the FAB (icon: `type=="file" && iconPath empty` → `kr_folder`; `iconPath` set → `IconPathAnalysis.loadLogo(option, useDefault=false)`; default `kr_fab`). Click dispatch by `type`: `refresh`/`reload` → `recreate()`; `exit`/`finish`/`close` → `finish()`; `file` → chooser, then run handler with params `{state=key, menu_id=key, file=path, folder=path}`; anything else → run `currentPageConfig.pageHandlerSh` in DialogLogFragment with params `{state=key, menu_id=key}`. onDismiss: `autoFinish` → finish(); `reloadPage` → recreate().

## 4.3 Icon vs logo precedence (`IconPathAnalysis`, exact)

- **List rows use iconPath ONLY**: `loadIcon()` tries `iconPath` via PathAnalysis, returns null otherwise — no logo fallback, no default icon (`ListItemClickable` hides the icon view unless `loadIcon` returns non-null).
- **Logo→icon fallback applies only to shortcuts/FAB**: `loadLogo()` tries `logoPath` first, then `iconPath`, then default `R.drawable.kr_shortcut_logo` when `useDefault=true` (FAB passes `useDefault=false` and falls back to `kr_fab`/`kr_folder` instead).

## 4.4 Shortcut protocol (`ActionShortcutManager` + hosts)

- Trigger: long-press on a row, enabled iff `key.isNotEmpty() && allowShortcut != false` (keys starting with `@` default `allowShortcut=false`); a shortcut-hint icon is shown on such rows. Empty key → "unsupported" alert. Then `KrScriptActionHandler.addToFavorites` → confirm dialog → `addShortcut(intent, loadLogo(node), node)`.
- Host intent (built by `ActionPage`/`MainActivity.addToFavorites`): component = `ActionPage`, flags `FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS | FLAG_ACTIVITY_NO_HISTORY`, extras `"page"` = PageNode (the node itself for a PageNode, else the current page config) and — for RunnableNode — `"autoRunItemId"` = node key.
- Because Serializable extras can't be pinned, `ActionShortcutManager.addShortcut` replaces `"page"` with `"shortcutId"` (id = `System.currentTimeMillis()`; the PageNode is persisted via `ObjectStorage`).
- Shortcut id = `"addin_" + config.index`; label = title; icon = logo bitmap; inner intent `ACTION_MAIN` + same extras + `NO_HISTORY|EXCLUDE_FROM_RECENTS`. API ≥ 26: `ShortcutManager.requestPinShortcut` (existing pinned id → `updateShortcuts`); pre-26: broadcast `com.android.launcher.action.INSTALL_SHORTCUT` with `EXTRA_SHORTCUT_NAME/ICON/INTENT`, `"duplicate"=false`.
- Re-entry: `ActionPage` reads `"page"` or `"shortcutId"`; cold start → redirect to `SplashActivity` (`"JumpActionPage"=true`, extras copied); after load, `autoRunItemId` triggers the row by key (§3.6); `onDestroy` → `setExcludeFromRecents` when task root.

## 4.5 File selector contract

`ActivityFileSelector` + `AdapterFileSelector`:
- Intent extras IN: `"extension"` (String; `"."` auto-prefixed; appended to title), `"mode"` (Int: `MODE_FILE=0`, `MODE_FOLDER=1`).
- Storage permission request code **111** (READ+WRITE_EXTERNAL_STORAGE). Starts at external storage root. **It does NOT run as root** — listing is plain `java.io.File` as the app user.
- Result: `RESULT_OK` with extra `"file"` = **absolute path**; back at root → `RESULT_CANCELED`. Adapter: dirs first, then case-insensitive name sort; `"..."` parent row when `hasParent`; folder mode lists dirs only and selects via **long-press + confirm**; file mode filters `name.endsWith(extension)`, selects via tap + confirm. No multi-select. Manifest: `excludeFromRecents="true"`.

Caller contract (`ActionPage`/`MainActivity`): request codes **`ACTION_FILE_PATH_CHOOSER = 65400`** (SAF `ACTION_GET_CONTENT`, `intent.type = mimeType() ?: "*/*"`, `CATEGORY_OPENABLE`) and **`ACTION_FILE_PATH_CHOOSER_INNER = 65300`** (ActivityFileSelector). Routing: missing READ_EXTERNAL_STORAGE → `requestPermissions(..., 2)` and abort; `type()==TYPE_FOLDER` → inner selector (mode=1); `suffix()` non-empty → inner selector (extension=suffix); else SAF. SAF uri → `FilePathResolver.getPath` → `onFileSelected(absPath)`; inner result → `data.getStringExtra("file")`.

## 4.6 TryOpenActivity (activity/slice opener — NOT file-open)

Input string `activity`:
- Starts with `"am "` → run via root shell (`KeepShellPublic.doCmdSync`), success iff output contains `"Start"`.
- Contains `"/"` → `Intent(ACTION_VIEW)` + `setClassName(package, class)` (leading `.` class resolved against package) + `FLAG_ACTIVITY_NEW_TASK`.
- Otherwise → `Intent(action)` + NEW_TASK.
- On `SecurityException` → root fallback `am start-activity -W -n pkg/class` (or `-a action`), success iff output contains `"ok"`; else toast `kr_slice_activity_fail`.

(The "mime from suffix / file uri" behavior lives in `Suffix2Mime` + ActivityFileSelector + `ACTION_GET_CONTENT` — TryOpenActivity itself has no file/mime logic.)

## 4.7 WebView JS bridge (`WebViewInjector` → JS object `KrScriptCore`)

| JS-callable | Signature | Behavior |
|---|---|---|
| `rootCheck` | `boolean rootCheck()` | Root availability |
| `executeShell` | `String executeShell(String script)` | Sync root exec, returns stdout only (supports `file:///android_asset` script paths) |
| `executeShellAsync` | `boolean executeShellAsync(String script, String callbackFunction, String envJson)` | New root process; `envJson` (stringified object) → env params; streams via `evaluateJavascript("<callback>({...})")`: `{type:2, message}` per stdout line, `{type:4, message}` per stderr line, `{type:-2, message:"<exitCode>"}` at exit (constants = ShellHandlerBase EVENT_REDE/READ_ERROR/EXIT; matches `docs/js-engine/WebBrowser.md`) |
| `extractAssets` | `String extractAssets(String assets)` | Extracts asset to disk, returns path or null |
| `fileChooser` | `boolean fileChooser(String callbackFunction)` | Host chooser (type=FILE, mime `*/*`, suffix null); callback `({"absPath": path\|null})` |

WebSettings: `javaScriptEnabled=true`; `allowFileAccess = allowUniversalAccessFromFileURLs = allowFileAccessFromFileURLs = credible` (`credible` == url starts with `file:///android_asset`); `allowContentAccess=true`; `useWideViewPort=true`. DownloadListener → storage permission request code **2** → confirm dialog (url/mime/size) → `Downloader.downloadBySystem`.

Host page (`ActionPageOnline`, §5.7): extras `"config"`/`"url"`; custom js alert/confirm dialogs; non-http(s) schemes forwarded to external `ACTION_VIEW`; page title adopted on load; back key → `webView.goBack()`; own SAF chooser request 65400; download extras `downloadUrl`/`taskId`/`autoClose`.

## 4.8 Row rendering & interaction behaviors

`PageLayoutRender` + `ListItem*`: PageNode → row + arrow glyph; ActionNode → row + run glyph (`kr_list` when it has params); PickerNode → row + picker glyph; SwitchNode → row + Switch widget; TextNode → text block (root layout vs white card inside groups); GroupNode → section container. Common (`ListItemView`): title/desc/summary hidden when empty; after any execution `updateViewByShell()` re-runs `desc-sh`/`summary-sh`; `reload="id1,id2"` → `triggerUpdateByKey` refreshes keyed nodes. Click dispatch by node type; long-click = shortcut (§4.4). Gate (`nodeUnlocked`): target/min/max-sdk dialogs; `lock` script output ∈ {`unlock`,`unlocked`,`false`,`0`} → unlocked, else toast the output.

- **Switch**: `get` executed at parse time AND on every refresh; truthy = `== "1"` or lowercase `== "true"` (`"error"` → false at parse). Click → confirm/warning → `set` with `state=1/0` → row re-polls `get`.
- **Picker**: click → confirm/warning → worker thread runs `get` + options load → chooser dialog → `set` with `state=<value(s)>`.
- **Action**: confirm (title+desc) or warning (standalone only when **no params**; with params it shows in the params dialog) → params flow (§4.1) → execute.
- **Log dialog** (`DialogLogFragment`): full-screen streaming; colored stdout/stderr/command lines; `progress:[cur/total]` protocol; copy / hide-to-background / force-stop buttons per `interruptable`; `auto-off` closes only on success (any stderr suppresses); dismiss → `auto-finish` → finish page, `reloadPage` → reload, `updateBlocks` → keyed refresh; `shell="bg-task"` → BgTaskThread notification, `"hidden"` → HiddenTaskThread (single-instance guard).
- **Text rows**: spans for size/color/bgColor/bold/italic/underline/align/breakRow; `link` span → `ACTION_VIEW`+NEW_TASK; `activity` span → TryOpenActivity; `script`/`run` span → execute + result dialog.
- **Page rows**: click priority `link` → browser; `activity` → TryOpenActivity; else sub-page: `html` → ActionPageOnline (extra `"config"`), `config-sh`/`config` → ActionPage (extra `"page"`). Page load pipeline (`ActionPage.loadPageConfig`): `before-load` → config-sh (preferred) or config XML → `after-load` → non-empty ? `load-success` : (`load-fail` + toast + finish).

---
# §5. PIO App-Level Features Contract

Source of truth: `pio/src/main/java/com/projectkr/shell/*` @ b90f2cc + `pio/src/main/AndroidManifest.xml` + `pio/src/main/res/values/strings.xml`.

## 5.1 Application class & manifest

- `PIO.kt`: `class PIO : Application()` — empty body; no crash handler, no init. Manifest `android:name=".PIO"`.
- Manifest permissions: `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `INTERNET`, `VIBRATE`, `SYSTEM_ALERT_WINDOW`, `com.android.launcher.permission.INSTALL_SHORTCUT`, `UNINSTALL_SHORTCUT`. `<application allowBackup="true" usesCleartextTraffic="true" theme="@style/AppTheme">`; `<meta-data android:name="android.max_aspect" value="2.8"/>`.
- Activities (all `screenOrientation="portrait"`, all with the long `configChanges` list `keyboardHidden|orientation|uiMode|layoutDirection|screenLayout|colorMode|fontScale|smallestScreenSize|density|keyboard|screenSize|touchscreen`):
  - `.SplashActivity` — LAUNCHER entry (`MAIN` + `LAUNCHER`).
  - `.MainActivity`, `.ActionPage`, `.ActionPageOnline` — `MAIN` + `DEFAULT` filters (internal navigation/shortcut targets).
  - `.ActivityFileSelector` — `excludeFromRecents="true"`, `launchMode="standard"`.
- **No services declared** (original FloatMonitor is a raw WindowManager view, not a Service). No `POST_NOTIFICATIONS`.

## 5.2 SplashActivity — init flow

1. If `ScriptEnvironmen.isInited()` and `isTaskRoot` → `gotoHome()` immediately (no UI).
2. Splash layout; nav bar = `R.color.splash_bg_color`; transparent status bar (`LAYOUT_FULLSCREEN|LAYOUT_STABLE`, API ≥ 21).
3. `checkRoot()`: `CheckRootStatus(this, next).forceGetRoot()`.
4. On root callback: status text `pio_permission_checking`; (`checkFileWrite` is **commented out** — it would have requested storage/MOUNT_UNMOUNT_FILESYSTEMS/REQUEST_IGNORE_BATTERY_OPTIMIZATIONS/WAKE_LOCK with request code `0x11`) → `startToFinish()`.
5. `startToFinish()`: `KrScriptConfig().init(this)` (→ executor substitution + toolkit extraction, §2/§3.1). If `beforeStartSh` non-empty → `BeforeStartThread`: process = `ShellExecutor.getSuperUserRuntime()` (root) else `getRuntime()`; `ScriptEnvironmen.executeShell(ctx, out, beforeStartSh, config.getVariables(), null, "pio-splash")`; stdout+stderr streamed line-by-line into the splash TextView (max 6 rows, `……\n` elision); `process.waitFor()` → `gotoHome()`. Otherwise `gotoHome()` directly.
6. `gotoHome()`: if intent extra `"JumpActionPage"==true` → start `ActionPage` **with the original extras copied**; else start `MainActivity`; `finish()`.
- Root denied: dialog `error_root` with Retry / Exit (`exitProcess(0)`) / Skip (Skip only when `R.bool.force_root != true`; shipped value `false`). 15 s `su` watchdog → confirm dialog `error_su_timeout` with Retry/Exit.
- Config missing: `kr-script.conf` open failure swallowed → built-in defaults. **No app update check exists** anywhere in the original (verified by grep; updates are delegated to user-supplied `before_start_sh`).

## 5.3 CheckRootStatus (`pio/.../permissions/CheckRootStatus.kt`)

- `forceGetRoot()`: cached `lastCheckResult` → post next; else background thread `rootStatus = KeepShellPublic.checkRoot()`; success → post next; failure → `KeepShellPublic.tryExit()` + non-cancelable AlertDialog (`error_root`; Retry → tryExit + interrupt + retry; Exit → `exitProcess(0)`; neutral Skip unless `force_root`).
- Watchdog thread: `Thread.sleep(1000 * 15)`; if not completed → `tryExit()` + confirm `error_su_timeout` (Retry/Exit).
- Companion: `lastCheckResult` (static cached boolean, gates the home tab); `grantPermission(context)` runs root cmd `dumpsys deviceidle whitelist +<package>;` when `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` not held (API ≥ M). (Storage auto-grant block commented out.)

## 5.4 MainActivity — tabs, page loading, menus

- `ThemeModeState.switchTheme(this)` first in `onCreate`; toolbar; title `app_name`.
- **Tabs** via `TabHost` + `TabIconHelper` (indicator = inflated `list_item_tab` with icon+label; non-selected tabs alpha `0.3f`, selected `1.0f`; tab id `"tab_<n>"`):
  - Home ("首页", drawable `tab_home`, content `main_tabhost_cpu`) added **only if** `CheckRootStatus.lastCheckResult && krScriptConfig.allowHomePage`; else GONE. Content = `FragmentHome`.
  - Favorites ("收藏夹", `tab_favorites`, `main_tabhost_2`) added only if the favorites item list is non-empty; else GONE.
  - Pages ("全部", `tab_pages`, `main_tabhost_3`) added only if the page list is non-empty; else GONE.
- **Page loading flow**: worker thread + `ProgressBarDialog` (`please_wait`): `krScriptConfig.pageListConfig` and `.favoriteConfig` (both PageNode); `getItems()`: `pageConfigSh` non-empty → `PageConfigSh(this, sh, null).execute()`; null result && `pageConfigPath` non-empty → `PageConfigReader(ctx, path, null).readConfigXml()`. Rendered via `ActionListFragment.create(items, handler, null, ThemeModeState.getThemeMode())` into `list_favorites` / `list_pages`.
- `KrScriptActionHandler` (both tabs): `onActionCompleted` → `autoFinish` → `finishAndRemoveTask()`; `reloadPage` → reload that tab on a thread. `addToFavorites` → builds the ActionPage intent (§4.4). `onSubPageClick` → `OpenPageHelper.openPage`. `openFileChooser` → §4.5 routing.
- Startup storage permission request: READ+WRITE_EXTERNAL_STORAGE, code `111`.
- **Options menu** (`R.menu.main`: `action_graph`, `option_menu_reboot`, `option_menu_info`, all `showAsAction="ifRoom"`; `action_graph` visible only when the home tab is visible):
  - info → about dialog (`dialog_about`) with `transparent_ui` toggle → `ThemeConfig.setAllowTransparentUI(checked)` (storage-permission gate) + `activity.recreate()`.
  - reboot → `DialogPower(this).showPowerMenu()`.
  - graph → `FloatMonitor.isShown` ? `hidePopupWindow()` : overlay-permission check (`Settings.canDrawOverlays`, API ≥ 23) → `showPopupWindow()` + toast `float_monitor_tips`; no permission → opens `android.settings.APPLICATION_DETAILS_SETTINGS` for own package + toast `permission_float`.
- MainActivity consumes **no** incoming intent extras (shortcut/open-page intents target ActionPage).

## 5.5 ActionPage

- **Extras contract**: `"page"` (Serializable PageNode), `"shortcutId"` (String → `ActionShortcutManager.getShortcutTarget` restores the PageNode), `"autoRunItemId"` (String), inbound `"JumpActionPage"`.
- **Cold-start guard**: `!ScriptEnvironmen.isInited()` → start SplashActivity (`FLAG_ACTIVITY_NO_ANIMATION`, extras copied, `JumpActionPage=true`), `finish()`.
- Pre-processing: `page.activity` non-empty → `TryOpenActivity.tryOpen()` then `finish()`. `page.onlineHtmlPage` non-empty → starts `ActionPageOnline` (NEW_TASK, extra `"config"`) **and continues** loading the config page beneath. `page.title` → activity title. Both `pageConfigPath` and `pageConfigSh` empty → `setResult(2)` + `finish()`.
- **Load lifecycle** (`loadPageConfig()`, first call from `onResume`, `actionsLoaded` guard; worker thread + progress dialogs):
  1. `beforeRead` sh (dialog `kr_page_before_load`).
  2. `pageConfigSh` → `PageConfigSh(this, sh, this).execute()`; fallback `pageConfigPath` → `PageConfigReader(ctx, path, pageConfigDir)` (dialog `kr_page_loading`).
  3. `afterRead` sh (dialog `kr_page_after_load`).
  4. Success (non-empty): optional `loadSuccess` sh (`kr_page_load_success`); `ActionListFragment.create(items, handler, autoRunTask, themeMode)`; `autoRunTask.key = autoRunItemId` (first load only; miss → toast `kr_auto_run_item_losted`).
  5. Failure/empty: optional `loadFail` sh (`kr_page_load_fail`) + toast + `finish()`.
- **Page menu**: §4.2 (PageMenuLoader, FAB, type dispatch, pageHandlerSh params `{state, menu_id, file, folder}`; execution = non-cancelable `DialogLogFragment`; onDismiss: autoFinish→finish, reloadPage→recreate, updateBlocks→TODO in the original).
- File chooser: §4.5 contract (codes 65400/65300, permission code 2, folder mode support).
- `onDestroy`: `setExcludeFromRecents()` when `isTaskRoot` (via `ActivityManager.appTasks`).

## 5.6 ActionPageOnline

- Extras: `"title"`; `"config"` or `"url"` → WebView URL; download extras `"downloadUrl"`, `"taskId"` (default random UUID), `"autoClose"` (bool).
- WebView: `WebChromeClient` js alert/confirm → non-cancelable AlertDialogs; `WebViewClient`: progress dialog on page start, hide + adopt page title on finish; `shouldOverrideUrlLoading`: non-`http*` schemes → external `ACTION_VIEW`.
- `WebViewInjector(webview, fileChooser).inject(this, credible = url.startsWith("file:///android_asset"))` — §4.7 bridge.
- Web file chooser: `ACTION_GET_CONTENT` `*/*` + `CATEGORY_OPENABLE`, request `65400`, storage permission request `2`, uri → `FilePathResolver`.
- **Download handling**: `Downloader.downloadBySystem(url, null, null, taskAliasId)`; permission gate (else `saveTaskStatus(taskAliasId, 0)` + request `2` + help dialog). Progress UI: `Timer` at **200 ms delay / 500 ms period** polling `DownloadManager.Query` → name/progress/title updates + `saveTaskStatus(taskAliasId, ratio)`; copy-to-clipboard buttons; at 100%: `saveTaskCompleted(downloadId, absPath)`, title `kr_download_completed`, `setResult(0, Intent("absPath"))`, `finish()` if `autoClose`.
- Back key → `webView.goBack()` when possible. Light-mode white status/nav bars with LIGHT_STATUS_BAR (+LIGHT_NAVIGATION_BAR O+).
- `Downloader` (`krscript/.../downloader/`): `downloadBySystem` (DownloadManager into `DIRECTORY_DOWNLOADS`, guessed filename, VISIBILITY_VISIBLE_NOTIFY_COMPLETED, metered/roaming allowed; history SP `kr_downloader`; auto-registers `DownloaderReceiver`), `downloadByBrowser`, `saveTaskStatus` (private file `downloader/status/<taskId>`), `saveTaskCompleted` (history + `downloader/path/<md5>` + `downloader/result/<taskId>`).

## 5.7 FragmentHome — dashboard

Widgets (exact ids in `fragment_home.xml`):
- **RAM card**: donut `home_raminfo` (`RamChatView`) + `home_raminfo_text` (`"x% (yGB)"`); button `home_clear_ram` runs `sync` + `echo 3 > /proc/sys/vm/drop_caches` + `echo 1 > /proc/sys/vm/compact_memory` via `KeepShellPublic.doCmdSync`, re-reads after 600 ms, toast `monitor_cache_cleared`.
- **SWAP card**: donut `home_swapstate_chat` + `home_zramsize_text` (`"x% (N.NGB)"` or `"(NMB)"`); button `home_clear_swap` runs `sync` + `echo 1 > /proc/sys/vm/compact_memory`, toast `monitor_ram_cleared`. Data: `free -m | grep Swap` via root shell.
- **GPU card**: donut `home_gpu_chat`, `home_gpu_freq` (`GpuUtils.getGpuFreq() + "Mhz"`), `home_gpu_load` (%). Sources: freq `/sys/kernel/gpu/gpu_clock` or `/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq`; load `/sys/kernel/gpu/gpu_busy`, `…/devfreq/gpu_load`, `…/gpu_busy_percentage`.
- **CPU summary**: `cpu_core_count`, `cpu_core_total_load` (loads map key `-1`), donut `home_cpu_chat`.
- **Per-core grid**: `cpu_core_list` (`OverScrollGridView`, `numColumns=2` when < 6 cores) with `AdapterCpuCores` — per-core current/max/min freq (kHz→MHz by stripping the last 3 chars) + load ratio.
- **Data sources**: `CpuFrequencyUtils` (`getCoreCount()`, `getCurrentFrequency("cpuN")`, cached min/max, static cached `getCpuLoad()` → `CpuLoadUtils` reading `/proc/stat` per-core `^cpu` lines).
- **Refresh cadence**: `Timer` `0, 1000` ms in `onResume`, cancelled in `onPause`; min/max freq caches cleared every 5 ticks (`updateTick > 5`); RAM refreshed per resume + after clears.
- **Root gating**: the whole tab exists only when rooted AND `allow_home_page="1"`. No battery widget on home (battery lives in FloatMonitor).

## 5.8 FloatMonitor (`pio/.../FloatMonitor.kt`, package `com.omarea.vtools`)

- Permission: `Settings.canDrawOverlays` (API ≥ 23) else toast `permission_float` and abort.
- Window: `TYPE_APPLICATION_OVERLAY` (O+) / `TYPE_SYSTEM_ALERT`; WRAP_CONTENT; TRANSLUCENT; gravity `TOP|LEFT` at (0,0); flags `FLAG_NOT_TOUCH_MODAL | FLAG_NOT_FOCUSABLE`; layout `R.layout.fw_monitor`. **Not a service** — a raw WindowManager view owned by the activity context.
- Contents: CPU donut `fw_cpu_load` + `fw_cpu_freq`; GPU donut `fw_gpu_load` + `fw_gpu_freq`; RAM views exist in the layout but the RAM update code is **commented out**; battery `fw_battery_chart` (level), `fw_battery_temp` (°C), `fw_battery_level` (%).
- Data: CPU freq = max of `cat /sys/devices/system/cpu/cpu*/cpufreq/cpuinfo_cur_freq` (displayed via subFreqStr kHz→MHz + "Mhz"); CPU load = `CpuLoadUtils().cpuLoadSum` (`/proc/stat`, clamped ≥ 0); GPU = GpuUtils; battery = `BatteryUnit().getBatteryTemperature()` = parse `dumpsys battery` (first occurrence each of `status`/`level`/`temperature`) into `BatteryStatus{level, temperature, status, statusText, …}`.
- Temperature text colors: ≥ 54 → `rgb(255,15,0)`; ≥ 49 → `color_load_veryhight`; ≥ 44 → `color_load_hight`; > 34 → `color_load_mid`; else `color_load_low`.
- Loop: `Timer` `0, 1000` ms. Lifecycle: companion `isShown`; **drag-to-move** via touch listener; **double-tap** (two taps < 300 ms apart, < 15 px movement, tap duration < 300 ms) → `hidePopupWindow()`.

## 5.9 DialogPower — exact commands

`DialogHelper.customDialog` over `dialog_power_operation`; each row dismisses then runs `KeepShellPublic.doCmdSync(...)` with (from `pio/src/main/res/values/strings.xml`):
- 关机 `power_shutdown`: `sync;svc power shutdown || reboot -p;`
- 重启 `power_reboot`: `sync;svc power reboot || reboot;`
- 热重启 `power_hot_reboot`: `sync;am restart || busybox killall system_server;`
- Recovery `power_recovery`: `sync;reboot recovery;`
- Fastboot `power_fastboot`: `sync;reboot bootloader;`
- 9008(EDL) `power_emergency`: `sync;reboot edl;`

## 5.10 OpenPageHelper

`openPage(pageNode)`: `onlineHtmlPage` non-empty → `ActionPageOnline` intent (NEW_TASK, extra `"config"`); `pageConfigSh` or `pageConfigPath` non-empty → `ActionPage` intent (NEW_TASK); when several are set the online page wins the component; final `putExtra("page", pageNode)` + `startActivity`; exceptions → Toast. (Note: in-page row clicks in `ActionListFragment` short-circuit `link`/`activity` before this helper is consulted.)

## 5.11 Theme

- `common/.../ui/ThemeMode.kt`: `isDarkMode=false`, `isLightStatusBar=false` (two flags only).
- `ThemeConfig`: SP file `"theme"`, key `"TransparentUI"` (bool, default false); setter → `activity.recreate()`.
- `ThemeModeState.switchTheme(activity)`: night from `UiModeManager.MODE_NIGHT_YES`. TransparentUI allowed **and** storage perms → wallpaper themes `AppThemeWallpaper` (dark) / `AppThemeWallpaperLight` (light); live wallpaper → `FLAG_SHOW_WALLPAPER`; static → window background = wallpaper drawable. Else dark → `AppThemeDark`. Light mode → `isLightStatusBar = SDK ≥ M` + decor flags `LAYOUT_FULLSCREEN|LAYOUT_STABLE|LIGHT_STATUS_BAR` (+`LIGHT_NAVIGATION_BAR` O+). `getThemeMode()` feeds ActionListFragment/DialogLogFragment dark rendering. No manual mode list; no Monet.

## 5.12 Constants (`pio/.../utils/Constants.java`) & contract values

Sysfs: `cpu_dir=/sys/devices/system/cpu/cpu0/`; `cpufreq_sys_dir=…/cpufreq/`; `scaling_min_freq`; `scaling_cur_freq=…/cpuinfo_cur_freq` (name/value mismatch is contractual — "scaling_cur_freq" points at the **cpuinfo_cur_freq** node); `scaling_max_freq`; `scaling_governor`; `scaling_available_freq=…/scaling_available_frequencies`; `scaling_available_governors`; `sched_boost=/proc/sys/kernel/sched_boost`; thermal `/sys/module/msm_thermal/core_control/enabled`, `…/vdd_restriction/enabled`, `…/parameters/enabled`.

Elsewhere in code: request codes `65400` (SAF file), `65300` (internal selector), `111` (storage), `2` (storage for chooser/download), `0x11` (splash, disabled); SP files `"theme"` (`TransparentUI`), `"kr-script-config"` (`executor`, `toolkitDir`), `"kr_downloader"`; intent extras `"page"`, `"shortcutId"`, `"autoRunItemId"`, `"JumpActionPage"`, `"config"`, `"url"`, `"title"`, `"downloadUrl"`, `"taskId"`, `"autoClose"`, `"extension"`, `"mode"`; result extras `"file"`, `"absPath"`; shortcut broadcast `com.android.launcher.action.INSTALL_SHORTCUT` (pre-O); bg-task stop broadcast `<package>.TaskStop.N<id>`; notification channel `kr_script_task_notification`.

## 5.13 Chart views & misc UI (`pio/.../ui/`)

- `TabIconHelper` — §5.4.
- `CpuChartView` / `RamChatView` / `FloatMonitorChartView` / `FloatMonitorBatteryView`: donut/ring ratio charts (`setData(total, free)`; FloatMonitor variants add ValueAnimator).
- `CpuBarChartView` (`CpuChartBarView`): vertical bar history (`LinkedBlockingQueue<Int>`, 5–6 samples).
- `CpuCoresAdapter` (`AdapterCpuCores`): per-core freq/load grid.
- `BatteryStatus.java`: `time, level=-1, temperature=-1f, status, statusText, io=-1, packageName, mode`. `utils/BatteryUnit.kt`: `dumpsys battery` parsing + `/sys/class/power_supply/bms/uevent` info. `utils/Files.kt`: `getDirFreeSizeMB(dir)` via StatFs.

---
# §6. Gap Analysis (rewrite vs contract)

Rewrite files audited: `core/src/main/java/com/projectkr/shell/core/{config/{PageConfigReader,PathAnalysis,ShellRunner,Suffix2Mime}.kt, executor/{ScriptEnvironment,ExtractAssets}.kt, model/*.kt}` and `app/src/main/java/com/projectkr/shell/{MainActivity.kt, ui/KrScriptApp.kt, ui/theme/Theme.kt, shell/{KeepShellRunner,RootShellRunner}.kt, service/FloatMonitorService.kt, shortcut/ShortcutHelper.kt}` + `app/src/main/AndroidManifest.xml`, `app/build.gradle.kts`, `app/src/main/assets/{kr-script.conf, sample.xml, kr-script/executor.sh}`.

Headline: **the rewrite is at parser/model parity but demo-level runtime parity.** The XML parser (`core/.../config/PageConfigReader.kt`) is a near-verbatim port of the original (≈83-line diff: package/ctor/ShellRunner only), and the model classes mirror the original field-for-field. Almost everything *downstream* of parsing — execution protocol, config, page lifecycle, menus, web bridge, shortcuts, file selection, dashboards — is missing or deviating. Many parsed fields are dead: `beforeRead`, `afterRead`, `loadSuccess`, `loadFail`, `pageMenuOptionsSh`, `pageHandlerSh`, `lockShell`, `locked`, `confirm`, `warning`, `autoOff`, `autoFinish`, `interruptable`, `reloadPage`, `updateBlocks`, `shell` modes, SDK-range fields, `iconPath`/`logoPath`, `allowShortcut`, `pageConfigSh` are parsed but never consumed.

## 6.1 Schema / parser gaps

| Item | Status | Deviation |
|---|---|---|
| Node types & attribute parsing (group/page/action/switch/picker/text/resource/param/option/menu, all aliases) | [DONE] | Near-verbatim port; truthy sets, defaults, and quirks preserved (`required` case-sensitivity, `interruptable` duplicate-value quirk, sticky-last-param, group flattening, ignored root tag) |
| `<slice align>` | [PARTIAL] | Rewrite maps `left`→`ALIGN_NORMAL`, `right`→`ALIGN_OPPOSITE`, and drops the API≥28 gate (original: `ALIGN_LEFT`/`ALIGN_RIGHT` on P+ only). Equal in LTR; differs in RTL |
| Parse-time script execution (`visible`/`support`/`desc-sh`/`summary-sh`/switch `get`/picker `get`) | [PARTIAL] | Rewrite `executeResultRoot` builds `vitualRootNode` but never passes it; no `PAGE_CONFIG_*`/`PAGE_WORK_*` env exports; script strings starting `file:///android_asset/` are written verbatim as script text instead of being extracted as asset files |
| `<resource>` | [PARTIAL] | Parse DONE; rewrite `ExtractAssets` dedups per instance (not per process) and copies to `filesDir` without toolkit-protection/ownership concerns of the original |
| Model classes (all 13 + SelectItem) | [DONE] | Field-for-field mirrors; SelectItem relocated from `com.omarea.common.model`, adds `selected`/`toString` |
| `ParamInfoFilter` (maxlength / int / number input filtering) | [MISSING] | No equivalent anywhere |
| `KrScriptActionHandler` host interface | [MISSING] | Only ad-hoc Compose callbacks |
| `ShellHandlerBase` (EVENT_* model, `progress:[x/y]` parsing, colored log model) | [MISSING] | No equivalent anywhere |
| `PathAnalysis` | [PARTIAL] | DONE: assets prefix, absolute direct read, `./` strip, assets-parent concat. MISSING: root fallback read (`useRootOpenFile`), `../` resolution, assets absolute fallback, disk fallback to app private dir |
| `PageConfigSh` (sh → path/inline-XML enumeration) | [MISSING] | Class absent; `config-sh` parsed but never executed |

## 6.2 Config (`kr-script.conf`) gaps

| Item | Status | Deviation |
|---|---|---|
| `executor_core` | [PARTIAL] | Executor mechanism exists (`$({VAR})` substitution, cache-and-run) but path hardcoded (`kr-script/executor.sh`); conf never consulted; init is lazy (first execution) not splash-time; no `kr-script-config` SP cache |
| `toolkit_dir` | [MISSING] | No toolkit extraction; `TOOLKIT` env `""`; no toolkit assets shipped |
| `page_list_config` | [MISSING] | Root list hardcoded to `file:///android_asset/sample.xml` (`KrScriptApp.kt:130,317`); shipped `app/src/main/assets/kr-script.conf` is dead weight (references `kr-script/more.xml` which doesn't even exist in rewrite assets) |
| `page_list_config_sh` | [MISSING] | No sh-based enumeration |
| `favorite_config` | [MISSING] | No XML-driven favorites page; favorites are local SP bookmarks |
| `favorite_config_sh` | [MISSING] | — |
| `allow_home_page` | [MISSING] | Home tab unconditional; no conf gate, no root gate |
| `before_start_sh` | [MISSING] | No pre-start script at all; splash = fixed 1 s delay |
| Conf file discovery & parser (`key="value"` format, `R.string.kr_script_config`, unknown-key retention, static single-parse) | [MISSING] | No parser exists |
| Init ordering (root → conf → executor/toolkit → before_start → page lists) | [MISSING] | `MainActivity` = bare `setContent{KrScriptApp()}` |
| Tab visibility semantics (home gated root+conf; list tabs hidden when empty; `action_graph` tie) | [MISSING] | All four bottom tabs always visible |
| Adjacent surface: `force_root` bool, `kr-script-config` SP, `ThemeConfig` | [MISSING] | — |

## 6.3 Execution gaps

| Item | Status | Deviation |
|---|---|---|
| executor.sh asset + `$({K})` placeholder substitution | [DONE] | Byte-identical asset; same substitution scheme |
| Env: EXECUTOR_PATH / START_DIR / TEMP_DIR(+TMPDIR) / ANDROID_SDK / SDCARD_PATH / PACKAGE_* | [DONE] | — |
| Env: TOOLKIT / BUSYBOX | [PARTIAL] | Always `""`; toolkit logic in executor.sh is dead |
| Env: MAGISK_PATH | [MISSING] | Always `""`; no MagiskExtend |
| Env: ANDROID_UID / APP_USER_ID | [PARTIAL] | User-serial and `u0_aNNN` replaced by raw app uid — breaks scripts keying on `0` and documented `chown $APP_USER_ID` |
| Env: ROOT_PERMISSION | [PARTIAL] | Hardcoded `"true"` — lies on unrooted devices |
| Env: PAGE_CONFIG_DIR / PAGE_CONFIG_FILE / PAGE_WORK_DIR / PAGE_WORK_FILE | [MISSING] | Never set anywhere |
| User param passing (`state`, custom params) | [PARTIAL+BUG] | Env `export` → textual `setState.replace("$state", …)` / `script.replace("$key", value)` in `KrScriptApp.kt:982/1003/1252`. The params-dialog variant replaces the **literal string `$key`** for every param — so named params are effectively never injected. No quoting/escaping (injection-unsafe) |
| Per-action fresh su/sh process + streaming (ShellExecutor/SimpleShellWatcher/ShellHandlerBase/EVENT_*) | [MISSING] | One blocking persistent shell; stderr merged into stdout; no per-line callback; exit code never surfaced |
| Script md5 cache + asset extraction | [PARTIAL] | Kept, but no CR→LF normalization |
| `progress:[n/m]` protocol + progress UI | [MISSING] | No parsing; progress lines would be Toasted as text |
| `@string:`/`@dimen:` output translation (ShellTranslation) | [MISSING] | — |
| `shell="bg-task"` notification task + stop broadcast | [MISSING] | `shell` attr parsed, never consumed |
| `shell="hidden"` + `hiddenTaskRunning` singleton guard | [MISSING] | — |
| Log dialog UI (streaming, error coloring, hasError gating autoOff, copy/hide/stop) | [MISSING] | Result Toast only |
| Interrupt (forceStop / pgrep session tag / destroy) | [MISSING] | `interruptable` unused; `KeepShellRunner.execute` is `@Synchronized` with no timeout — one hung script wedges ALL later shell calls |
| confirm/warning pre-run dialogs; autoOff/autoFinish/reloadPage/updateBlocks post-run | [MISSING] | Fields exist on `core` RunnableNode; zero consumers in `app` |
| onExit/onError semantics (exit-code branch, stderr→hasError) | [MISSING] | Single `onResult(String)`; no error signal, no code |
| before_start.sh at splash (streamed, never-blocking failure) | [MISSING] | — |
| AutoRunTask trigger flow (`autoRunItemId` → `triggerActionByKey`) | [MISSING] | Interface copied verbatim, zero consumers |
| KeepShell sync API (markers, `"error"` sentinel, lock-timeout recovery, checkRoot) | [PARTIAL] | `KeepShellRunner` has markers + blocking; missing checkRoot/timeout/sentinel (returns `""` on failure) |
| KeepShellPublic multi-instance / KeepShellAsync / AsynSuShellUnit / ShellEvents / setExtraEnvPath | [MISSING] | Only `ShellRunner.execute` exists |
| Threading discipline | [PARTIAL-BAD] | `runShellAsync` uses Dispatchers.IO for actions, BUT `KrScriptApp` calls `reader.readConfigXml(...)` directly in composition → every parse-time script (`visible`/`desc-sh`/`get-state`) runs blocking on the **main thread**; the original loaded pages on background threads with progress dialogs |
| Root preference | [PARTIAL-BAD] | `RootShellRunner` prefers non-root `sh -c`, retrying `su -c` only on failure; the original preferred `su` whenever rooted — get-state scripts may silently return unprivileged data |

## 6.4 UI interaction gaps

| Item | Status | Deviation |
|---|---|---|
| Param type: text | [PARTIAL] | Plain TextField; no `placeholder`, no `readonly`, no `maxlength`, no title/label/desc layout parity |
| Param type: int/number | [PARTIAL] | Falls through to TextField; no numeric filter, no min/max validation, no `min ~ max` hint |
| Param type: bool | [MISSING] | `type=="bool"` not matched (only `switch`/`boolean`/`checkbox`) → renders a TextField |
| Param type: checkbox/switch | [PARTIAL] | CheckboxPreference for all three; initial checked `value=="1"||value=="true"` (case-sensitive vs original lowercase compare); `value-sh` never executed |
| Param type: seekbar | [PARTIAL] | SliderPreference honors min/max (defaults 0..100 when unset); exported as `toInt()`; no ±1 buttons, no live value text; `value-sh` never executed |
| Param type: single-select | [PARTIAL] | OverlayDropdownPreference; **options-sh never executed** (static only); no >5-dialog variant; no readonly |
| Param type: multi-select | [PARTIAL+BUG] | Multi branch is **unreachable whenever options exist** — the `options != null` dropdown branch matches first (`KrScriptApp.kt:1152` vs `:1197`); real multi-select params degrade to single dropdown |
| Param type: color | [PARTIAL] | Miuix ColorPicker only (no hex text input/validity); output `#AARRGGBB` — format matches original |
| Param type: file | [PARTIAL] | TextField + in-app selector button; always editable (no `editable` gate); no suffix/mime filtering |
| Param type: folder | [MISSING] | Falls to TextField |
| Param type: app/packages | [MISSING] | No app chooser at all |
| Param validation on confirm (required / maxlen / min-max / color) | [MISSING] | No validation |
| value-sh / options-sh execution timing (on click, per param, before render) | [MISSING] | Fields parsed, never executed |
| Page menu (static options → TopAppBar) | [PARTIAL→broken] | Menu titles render, but onClick runs `option.setState` which the parser never sets for menu options → clicks are no-ops. No `type` dispatch (refresh/exit/file), no FAB, no `{state,menu_id,file,folder}` params, no `pageHandlerSh` |
| Page menu `options-sh` | [MISSING] | Never executed (incidentally matches the original's bug outcome — empty menu — but without the precedence code) |
| Icon vs logo rendering | [MISSING] | No icon/logo rendering anywhere; no `IconPathAnalysis` port; logo→icon shortcut fallback absent |
| Shortcut protocol | [MISSING] (incompatible) | `ShortcutHelper` pins a plain MainActivity intent with unread extra `"shortcut_title"`; no page serialization / `shortcutId` / `autoRunItemId` / `ObjectStorage`; no `"addin_"+index` id; no NO_HISTORY/EXCLUDE_FROM_RECENTS; no long-press flow; no update-existing |
| File selector contract | [MISSING] | In-Compose browser from `/storage/emulated/0`; no permission flow (111/2), no `extension`/`mode` extras, no folder mode, no confirm, no SAF `ACTION_GET_CONTENT` fallback, no request codes 65300/65400, no FilePathResolver |
| TryOpenActivity | [MISSING] | Rewrite uses `Class.forName(node.activity)` only — no action-string form, no `package/class` form, no NEW_TASK, no `am ` prefix, no root `am start-activity` fallback |
| WebView JS bridge (`KrScriptCore`) | [MISSING] | `OnlinePageScreen` is a bare WebView (`javaScriptEnabled=true` only): no `rootCheck`/`executeShell`/`executeShellAsync`(2/4/-2 protocol)/`extractAssets`/`fileChooser`; no `credible` file-access flags; no DownloadListener; no js alert/confirm dialogs; no non-http scheme interception; no back-history nav |
| Row rendering | [PARTIAL] | Miuix preferences with `summary.ifEmpty{desc}` merge (original shows both); no icons; no run/list/picker/arrow glyphs; groups flattened under SmallTitle headers |
| Switch behavior | [PARTIAL] | Parse-time get-state DONE in reader; UI never re-polls after `set` (optimistic local toggle); no confirm/warning; no lock check |
| Picker behavior | [PARTIAL] | Reader executes `get` at parse into `node.value` but UI ignores it (`selectedIndex` hardcoded 0); options-sh never executed; no multiple/separator; no confirm/warning |
| Confirm/warning dialogs | [MISSING] | Attrs parsed, never used |
| Lock/SDK gating (`nodeUnlocked`) | [MISSING] | Parsed, never enforced |
| desc-sh/summary-sh refresh + `reload`/`updateBlocks` | [MISSING] | Executed once at parse; no post-execution refresh |
| Text node rows | [PARTIAL] | Renders text/size/bold/italic/underline/color; **bgColor, align, breakRow not honored; link/activity/run click spans not implemented** (rows not clickable); node `desc`/`summary` of the text node itself unused |
| Page navigation & load pipeline | [PARTIAL] | Page stack works for `config` XML; no `config-sh`, no before/after/load-success/load-fail hooks; `link` handled only for http(s) (original passes any Uri); `activity` deviates (above); root page hardcoded |
| Log dialog | [MISSING] | Single Toast; no streaming, copy/hide/stop, progress, autoOff/autoFinish/reloadPage, bg/hidden modes |

## 6.5 PIO app-level gaps

| Item | Status | Deviation |
|---|---|---|
| Application class `.PIO` | [DONE] | Original was empty; rewrite omits it — behaviorally equivalent |
| Splash init flow | [MISSING] | Cosmetic 1 s `delay(1000)`; no root check, no conf parse, no before_start.sh + log, no eager environment init, no JumpActionPage |
| Root check & user-facing states | [MISSING] | No CheckRootStatus equivalent (no retry/exit/skip dialog, no 15 s watchdog, no `force_root`, no `lastCheckResult`); `suAvailable()` silently probes `su -c true` |
| Tab structure | [PARTIAL] | 4 unconditional Miuix nav items （关于 is new) vs 3 conditional tabs with gating; no config-driven visibility |
| Page/favorites list loading | [PARTIAL] | Only hardcoded `sample.xml`; no conf keys, no PageConfigSh; favorites tab isn't a kr-script page |
| Update check | [DONE] | Absent in the original too (grep-verified); nothing to port |
| MainActivity menu actions | [PARTIAL] | Power via FAB/quick action; float monitor via start/stop buttons; about exists but `transparent_ui` toggle gone |
| Intent handling (shortcut/open-page extras) | [MISSING] | MainActivity never reads the intent; no `"page"`/`"shortcutId"`/`"autoRunItemId"`/`"JumpActionPage"`; pinned shortcuts just open app home (broken deep link) |
| ActionPage (page lifecycle/contract) | [MISSING] as activity / [PARTIAL] fragments | Sub-page stack exists but: `config-sh`-only pages unreachable; no before/after/load hooks; no progress states; no reloadPage/autoFinish; no pageHandlerSh/menus; no log dialog; no auto-run; no TryOpenActivity; no `setResult(2)`/`setExcludeFromRecents` |
| ActionPageOnline | [PARTIAL] | Bare WebView; missing JS bridge, js dialogs, progress, title adoption, scheme interception, web file chooser, back nav, and the entire download pipeline (`downloadUrl`/`taskId`/`autoClose`, Downloader, 200/500 ms polling, `"absPath"` result) |
| DialogPower commands | [PARTIAL] | 4 of 6 actions with different commands (`reboot`, `reboot recovery`, `reboot bootloader`, `reboot -p`); missing 热重启 (`sync;am restart || busybox killall system_server;`) and 9008/EDL (`sync;reboot edl;`); all lack the `sync;` prefix and `svc power … || …` fallbacks |
| Home: RAM card | [PARTIAL] | Static one-shot total + linear bar; no donut, no `"x% (yGB)"`, no clear-RAM command |
| Home: SWAP card | [MISSING] | No swap readout (`free -m`), no clear-swap |
| Home: GPU card | [MISSING] | No GpuUtils port |
| Home: CPU load + per-core grid | [MISSING] | No `/proc/stat` load parsing; per-core freqs are static one-shot rows; no min/max, no grid/charts |
| Home: refresh cadence & gating | [MISSING] | No 1 s timer on home (only MonitorScreen loops cpu0-freq+battery); no root/`allow_home_page` gating. (Rewrite adds device-info/storage/battery/uptime rows — extra, not parity) |
| Float monitor | [PARTIAL] | Service-based overlay with permission check and 1 s loop preserved. Deviations: text-only (CPU freq + battery %); no donuts, no GPU, no battery temperature/color thresholds, no `dumpsys battery`; uses cpu0 `scaling_cur_freq` (original: max of all-core `cpuinfo_cur_freq`); not draggable, no double-tap-to-hide (`FLAG_NOT_TOUCHABLE`); adds FGS notification but manifest lacks `foregroundServiceType` + `POST_NOTIFICATIONS` (targetSdk 37 → `startForeground` will throw on Android 14+) |
| OpenPageHelper | [PARTIAL] | Inlined in NodeItem click; missing `pageConfigSh`, NEW_TASK flags, extra contract |
| Theme modes | [PARTIAL] | 6-mode Miuix ColorSchemeMode persisted (manual selection is new). Missing: wallpaper-transparent mode, per-activity switchTheme, dark-mode propagation to renderers; static XML `windowLightStatusBar=true` even in dark mode |
| Manifest contract | [PARTIAL] | Permission set identical. Missing: `.PIO`, `usesCleartextTraffic`, `max_aspect`, the 4 activities with filters/portrait/configChanges. Added: FGS without type; no POST_NOTIFICATIONS |
| Constants / request codes / SP / extras | [MISSING] | No Constants port; paths hardcoded, and `scaling_cur_freq` is read where the original contract maps that name to `cpuinfo_cur_freq`; none of codes `65400/65300/111/2/0x11`, SP `"theme"`/`"kr-script-config"`, or the original extras exist |
| TabIconHelper semantics | [PARTIAL] | Miuix NavigationBarItem replaces it; no drawable tab icons, no alpha-highlight rule, no dynamic add/remove |
| Chart views (donuts/bars/grid) | [MISSING] | None ported; the only chart is the new `CpuChart` bar canvas in MonitorScreen |
| ActivityFileSelector | [PARTIAL] | In-app FileSelectorScreen (browse + tap select); missing modes, extension filter, result contract, SAF fallback, permission gate, `excludeFromRecents` |
| Favorites semantics | [PARTIAL] | Original: favorites tab = executable page from `favorite_config(_sh)`; "add to favorites" = pinned ActionPage shortcut. Rewrite: local SP bookmark strings with no click-to-run (only remove/pin; pin targets a dumb MainActivity intent) |
| Runtime permission requests | [MISSING] | No `requestPermissions` anywhere in app (original: 111/2/0x11 flows) |

## 6.6 Totals and biggest gaps

Counted across 6.1–6.5 (contract items only, rewrite-only extras excluded): **[DONE] ≈ 9** (parser attrs, models, executor.sh substitution, core env subset, PIO class, update-check absence, and a few mechanical pieces) · **[PARTIAL] ≈ 45** · **[MISSING] ≈ 45**.

Biggest gaps, in the order the rewrite should close them (maps to `docs/REWRITE_PLAN_V2.md` steps 3–4):

1. **Execution core is absent** — no streaming process executor, no log dialog, no stderr separation, no exit codes, no interrupt, no bg/hidden modes, no `progress:[n/m]`. Everything user-visible about running scripts currently collapses to a Toast of merged output. (§3.3–3.5, §6.3)
2. **Param passing is broken, not just divergent** — `script.replace("$key", value)` replaces a literal `$key`; env-var export (`export name='value'`) must replace it. Also `value-sh`/`options-sh` are never executed, and multi-select params render as single-select. (§3.2, §4.1, §6.3/§6.4)
3. **`kr-script.conf` is entirely unwired** — the shipped asset is never parsed; page list is hardcoded to `sample.xml`; `before_start_sh`, tab gating, toolkit extraction, and executor/toolkit overrides are all missing. (§2, §6.2)
4. **Page lifecycle is missing** — `config-sh` (PageConfigSh), `before-load`/`after-load`/`load-ok`/`load-fail` hooks; sub-page opening only handles static `config`. (§1.9/§1.12, §5.5)
5. **Env-var contract regressions** — `PAGE_CONFIG_DIR/FILE`, `PAGE_WORK_DIR/FILE` never set; `ANDROID_UID`/`APP_USER_ID` semantics wrong; `ROOT_PERMISSION` hardcoded `true`; `TOOLKIT`/`BUSYBOX`/`MAGISK_PATH` empty. (§3.2)
6. **Root handling inverted** — rewrite prefers non-root `sh` and retries `su` only on failure; no `CheckRootStatus`, no `force_root`, no root-denied UX; a hung script wedges the `@Synchronized` runner forever. (§3.5/§3.7, §5.3)
7. **Page menus are inert** — clicks run a field that is never set; no type dispatch, no FAB, no handler params; `options-sh` unexecuted. (§4.2)
8. **KrScriptCore JS bridge missing** — all 5 methods, the `credible` security flags, download listener, and the ActionPageOnline download pipeline. (§4.7, §5.6)
9. **Shortcut/auto-run protocol incompatible** — deep links dead (no `"page"`/`"shortcutId"`/`"autoRunItemId"` handling; pinned shortcuts open app home); long-press flow absent. (§4.4, §3.6)
10. **File/app pickers below contract** — no folder mode, no suffix/mime filtering, no SAF fallback, no app chooser, no permission flows, no result-extra contract. (§4.5)
11. **Parse-time scripts run on the main thread** — `readConfigXml` is called directly in composition; every `visible`/`desc-sh`/`get-state` blocks the UI. (§6.3)
12. **Node interaction gates missing** — confirm/warning dialogs, lock/lock-shell, SDK-range gating, post-run desc-sh refresh, reload/updateBlocks, auto-finish/auto-off. (§4.8)
13. **Home dashboard is static** — no 1 s refresh, no swap/GPU/CPU-load, no per-core grid, no clear-RAM/swap commands; float monitor is a text-only, non-draggable overlay with manifest-level FGS bugs (`foregroundServiceType`, `POST_NOTIFICATIONS`). (§5.7/§5.8)
14. **Power menu fidelity** — missing hot-reboot and EDL actions; commands lack `sync;` and `svc power` fallbacks. (§5.9)
15. **Manifest/permission surface** — missing activities (Splash/ActionPage/ActionPageOnline/ActivityFileSelector), `usesCleartextTraffic`, `max_aspect`, runtime permission requests (111/2/0x11). (§5.1)

*End of contract.*
