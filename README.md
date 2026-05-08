# SaltStack Toolkit (JetBrains)

JetBrains IDE plugin for SaltStack: syntax highlighting, linting, formatting, snippets, completion and hover docs for `.sls` and Jinja2 files.

Compatible with IntelliJ IDEA, PyCharm, GoLand, WebStorm, RubyMine and other IntelliJ-based IDEs (2024.1+).

## Features

- **Syntax highlighting** for SLS and Jinja2 (`.jinja`, `.j2`)
- **Inspections**: duplicate state IDs / pillar keys, unclosed Jinja blocks, tab characters, trailing whitespace
- **Auto-completion**: state modules, requisites, Salt execution modules
- **Hover documentation** for state modules, execution modules, requisites, builtins
- **Live templates** (snippets): `file.managed`, `pkg.installed`, `service.running`, `if`, `for`, `set`, `from`, `map.jinja`, etc.
- **Format-on-save** with Jinja tag normalization (`{% if %}` → `{%- if %}`)
- **Settings panel** under `Settings → Tools → SaltStack Toolkit`

## Build

Requires **JDK 17** and **Gradle 8.x**.

If you use SDKMAN: `sdk install gradle 8.7`
Otherwise install gradle 8.x via Homebrew or the official distribution.

First time setup — generate Gradle wrapper:

```bash
gradle wrapper --gradle-version 8.7
```

Then build:

```bash
./gradlew buildPlugin
```

The `.zip` distribution will be in `build/distributions/`.

## Run in development

```bash
./gradlew runIde
```

This launches a sandbox IDE with the plugin loaded.

## Install

In your IDE: `Settings → Plugins → ⚙ → Install Plugin from Disk...` → select the `.zip` from `build/distributions/`.

## License

MIT
