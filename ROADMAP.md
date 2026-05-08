# Roadmap

Mirror of the VS Code plugin's roadmap, JetBrains side. See
`code-salt-extension/ROADMAP.md` for the cross-cutting items.

Legend: ✅ done · 🚧 in progress · ⏳ planned · 💤 deferred (with reason)

## 1. Plugin Verifier in CI
- ✅ `gradle verifyPlugin` (recommended IDE matrix) wired into a GitHub
  Actions job (`.github/workflows/ci.yml`). Runs in parallel with the test
  job; advisory output uploaded as an artifact.

## 2. Tests
- ✅ JUnit 5 unit suite covers formatter, set-form detection, salt://
  extraction, and requisite checks (43 tests).
- ⏳ Light integration tests for `SaltLexer`/`SaltParserDefinition` and
  `SaltCompletionContributor` are scaffolded in next iteration.

## 3. Shared fixtures
- ✅ Top-level `fixtures/` directory mirrors the VS Code repo. Both test
  suites consume the same `.sls`/`.tst` corpora; a regression on either
  side breaks both CIs.

## 4. Safe formatter
- ✅ Default for `enforceDashTags` flipped to `false`. The dash-vs-no-dash
  choice changes Jinja runtime whitespace semantics, so we no longer
  enforce that on save by default. Existing users who want it can opt in
  in `Settings → Tools → SaltStack Toolkit`.

## 5. Marketplace-ready
- ✅ Long-form description in `plugin.xml` with feature list.
- ✅ Per-version `<change-notes>` consumed by JetBrains Marketplace.
- ✅ README listed compatible IDEs (IDEA, PyCharm, GoLand, WebStorm,
  RubyMine, RustRover, CLion).
- ⏳ Real screenshots once features stabilize.

## 6. Compatibility matrix
- ✅ `pluginUntilBuild = 299.*` covers the foreseeable future without
  per-release re-publishing. Plugin Verifier exercises the recommended
  set.
- All IntelliJ-Platform IDEs (`IC`, `PY`, `GO`, `WS`, `PS`, `RM`, `RR`)
  share the same plugin SDK; this plugin works in all of them.

## Cross-cutting

The pure helpers (`isAssignmentSet`, `findUnknownRequisiteRefs`,
`extractSaltUri`, `normalizeJinjaTags/Expressions`, `getPillarKeyPath`)
are deliberately written as pure functions (companion objects in Kotlin,
exported funcs in TS) so the same fixtures exercise the same logic on
both sides.
