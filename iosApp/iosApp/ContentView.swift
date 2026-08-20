import SwiftUI
import UIKit
import ComposeApp

/// Bridges the Kotlin-side MainViewController into SwiftUI.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        // Compose draws its own background and handles safe areas via
        // WindowInsets, so it takes the full screen.
        ComposeView().ignoresSafeArea(.all)
    }
}
