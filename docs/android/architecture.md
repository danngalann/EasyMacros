# Android Architecture

> Living document: update this file in the same change whenever the described
> behavior, contract, command, configuration, or architecture changes.

`android/` is its own Gradle root. `settings.gradle.kts` includes the `:app` module,
and `gradle/libs.versions.toml` centrally defines plugin and dependency versions. This
means Android Studio can open `EasyMacros/android` directly without reading pnpm files.

The `app` module uses Kotlin, the Android application plugin, and the Kotlin Compose
compiler plugin. `MainActivity` is the single activity and calls `setContent` to create
the Compose UI tree. It explicitly enables edge-to-edge, while the root `Surface`
applies `safeDrawingPadding`; each screen retains its local content spacing inside that
safe area. `MaterialTheme` provides Compose styling; the XML theme supplies window-level
activity resources.

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

The API base URL is a `BuildConfig` value. The debug manifest permits cleartext HTTP for
the local development API; release builds must provide a TLS endpoint before distribution.
In debug builds, `EasyMacrosNetwork` logs an `IOException` with only the request method,
host, and path so connectivity errors are diagnosable in Logcat. Network bodies, query
values, headers, and bearer tokens are never logged.

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

Phase 9 replaces the dashboard-only authenticated view with `TrackingViewModel` and a
coordinated dashboard, log-ration, goals, history, and settings workspace. The
ViewModel owns immutable `TrackingUiState`: selected date, server dashboard, ration
pages, destructive confirmation, log draft, server preview, effective-date goals, and
timezone update state. `TrackingRepository` maps protected ration, goal, and profile
DTOs to UI models. Recipe search for the log flow is debounced and cancellable; history
uses the server's opaque keyset cursor. Compose never calculates a persisted ration's
nutrients or calories, instead rendering `POST /rations/preview` and dashboard values.

Timezone changes are explicit and warn that the backend reclassifies existing ration
days while retaining their instant and nutrient snapshots. Corrections delete a ration
and require a new log, so the UI does not offer an in-place edit of historical totals.