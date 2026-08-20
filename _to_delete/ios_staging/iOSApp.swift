import SwiftUI

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                // The Compose view draws its own background and handles safe
                // areas via WindowInsets, so let it own the full screen.
                .ignoresSafeArea(.all)
        }
    }
}
