# iOS app shell

The Compose UI is built by Gradle into a static framework named `ComposeApp`
and consumed here. Xcode owns the app target; Gradle owns everything inside it.

## One-time Xcode setup

1. Xcode → File → New → Project → iOS → App.
   * Product Name: `iosApp`
   * Interface: SwiftUI, Language: Swift
   * Save into this `iosApp/` directory, replacing the generated
     `iOSApp.swift` and `ContentView.swift` with the ones already here.
2. Select the `iosApp` target → Build Phases → `+` → New Run Script Phase.
   Move it **above** "Compile Sources" and paste:

   ```sh
   cd "$SRCROOT/.."
   ./gradlew :composeApp:embedAndSignAppleFrameworkForXcode
   ```

3. Target → Build Settings:
   * `Framework Search Paths` →
     `$(SRCROOT)/../composeApp/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)`
   * `Other Linker Flags` → `$(inherited) -framework ComposeApp`
   * `User Script Sandboxing` → **No** (the script phase runs Gradle)

4. Build and run on a simulator.

## Notes

`MainViewControllerKt.MainViewController()` is the Kotlin entry point in
`composeApp/src/iosMain/.../MainViewController.kt`. Koin starts there, because
iOS has no `Application` class to start it from.
