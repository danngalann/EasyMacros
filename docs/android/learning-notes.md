# Android Learning Notes

> Living document: update this file in the same change whenever the described
> behavior, contract, command, configuration, or architecture changes.

## Phase 1: Activity and Compose root

`MainActivity` extends `ComponentActivity`, Android's lifecycle-aware base class for a
Compose screen. Its `onCreate` method calls `setContent`, which installs a composable
UI tree. A composable describes UI from state; when state changes later, Compose may
recompose that portion of the tree rather than imperatively editing views.

`MaterialTheme` and `Surface` wrap the current placeholder content. They do not retain
application state. `enableEdgeToEdge` lets the window use the full display, while the
root `Surface` uses `safeDrawingPadding` to keep screen content below the status bar,
camera cutout, and navigation areas; each screen then adds its ordinary content padding.
The activity owns the initial UI lifecycle, while future ViewModels will retain screen
state across configuration changes and expose immutable state to composables.

The Phase 1 JVM test verifies the generated application ID. `lint` catches Android
resource and manifest issues, while `assembleDebug` proves the app can be packaged.

## Phase 6: Authentication state and persistence

`EasyMacrosApplication` is annotated with `@HiltAndroidApp`, creating the application
component used for singleton Retrofit, repository, and storage dependencies.
`MainActivity` is `@AndroidEntryPoint`; Compose obtains the Hilt-created ViewModel with
`hiltViewModel()`. The ViewModel survives configuration changes, while its immutable
`StateFlow` is collected lifecycle-safely by the composable. Recomposition redraws from
the latest state without recreating requests.

Retrofit `suspend` endpoints run their network work without blocking the UI thread.
The repository writes successful login/register/refresh results to storage, maps HTTP
and connectivity failures to standard errors, and serializes refresh rotation with a
`Mutex`. Cancellation remains cooperative: a cancelled screen coroutine does not block
the UI, while the repository owns the durable persisted result.

All Retrofit clients use the shared OkHttp construction in `NetworkModule`. Debug builds
install `NetworkFailureLoggingInterceptor`, which records an `IOException` under the
`EasyMacrosNetwork` Logcat tag with the method, host, path, and exception stack trace.
It deliberately excludes request bodies, headers, and query values, so a request can be
traced without exposing credentials, tokens, or opaque cursors. The focused JVM test
proves the message redacts a query value.

The main manifest sets `usesCleartextTraffic="true"`, so both debug and release variants
can call an HTTP API. This Android flag permits transport; it does not verify that a VPN
is connected or encrypt traffic itself. The deployment must make the API address
unreachable outside the encrypted VPN, or use HTTPS. Inspecting each merged manifest is a
useful troubleshooting check when variant behavior differs.

`android.permission.INTERNET` is a normal manifest permission, so Android grants it when
the app is installed and does not show a runtime prompt. Without it, socket creation can
fail with `EPERM` before OkHttp reaches the network. `PasswordVisualTransformation` only
masks displayed characters; `KeyboardOptions(keyboardType = KeyboardType.Password)` sets
the platform input type, which prevents ordinary keyboard suggestions and lets password
managers recognize the field.

DataStore provides asynchronous persistence but not encryption. The session store
encrypts its plaintext with an AES-GCM key held by Android Keystore before writing it to
DataStore. This permits process-death restoration without putting bearer or refresh
tokens in ordinary preferences. The ViewModel begins in `isRestoring`, then chooses the
auth or main shell once storage and any required refresh finish.

`AuthViewModelTest` proves three key transitions: persisted sessions open the main
shell, successful authentication switches shells, and a failure remains visible to the
form. Run `./gradlew test` from `android/` for JVM coverage and
`./gradlew lint test assembleDebug` for the Android phase gate.

## Phase 7: Daily dashboard

`DashboardViewModel` is Hilt-created for the authenticated dashboard and retains the
selected `LocalDate` across activity configuration changes. It exposes immutable
`DashboardUiState` through `StateFlow`; Compose collects that flow with lifecycle
awareness, so recomposition redraws loading, content, or retry UI without launching
duplicate requests. Date changes call the repository through `viewModelScope`, which
keeps network work off the main thread and is cancelled when the ViewModel is cleared.

The dashboard API response remains a wire DTO in `data/api`; `DashboardRepository`
maps it to a domain/display model before it reaches Compose. Amounts and calories remain
strings because the backend owns decimal-safe calculation and serialization. The UI only
formats those values for display, avoiding a second authoritative calculation on Android.
`DashboardViewModelTest` verifies initial content, date navigation, and retryable
failure state. A useful debugging check is to compare the requested `date` query value
with the user's stored timezone: the backend, rather than the device timezone, defines
the dashboard's local-day totals.

## Phase 8: Recipes

`RecipesViewModel` is screen-scoped and exposes one immutable `RecipesUiState` containing
the recipe list, selected detail, draft editor, and pending delete confirmation. Each
editor change creates a new immutable component list, so Compose sees a state change and
recomposes without a mutable list escaping into the UI. The draft is source state; the
server preview is derived display state and is cleared when source values change.

Recipe and ingredient endpoints are `suspend` Retrofit calls. They flow through
`RecipeRepository`, which maps DTOs to domain/UI models and turns HTTP or connectivity
errors into a standard failure. `viewModelScope` survives recomposition and cancels
requests when its ViewModel is cleared. The Hilt ViewModel survives configuration changes,
so a rotation retains the current list, detail, and editor draft, but process death starts
from the recipe list because Phase 8 does not persist unfinished recipes.

The backend validates ownership and performs decimal calculation for `POST
/recipes/preview`, create, and update. This prevents Android display arithmetic from
becoming a second source of truth. A useful debugging technique is to compare the draft's
ingredient IDs and gram strings with the preview request; a `400` means one of those
server validations rejected the draft, while `404` during an update means the active
recipe was deleted and should be reopened from search.

`RecipesViewModelTest` proves list loading, local validation, deliberate hidden-ingredient
inclusion, server-preview display, duplicate-save prevention, operation-specific errors,
and reviewed ingredient creation. The ingredient form is another immutable nullable
state branch: opening it supplies a value, edits use `copy`, confirmation launches a
`suspend` repository workflow in `viewModelScope`, and success clears the branch while
adding the returned ingredient to the recipe. Run it with:

```sh
cd android
./gradlew testDebugUnitTest --tests 'com.danngalann.easymacros.ui.RecipesViewModelTest'
```

`IngredientsViewModel` applies the same immutable form and coroutine pattern outside a
recipe draft. It reloads the owner-scoped catalog after each confirmation, enabling a
bulk-entry workflow without changing authoritative nutrient or ration semantics.
`IngredientsViewModelTest` proves reviewed creation and post-create refresh:

```sh
cd android
./gradlew testDebugUnitTest --tests 'com.danngalann.easymacros.ui.IngredientsViewModelTest'
```

Android Studio Live Edit rewrites composable click lambdas in a running process. A crash
whose stack begins in `com.android.tools.deploy.liveedit` with
`NoSuchFieldException: receiver` indicates stale injected bytecode rather than an app or
Retrofit exception. Disable Live Edit for the session and install clean bytecode:

```sh
cd android
./gradlew clean :app:installDebug
```

For API failures, filter Logcat by `EasyMacrosNetwork`. Debug builds record sanitized
transport exceptions and non-success HTTP status codes; release builds install neither
transport logger. The absence of a backend request alongside an Android transport error
usually means the configured host is unreachable from the device.

## Phase 9: Tracking state, time, and history

`TrackingViewModel` is the authenticated tracking screen's owner. It exposes immutable
`TrackingUiState` via `StateFlow`; `TrackingScreen` collects it with lifecycle-aware
collection, so configuration changes retain selected dates, drafts, filters, detail,
and confirmation state while recomposition only redraws the current state. Network
calls run from `viewModelScope`, and the recipe lookup cancels the earlier search job
before a short debounce, preventing stale search responses from replacing newer input.

Ration times cross two domains. Android sends an ISO-8601 instant, while the server uses
the user's IANA timezone to derive a local day. The UI accepts no meal category. It
requests `POST /rations/preview` before enabling confirmation, so displayed nutrients
and calories remain server-authoritative. A history detail renders its stored snapshot;
correction deletes it and asks the user to log a replacement rather than editing that
snapshot in place.

Goals are effective-dated and use positive decimal strings. Progress is presentation
only: when consumed amount exceeds a target, the screen exposes an over-goal label and
caps the progress bar's visual fraction at one. The underlying amount is never capped.
History carries the backend's opaque cursor forward for the next page, rather than
constructing an offset in Android.

`TrackingViewModelTest` proves a selected day loads both dashboard and ration history,
and that logging uses a returned server preview. Run it with:

```sh
cd android
./gradlew testDebugUnitTest --tests 'com.danngalann.easymacros.ui.TrackingViewModelTest'
```

## Modern app shell and guided states

`AuthenticatedApp` hoists primary navigation above individual screens. A saved Compose
destination selects the visible workspace, while `TrackingViewModel` remains the owner
of tracking state and events. Selecting Today, History, Goals, or Settings sends a
navigation event to that ViewModel; Recipes has its own Hilt `RecipesViewModel`. The
prominent Today action opens the ration draft without creating a second source of truth.

Compose recomposes the selected content whenever either navigation state or a collected
`StateFlow` changes. ViewModels survive configuration changes, so dates, recipe drafts,
history filters, and submission state remain intact across rotation. They are not saved
for process death; after process recreation the encrypted authentication session is
restored, then the authenticated workspaces reload their server state.

Visible dates use localized `DateTimeFormatter` instances and a Material date picker.
The picker converts its UTC epoch-day value back to `LocalDate`, while repositories still
send ISO-8601 dates and instants. This is a presentation mapping, not a change to backend
timezone semantics. A useful debugging check is to inspect the ISO request value and the
stored user timezone when a ration appears on an unexpected day; changing the phone's
locale should only change its label.

Loading, failure, empty, and content branches are intentionally exclusive. A successful
empty response explains the next useful action, while a failed request offers retry and
does not also claim that no data exists. Recipe failures are mapped by operation, so a
list request can no longer display save-specific copy. The focused ViewModel tests prove
that distinction and that editing the timezone field does not issue a network request:

```sh
cd android
./gradlew :app:testDebugUnitTest \
	--tests 'com.danngalann.easymacros.ui.RecipesViewModelTest' \
	--tests 'com.danngalann.easymacros.ui.TrackingViewModelTest'
```

## External ingredient candidates

`IngredientCandidate` is an immutable Kotlin `data class`; nullable confidence and
provider fields represent genuinely absent values. Repository `suspend` functions map
Retrofit DTOs before Compose sees them. `IngredientsViewModel` hoists form and operation
state into `StateFlow`; composables send events back, and recomposition only redraws.

`viewModelScope` owns upload and poll coroutines. Configuration changes retain the
ViewModel; `SavedStateHandle` also retains the candidate ID across process death so a new
process reloads authoritative state. Cancellation stops client work and calls the backend
`DELETE` cleanup endpoint.

CameraX binds preview and `ImageAnalysis` to the lifecycle owner. KEEP_ONLY_LATEST
backpressure avoids stale frames, ML Kit decodes UPC-A/EAN-13, and duplicate suppression
prevents repeated submissions. Runtime permission uses an Activity Result contract;
denial leaves manual entry available. The system photo picker needs no broad storage
permission.

Hilt supplies Retrofit and the repository. Data flows DTO → repository mapper →
ViewModel `StateFlow` → composable. A useful scanner debugging check is that every image
proxy closes after ML Kit completes; a leaked proxy freezes analysis. Focused tests prove
shared manual review, duplicate/checksum handling, permission fallback, and candidate-ID
process restoration.

The shared recipe-to-ingredient flow demonstrates state ownership across navigation:
`RecipesViewModel` owns the unfinished recipe, while `IngredientsViewModel` owns capture,
provider polling, and review. Only the confirmed ingredient's small identity crosses
back; images and DTOs do not. This is preferable to duplicating candidate logic inside
the recipe editor and survives recomposition/configuration changes because both states
remain in their ViewModels.

Multiple photos use an immutable `List<IngredientImage>` updated with `+` and
`filterIndexed`; Compose detects the new list and recomposes previews. Retrofit accepts a
list of multipart `Part`s, while cancellation still propagates through the repository's
`suspend` call. Tests prove two images are submitted together, confirmation returns to
the list, the recipe draft accepts the returned ingredient, and the ration FAB visibility
follows navigation state.

Android back gestures do not automatically understand custom enum-based navigation.
`BackHandler` must delegate to the same state transition as the visible header action.
The ingredient screen owns nested creation back behavior, while the authenticated shell
handles recipe/tracking and primary-destination behavior. A transient success message's
`LaunchedEffect` timeout is cancelled when the composable leaves, so
`DisposableEffect.onDispose` also clears it and prevents stale feedback from returning.

A server-side session can disappear while the activity and `AuthViewModel` remain
alive, such as after `make dev-db-reset`. Clearing DataStore alone does not recompose the
root authentication graph. `RefreshCoordinator` therefore publishes a `SharedFlow`
invalidation event, and `AuthViewModel` collects it in `viewModelScope`. The event changes
immutable `AuthUiState`, Compose observes the `StateFlow`, and the login screen replaces
the authenticated shell without restarting the process.