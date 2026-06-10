# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

FeatureDemo is an Android app written in Kotlin using Jetpack Compose, with Navigation 3 for
screen navigation and Hilt for dependency injection.

- Package: `jp.developer.bbee.featuredemo`
- Min SDK 24, target/compile SDK 36
- Kotlin 2.2.10, AGP 9.1.1 (built-in Kotlin — no `org.jetbrains.kotlin.android` plugin), Compose BOM 2026.02.01
- Navigation 3 (`androidx.navigation3`), Hilt (KSP), kotlinx-serialization
- Dependency versions are managed centrally in `gradle/libs.versions.toml` (version catalog) —
  add new dependencies there, then reference via `libs.*` in `app/build.gradle.kts`.

## Navigation architecture (Navigation 3 + Hilt)

- Routes are `@Serializable` classes/objects implementing `NavKey`, defined in
  `navigation/AppRoute.kt`. Serializability is required by `rememberNavBackStack`.
- `MainActivity` (annotated `@AndroidEntryPoint`) hosts a single `NavDisplay`. The back stack is
  created with `rememberNavBackStack(HomeRoute)`; navigation is performed by adding/removing
  route keys on the back stack directly.
- **ViewModel scoping**: `NavDisplay` is configured with
  `entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator())`.
  `rememberViewModelStoreNavEntryDecorator()` makes each `NavEntry` act as its own
  `ViewModelStoreOwner`, so `hiltViewModel()` instances live exactly as long as their back stack
  entry and are cleared when the entry is popped. Keep this decorator (plus the saveable-state
  one, which must come first) whenever modifying `NavDisplay`.
- `hiltViewModel()` comes from `androidx.hilt:hilt-lifecycle-viewmodel-compose`
  (import `androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel`) — not the Nav2-based
  `hilt-navigation-compose` artifact.
- To pass a route's arguments into a ViewModel, use Hilt assisted injection
  (`@HiltViewModel(assistedFactory = ...)` + `hiltViewModel(creationCallback = ...)`);
  see `ui/detail/DetailScreen.kt` for the pattern. No extra `key` is needed —
  the ViewModelStore decorator already keys ViewModels by `NavEntry.contentKey`.
- Screen-level composables live under `ui/<feature>/` alongside their ViewModel.
- `FeatureDemoApplication` is the `@HiltAndroidApp` entry point (registered in the manifest).

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