# Client UI architecture: shared Compose UI on native platforms, Compose HTML on Web

We build the Android, iOS, and Desktop clients with a **single shared Compose Multiplatform UI** (the Compose UI widget toolkit), and build the **Web client separately with Compose HTML** (DOM-based `@Composable`s). All four clients share everything below the view layer — domain, logic, ViewModels, and the Compose runtime/state model — but the Web UI does **not** reuse the shared widget tree.

## Why

We are shipping a production product on all four UI platforms (see the "production-grade" project goal), where fidelity matters. Compose Multiplatform is stable on Android/iOS/Desktop and lets a single team own three clients from one UI codebase, with native escape hatches (SwiftUI/UIKit interop) available per-screen when iOS fidelity demands it. On Web we deliberately reject Compose/Wasm (canvas rendering): it is still Beta, ships a large canvas bundle, and gives up real DOM, text selection, and accessibility — costs a production web app should not pay. Compose HTML keeps us in the `@Composable`/state programming model and reuses all shared ViewModels and logic, at the cost of a second view-layer codebase for Web only.

## Consequences

- The shared UI module must not assume the web view layer; web-specific view code lives in the web client module only.
- Any screen written once in shared Compose UI must be written a second time in Compose HTML for Web. Keep view-layer logic thin and in shared ViewModels so the duplicated part is minimal.
- iOS may drop to native SwiftUI/UIKit via interop for specific screens; that is an expected escape hatch, not a deviation.
