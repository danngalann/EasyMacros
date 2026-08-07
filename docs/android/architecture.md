# Android Architecture

> Living document: update this file in the same change whenever the described
> behavior, contract, command, configuration, or architecture changes.

`android/` is its own Gradle root. `settings.gradle.kts` includes the `:app` module,
and `gradle/libs.versions.toml` centrally defines plugin and dependency versions. This
means Android Studio can open `EasyMacros/android` directly without reading pnpm files.

The `app` module uses Kotlin, the Android application plugin, and the Kotlin Compose
compiler plugin. `MainActivity` is the single activity and calls `setContent` to create
the Compose UI tree. `MaterialTheme` provides Compose styling; the XML theme supplies
window-level activity resources.

There are no repositories, ViewModels, navigation graphs, or API DTOs yet. They will be
introduced with the Android foundation in Phase 6.