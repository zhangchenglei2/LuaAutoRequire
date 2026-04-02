# Lua Auto Require — IntelliJ / Rider 插件

一个独立的 IntelliJ Platform 插件，适用于 **Rider**、**IntelliJ IDEA** 及其他所有基于 IntelliJ 的 IDE。  
功能：在 Lua 文件中，根据模块名自动匹配并插入 `require` 语句。

---

## 功能特性

| 功能 | 说明 |
|---|---|
| **模块索引** | 扫描配置的源码根目录，提取 `local X = require("...")` 模式，构建项目级索引 |
| **智能补全** | 输入 ≥ 2 个字符时，若与索引中的变量名匹配，补全列表右侧会显示对应的 require 路径 |
| **自动插入** | 选中补全项后，自动在文件顶部插入 `local <Name> = require("<path>")` |
| **去重保护** | 若文件中已存在相同的 `local` 声明，不会重复插入 |
| **增量索引** | 通过监听 VFS 事件（新建/修改/删除），实时更新索引，无需全量重建 |
| **项目级路径配置** | 每个项目可在设置面板中单独配置扫描目录 |

---

## 环境要求

- IntelliJ Platform IDE **2025.1 – 2026.1**（build `251` – `261.*`）
- 目标 IDE 中已安装支持 `Lua` 语言及文件类型的 Lua 插件

---

## 使用方法

### 第一步：配置扫描路径

打开 **Settings → Lua Auto Require**，添加项目中存放 Lua 文件的根目录。

> 提示：在 **Project Structure** 中配置的 Source Root 会被自动包含，无需重复添加。

### 第二步：等待索引构建

项目打开或保存设置后，后台会自动执行「构建 require 模块索引」任务并显示进度条。

### 第三步：在 Lua 文件中使用

输入索引中某个模块变量名的前 2 个以上字符，补全列表中会出现带 **(auto require)** 标记的候选项，右侧显示完整 require 路径。

```lua
-- 输入 "UIConf" 后选择补全：
UIConfig  (auto require)    Game.Mod.Config.UIConfig
-- 选中后，文件顶部自动插入：
local UIConfig = require("Game.Mod.Config.UIConfig")
```

---

## 构建

```bash
./gradlew buildPlugin
```

构建产物 `.zip` 文件位于 `build/distributions/`，通过 **Settings → Plugins → ⚙ → Install Plugin from Disk…** 安装即可。

---

## 项目结构

```
LuaAutoRequire/
├── build.gradle.kts
├── settings.gradle.kts
├── README.md
└── src/main/
    ├── kotlin/com/tang/intellij/lua/autorequire/
    │   ├── completion/
    │   │   ├── RequireModuleInfo.kt                 # 数据类：varName + requirePath + sourceFile
    │   │   ├── RequireModuleIndex.kt                # 项目级服务：构建并维护索引
    │   │   ├── RequireModuleIndexStartupActivity.kt # 项目打开后触发预热
    │   │   └── AutoRequireCompletionContributor.kt  # 补全贡献者 + 插入处理器
    │   └── project/
    │       ├── AutoRequireSourceRootManager.kt      # 持久化扫描路径配置
    │       └── AutoRequireSettingsPanel.kt          # 设置面板 UI
    └── resources/META-INF/
        └── plugin.xml
```

---

## 配置文件

扫描路径配置存储在 `.idea/lua-auto-require.xml`（项目级，不跨项目共享）。  
若路径为本机绝对路径，建议将该文件加入 `.gitignore`。

---

## 许可证

Apache 2.0
