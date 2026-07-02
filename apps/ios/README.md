# iOS app

The iOS client is a thin SwiftUI wrapper that presents the shared Compose UI
(ADR-0001). The Kotlin code lives in `:ui-compose` (see `MainViewController.kt`),
which builds an `UiCompose.framework` consumed by Xcode.

## Verified in slice 1

`./gradlew :ui-compose:linkDebugFrameworkIosSimulatorArm64` builds the framework,
and `MainViewController` + these Swift files compile against it.

## Remaining manual step (one-time)

Create an Xcode project in this directory that:

1. Adds `iOSApp.swift` and `ContentView.swift` to an iOS App target.
2. Adds a "Run Script" build phase before compilation that invokes
   `./gradlew :ui-compose:embedAndSignAppleFrameworkForXcode`, and adds
   `$(SRCROOT)/../../ui-compose/build/xcode-frameworks/...` to the framework
   search paths — the standard Compose Multiplatform iOS wiring.
3. Links `UiCompose.framework`.

This is deferred because a hand-authored `.xcodeproj` is fragile; the framework
and entry point are proven to build via Gradle above.
