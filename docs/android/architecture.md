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

The Compose root applies `EasyMacrosTheme`, whose default is a dark Material 3 color
scheme with high-contrast green and amber actions. `AuthenticatedApp` owns the persistent
top app bar, logout action, five primary bottom-navigation destinations, and the Today
screen's ration logging action. Today, History, Recipes, Goals, and Settings therefore
retain the same navigation model; back navigation is reserved for subordinate flows such
as ration logging, recipe detail, and recipe editing.

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

The base manifest declares `INTERNET` for Retrofit requests. The authentication password
field uses `KeyboardType.Password`, which signals the input method to suppress normal
text suggestions and offer password-oriented actions instead.

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

The recipe editor can also create a manual ingredient without discarding the recipe
draft. `IngredientEditorState` retains the name, optional brand, and protein,
carbohydrate, and fat values per 100 g through recomposition and configuration changes.
After a local review step, `RecipeRepository` creates a manual nutrient candidate and
confirms its returned version through the protected API. The confirmed user-owned
ingredient is then added to the active recipe component list. Android does not bypass
the backend's candidate confirmation or calculate ingredient nutrients.

Recipes also links to a dedicated `IngredientsScreen` subflow for bulk library setup.
`IngredientsViewModel` owns its search, list, loading, error, and reviewed editor state;
after each successful confirmation it reloads the catalog so another ingredient can be
entered immediately. The Recipes bottom-navigation destination remains selected while
this subordinate screen is open, avoiding a cramped sixth primary destination. Logging
consumption still uses immutable recipe/ration snapshots; an ingredient catalog record
is not itself a logged ration.

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

The tracking and recipe workspaces use shared screen introductions, bounded
section cards, full-width primary actions, and mutually exclusive loading, content,
empty, and error surfaces. Empty database results guide the user toward the next valid
action and are not presented as failures. Locale-aware date pickers and formatters render
dates and instants using the device locale; ViewModels and repositories continue to own
ISO-8601 strings at API boundaries. Drafts, filters, selected dates, and dialogs remain
in their Hilt ViewModels, so recomposition and configuration changes redraw rather than
reset active flows.