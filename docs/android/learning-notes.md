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