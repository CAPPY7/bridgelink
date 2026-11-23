import Foundation
import Security

/// Ignore old relay_* messages. They are never sent.
public enum OldMessages {
    public static let savedKeys = ["relayUrl", "relaySecret"]
    private static let service = "app.bridgelink.mac.relay"

    public static func ignoreSender(_ from: String) -> Bool {
        from == "relay"
    }

    public static func ignore(_ type: String) -> Bool {
        type == "relay_key" || type == "relay_set" || type == "relay_use"
            || type == "relay_auth" || type == "relay_ok"
    }

    /// Drops a saved away secret left by an older build. Ignore old relay_* messages.
    public static func dropSaved() {
        for key in savedKeys {
            UserDefaults.standard.removeObject(forKey: key)
        }
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
        ]
        SecItemDelete(query as CFDictionary)
    }
}
