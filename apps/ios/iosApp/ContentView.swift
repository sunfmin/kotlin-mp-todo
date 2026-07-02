import SwiftUI
import UiCompose

/// Bridges the shared Compose UI (ADR-0001) into SwiftUI. The `UiCompose` framework
/// is produced by the `:ui-compose` Gradle module; `MainViewController` builds the
/// full client stack (Darwin HTTP engine + shared ViewModel) and renders `App`.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(baseUrl: "http://localhost:8080")
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all)
    }
}
