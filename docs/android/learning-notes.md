# Android Learning Notes

> Living document: update this file in the same change whenever the described
> behavior, contract, command, configuration, or architecture changes.

## Phase 1: Activity and Compose root

`MainActivity` extends `ComponentActivity`, Android's lifecycle-aware base class for a
Compose screen. Its `onCreate` method calls `setContent`, which installs a composable
UI tree. A composable describes UI from state; when state changes later, Compose may
recompose that portion of the tree rather than imperatively editing views.

`MaterialTheme` and `Surface` wrap the current placeholder content. They do not retain
application state. The activity owns the initial UI lifecycle, while future ViewModels
will retain screen state across configuration changes and expose immutable state to
composables.

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

DataStore provides asynchronous persistence but not encryption. The session store
encrypts its plaintext with an AES-GCM key held by Android Keystore before writing it to
DataStore. This permits process-death restoration without putting bearer or refresh
tokens in ordinary preferences. The ViewModel begins in `isRestoring`, then chooses the
auth or main shell once storage and any required refresh finish.

`AuthViewModelTest` proves three key transitions: persisted sessions open the main
shell, successful authentication switches shells, and a failure remains visible to the
form. Run `./gradlew test` from `android/` for JVM coverage and
`./gradlew lint test assembleDebug` for the Android phase gate.