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

Phase 6 establishes a feature-oriented Android foundation. Hilt owns application-wide
dependencies. `data/api` contains Retrofit wire DTOs and endpoints, while
`data/repository` maps API outcomes to app behavior and owns authentication session
replacement. `ui/AuthViewModel` exposes immutable `StateFlow<AuthUiState>` to the
Compose root, which selects the authentication or authenticated shell.

The session store encrypts one serialized session with an AES-GCM key generated in the
Android Keystore, then persists only ciphertext in Preferences DataStore. On process
restart, the ViewModel asks the repository to restore that session; an expired access
token is refreshed before the main shell is shown. Refresh requests are protected by a
coroutine `Mutex`, so concurrent callers do not rotate the opaque server token twice.
An OkHttp interceptor adds the access token to protected calls. Its authenticator performs
at most one refresh-and-retry after a `401`, using a separate unauthenticated Retrofit
client so the refresh request cannot recurse through the authenticator.

The API base URL is a `BuildConfig` value. Debug builds use the Android emulator host
address `http://10.0.2.2:3000/`; release builds must provide a TLS endpoint before
distribution. Network bodies and bearer tokens are never logged.

Phase 7 replaces the authenticated placeholder with a daily dashboard. `DashboardApi`
calls the protected `GET /dashboard` endpoint through the same bearer-token interceptor
and refresh authenticator. `DashboardRepository` maps wire DTOs into display models;
the `DashboardViewModel` owns the selected `LocalDate` and its immutable loading,
content, and error state. The app displays server-provided nutrient totals and calories
without independently recalculating them.

Phase 8 adds a recipe workspace reachable from the authenticated dashboard. `RecipeApi`
and `IngredientApi` use the protected client, while `RecipeRepository` maps recipe and
ingredient wire responses into UI models. `RecipesViewModel` owns immutable list, detail,
editor, preview, and delete-confirmation state. The editor retains its draft across
recomposition and configuration changes, validates positive decimal inputs before a
request, prevents duplicate submissions, and asks `POST /recipes/preview` for display
totals. Creating or updating then receives the persisted immutable version from the
server; Android never calculates recipe nutrients itself.