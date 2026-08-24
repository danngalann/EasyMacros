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

The debug source set has its own manifest overlay with `usesCleartextTraffic="true"` for
the trusted-LAN development API. Manifest overlays are selected by build variant, so this
does not relax the release network-security policy; a distributed build still requires
HTTPS. The merged debug manifest is a useful troubleshooting check when Logcat reports
that cleartext communication is not permitted.

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
inclusion, server-preview display, and duplicate-save prevention. Run it with:

```sh
cd android
./gradlew testDebugUnitTest --tests 'com.danngalann.easymacros.ui.RecipesViewModelTest'
```

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