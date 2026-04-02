# Lua Auto Require — IntelliJ / Rider Plugin

A standalone IntelliJ Platform plugin (works with **Rider**, **IntelliJ IDEA**, and any other IntelliJ-based IDE) that provides automatic `require` statement insertion for Lua files.

Extracted from [EmmyLua](https://github.com/zhangchenglei2/IntelliJ-EmmyLua) (commits `c9b4f27`, `c9c8e02`, `82c229b`, `ae21247`).

---

## Features

| Feature | Description |
|---|---|
| **Module index** | Scans configured source-root directories for `local X = require("...")` patterns and builds a project-level index |
| **Smart completion** | When you type ≥ 2 chars that match an indexed variable name, the completion popup shows the matching modules with their require path on the right |
| **Auto-insert** | Selecting a completion item automatically prepends `local <Name> = require("<path>")` at the top of the current file |
| **Duplicate guard** | If the same `local` declaration already exists, no duplicate is inserted |
| **Incremental index** | VFS events (create / change / delete) keep the index up-to-date without a full rebuild |
| **Per-project paths** | Each project can configure its own set of scan directories via the settings panel |

---

## Requirements

- IntelliJ Platform IDE **2023.3 – 2025.1** (build `233` – `251.*`)
- **EmmyLua** plugin (or equivalent Lua plugin) installed — this plugin registers the `Lua` language and its file type

---

## Usage

### 1. Configure scan paths

Open **Settings → EmmyLua → Auto Require 路径** and add the root directories that contain your project's Lua files.

> Tip: If you have configured source roots in **Project Structure**, those are also scanned automatically.

### 2. Wait for index build

On project open (or after saving settings), a background task *"EmmyLua: Building require module index…"* runs and fills the index.

### 3. Use in a Lua file

Type 2+ characters of a module variable name that appears in the index.  
The completion popup will show entries tagged **(auto require)** with the full require path.

```lua
-- Type "UIConf" and pick the completion:
UIConfig  (auto require)    Game.Mod.Config.UIConfig
-- After selecting, the following line is inserted at the top of the file:
local UIConfig = require("Game.Mod.Config.UIConfig")
```

---

## Building

```bash
./gradlew buildPlugin
```

The resulting `.zip` file will be in `build/distributions/`. Install it via **Settings → Plugins → ⚙ → Install Plugin from Disk…**.

---

## Project structure

```
lua-auto-require-plugin/
├── build.gradle.kts
├── settings.gradle.kts
├── README.md
└── src/main/
    ├── kotlin/com/tang/intellij/lua/autorequire/
    │   ├── completion/
    │   │   ├── RequireModuleInfo.kt                 # Data class: varName + requirePath + sourceFile
    │   │   ├── RequireModuleIndex.kt                # Project service: builds & maintains the index
    │   │   ├── RequireModuleIndexStartupActivity.kt # Kicks off warm-up on project open
    │   │   └── AutoRequireCompletionContributor.kt  # CompletionContributor + InsertHandler
    │   └── project/
    │       ├── AutoRequireSourceRootManager.kt      # Persists configured scan paths
    │       └── AutoRequireSettingsPanel.kt          # Settings UI panel
    └── resources/META-INF/
        └── plugin.xml
```

---

## Configuration file

The configured scan paths are stored in `.idea/lua-auto-require.xml` (project-scoped).  
Add it to `.gitignore` if the paths are machine-specific.

---

## License

Apache 2.0 — same as the original EmmyLua project.
