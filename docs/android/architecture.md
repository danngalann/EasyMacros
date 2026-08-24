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
client so the refresh request cannot recurse through the authenticator. A rejected
refresh clears the encrypted session and emits a process-local invalidation event;
`AuthViewModel` collects that event and immediately replaces the authenticated graph
with login. This also handles development database resets while the app process remains
open.

The API base URL is a `BuildConfig` value. The main manifest permits cleartext HTTP in
debug and release builds for deployments whose API is reachable exclusively through an
encrypted VPN. Android cannot enforce that routing boundary, so operators must bind and
firewall the API to the VPN network or use HTTPS. In debug builds, `EasyMacrosNetwork`
logs an `IOException` with only the request method, host, and path so connectivity errors
are diagnosable in Logcat. Network bodies, query values, headers, and bearer tokens are
never logged.

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

The recipe editor can open the shared `IngredientsScreen` without discarding its draft.
`IngredientsViewModel` owns ingredient search, manual/barcode/AI creation, loading,
errors, and reviewed candidate state. After confirmation, the user-owned ingredient is
added to the active recipe component list and the editor resumes. Android does not
bypass backend candidate confirmation or calculate ingredient nutrients. The Recipes
bottom-navigation destination remains selected while this subordinate screen is open,
avoiding a cramped sixth primary destination. Logging consumption still uses immutable
recipe/ration snapshots; an ingredient catalog record is not itself a logged ration.

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

The Ingredients destination is now a complete management workspace: searchable visible
or hidden lists, details, editing, visibility changes, and a creation-method chooser.
`IngredientApi` is the only Android ingestion boundary. `DefaultIngredientRepository`
maps wire candidates into domain models; `IngredientsViewModel` owns immutable list,
scanner, image, processing, review, and detail states.

CameraX binds preview and analysis to the lifecycle owner. ML Kit accepts UPC-A/EAN-13,
while a checksum gate and duplicate-frame suppression prevent avoidable requests. Camera
permission denial leaves manual entry available. Activity Result contracts provide
camera capture and the system photo picker; images are resized and compressed before
multipart upload.

The candidate ID is stored in `SavedStateHandle`, so process recreation resumes polling.
Polling and uploads run in `viewModelScope`, cancellation calls backend cleanup, and
recomposition performs no network work. Manual, OFF, and AI candidates render through
one review state with explicit confirmation. Barcode review always exposes image+AI
fallback.

Recipe-origin ingredient creation navigates to that same screen rather than opening a
manual-only dialog. The activity-scoped Hilt `RecipesViewModel` retains the recipe draft;
the confirmed ingredient ID/name is returned as an event, added to the draft, and
navigation returns to the editor. Standalone confirmation returns to the ingredient list
with success feedback that is cleared after three seconds or when the screen leaves
composition. Back from the recipe-origin creation chooser returns directly to the
preserved recipe rather than exposing the ingredient catalog first.

The image draft is an immutable list of up to four compressed images. Camera capture
appends one image and the system multi-photo picker appends the remaining slots, allowing
front/back/nutrition-panel context in one multipart request. Candidate review labels
required and optional fields and associates nutrient-specific warnings with their fields.
The app shell observes `TrackingUiState`, so the Log ration FAB is absent while the log
screen is already active. Compose `BackHandler`s map Android's system-back gesture to
subordinate screen state first (ingredient creation, recipe editor/detail, and ration
logging), then to the Today destination; the activity exits only from the top-level Today
dashboard.