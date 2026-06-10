# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

FeatureDemo is an Android app written in Kotlin using Jetpack Compose. It currently consists of the
default Android Studio "Empty Activity" template (single `MainActivity`, Compose theme files), so
there is no established feature architecture yet — new features should follow standard
Android/Compose conventions.

- Package: `jp.developer.bbee.featuredemo`
- Min SDK 24, target/compile SDK 36
- Kotlin 2.2.10, AGP 9.1.1, Compose BOM 2026.02.01
- Dependency versions are managed centrally in `gradle/libs.versions.toml` (version catalog) —
  add new dependencies there, then reference via `libs.*` in `app/build.gradle.kts`.

## Common commands

Run all commands from the project root using the Gradle wrapper.

```sh
# Build debug APK
./gradlew assembleDebug

# Run unit tests (app/src/test)
./gradlew test

# Run a single unit test class
./gradlew test --tests "jp.developer.bbee.featuredemo.ExampleUnitTest"

# Run instrumented tests (app/src/androidTest, requires connected device/emulator)
./gradlew connectedAndroidTest

# Lint
./gradlew lint

# Install debug build on a connected device/emulator
./gradlew installDebug
```

## Code structure

- `app/src/main/java/jp/developer/bbee/featuredemo/` — app source
  - `MainActivity.kt` — single entry-point activity, sets Compose content via `setContent`
  - `ui/theme/` — Compose theme (`Color.kt`, `Theme.kt`, `Type.kt`)
- `app/src/test/` — local JUnit unit tests
- `app/src/androidTest/` — instrumented tests (Espresso/Compose UI test)