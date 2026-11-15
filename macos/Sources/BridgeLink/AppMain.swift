import AppKit
import BridgeLinkCore
import CoreImage
import Darwin
import Foundation
import Network
import Security
import ServiceManagement
import SwiftUI
import UniformTypeIdentifiers
import UserNotifications

@main
struct BridgeLinkApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) var delegate

    var body: some Scene {
        Settings {
            Text("Use the menu-bar icon. Drop a file on it, or press Command-Shift-D.")
                .padding()
                .frame(width: 320)
        }
    }
}
