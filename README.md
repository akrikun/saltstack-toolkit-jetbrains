# SaltStack Toolkit (JetBrains)

JetBrains IDE plugin for SaltStack: syntax highlighting, inspections,
formatting, snippets, completion, and pillar-aware navigation for `.sls`
and Jinja2 files.

## Compatibility matrix

The plugin uses only stable IntelliJ Platform APIs and ships with
`since-build = 241` (IDEA 2024.1) and `until-build = 299.*`. It works
in any IntelliJ-Platform IDE in that range, including:

| IDE                      | Code | Verified  |
|--------------------------|:----:|:---------:|
| IntelliJ IDEA Community   | IC   | ✅ |
| IntelliJ IDEA Ultimate    | IU   | ✅ |
| PyCharm Community         | PC   | ✅ |
| PyCharm Professional      | PY   | ✅ |
| WebStorm                  | WS   | ✅ |
| GoLand                    | GO   | ✅ |
| RubyMine                  | RM   | ✅ |
| RustRover                 | RR   | ✅ |
| CLion                     | CL   | ✅ |

CI runs `gradle verifyPlugin` against the recommended set on every push.

## Features

- **Syntax highlighting** for SLS and Jinja2 (`.jinja`, `.j2`).
- **Inspections**:
  - duplicate top-level keys (state IDs in state files, pillar keys in pillar files)
  - unclosed Jinja blocks (correctly handles assignment vs block `{% set %}`)
  - tab characters / trailing whitespace
  - empty state blocks
  - requisite refs that don't resolve to a local state ID
- **Auto-completion** in both SLS and Jinja files: state modules,
  parameters, requisites, execution modules, dot-notation suggestions
  for `pillar.`, `grains.`, `sdb.`, `defaults.`.
- **Pillar-aware navigation**:
  - Cmd+Click on a pillar key resolves to every reference in state files,
    including indirect access via `{% set X = pillar.Y %}` aliases and
    `{% from "X" import Y %}` dict maps.
  - Hover shows the full key path, all access forms, and ready-to-run
    `salt '<minion>' state.apply <formula>` commands grouped by formula.
- **Cross-file go-to-definition** for Jinja imports/includes,
  `salt://` sources, SLS includes, pillar includes, requisite refs.
- **Live templates** (40+): state modules, Jinja constructs, Salt patterns
  like `map.jinja`, `sdb.get`, `defaults.merge`.
- **Settings panel**: `Settings → Tools → SaltStack Toolkit`.
  Configure state/pillar roots, toggle individual lint checks, control
  format-on-save behavior.

### Note on the formatter
The `enforceDashTags` option (`{% if %}` → `{%- if %}`) is **off by
default** because it changes Jinja runtime whitespace semantics. Turn
it on in the settings panel if your team relies on the dash style.

## Build

Requires **JDK 17** and **Gradle 8.x** (or 9.x).

```bash
sdk install gradle 9.5     # SDKMAN
# or:  brew install gradle

gradle wrapper --gradle-version 8.7   # first time only
./gradlew buildPlugin
```

The `.zip` distribution will be in `build/distributions/`.

## Run in development

```bash
./gradlew runIde
```

Launches a sandbox IDE with the plugin loaded.

## Install

In your IDE: `Settings → Plugins → ⚙ → Install Plugin from Disk...` →
select the `.zip` from `build/distributions/`.

## Tests

```bash
./gradlew test
```

JUnit 5 unit tests cover formatter, set-form detection, salt://
extraction, and requisite checks.

## License

MIT
