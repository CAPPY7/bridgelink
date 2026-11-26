import CryptoKit
import Foundation
import Security

public enum IdentityError: Error, CustomStringConvertible {
    case message(String)
    public var description: String { switch self { case .message(let text): return text } }
}

/// Long-lived self-signed TLS identity. Created once with openssl, then loaded
/// from the keychain by label so later launches do not import another copy.
public enum MacIdentity {
    public static var service = "app.bridgelink.mac.identity"
    public static var label = "BridgeLink TLS"
    /// Only used to open a PKCS#12 written before passwords were random.
    private static let legacyUnlock = "bridgelink-local"

    public static func load() throws -> (identity: SecIdentity, fingerprint: String) {
        if let existing = copyLabeled() {
            return (existing, fingerprint(of: existing))
        }
        if let stored = readKeychain(account: "p12") {
            let imported = try importP12(stored, password: currentPassword())
            labelIdentity(imported.identity)
            return imported
        }
        let created = try makeP12()
        try writeKeychain(created.p12, account: "p12")
        labelIdentity(created.identity)
        return (created.identity, created.fingerprint)
    }

    public static func identityCount() -> Int {
        let query: [String: Any] = [
            kSecClass as String: kSecClassIdentity,
            kSecAttrLabel as String: label,
            kSecMatchLimit as String: kSecMatchLimitAll,
            kSecReturnRef as String: true,
        ]
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        if status == errSecItemNotFound { return 0 }
        guard status == errSecSuccess else { return -1 }
        if let list = item as? [Any] { return list.count }
        return item == nil ? 0 : 1
    }

    public static func removeStored() {
        let generic: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
        ]
        SecItemDelete(generic as CFDictionary)
        let ident: [String: Any] = [
            kSecClass as String: kSecClassIdentity,
            kSecAttrLabel as String: label,
        ]
        SecItemDelete(ident as CFDictionary)
    }

    private static func currentPassword() -> String {
        if let data = readKeychain(account: "p12pass"),
           let text = String(data: data, encoding: .utf8), !text.isEmpty {
            return text
        }
        return legacyUnlock
    }

    private static func randomPassword() -> String {
        var bytes = [UInt8](repeating: 0, count: 32)
        _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        return Data(bytes).base64EncodedString()
    }

    private static func makeP12() throws -> (p12: Data, identity: SecIdentity, fingerprint: String) {
        let pass = randomPassword()
        try writeKeychain(Data(pass.utf8), account: "p12pass")
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("bridgelink-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true, attributes: [.posixPermissions: 0o700])
        defer { try? FileManager.default.removeItem(at: dir) }
        let keyURL = dir.appendingPathComponent("key.pem")
        let certURL = dir.appendingPathComponent("cert.pem")
        let p12URL = dir.appendingPathComponent("id.p12")
        try runOpenSSL([
            "ecparam", "-name", "prime256v1", "-genkey", "-noout", "-out", keyURL.path,
        ])
        try FileManager.default.setAttributes([.posixPermissions: 0o600], ofItemAtPath: keyURL.path)
        try runOpenSSL([
            "req", "-new", "-x509", "-key", keyURL.path, "-out", certURL.path,
            "-days", "3650", "-subj", "/CN=BridgeLink",
        ])
        try runOpenSSL([
            "pkcs12", "-export",
            "-inkey", keyURL.path,
            "-in", certURL.path,
            "-out", p12URL.path,
            "-passout", "env:BL_P12PASS",
        ], env: ["BL_P12PASS": pass])
        let p12 = try Data(contentsOf: p12URL)
        let imported = try importP12(p12, password: pass)
        return (p12, imported.identity, imported.fingerprint)
    }

    private static func runOpenSSL(_ arguments: [String], env: [String: String] = [:]) throws {
        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/usr/bin/openssl")
        process.arguments = arguments
        if !env.isEmpty {
            var merged = ProcessInfo.processInfo.environment
            for (key, value) in env { merged[key] = value }
            process.environment = merged
        }
        try process.run()
        process.waitUntilExit()
        guard process.terminationStatus == 0 else {
            throw IdentityError.message("openssl \(arguments.first ?? "") failed (\(process.terminationStatus))")
        }
    }

    private static func requireIdentity(_ value: Any?) throws -> SecIdentity {
        guard let value else {
            throw IdentityError.message("The TLS identity was missing.")
        }
        let ref = value as CFTypeRef
        guard CFGetTypeID(ref) == SecIdentityGetTypeID() else {
            throw IdentityError.message("The TLS identity was the wrong type.")
        }
        guard let identity = value as? SecIdentity else {
            throw IdentityError.message("The TLS identity was the wrong type.")
        }
        return identity
    }

    private static func importP12(_ data: Data, password: String) throws -> (identity: SecIdentity, fingerprint: String) {
        let options = [kSecImportExportPassphrase as String: password] as CFDictionary
        var items: CFArray?
        let status = SecPKCS12Import(data as CFData, options, &items)
        guard status == errSecSuccess,
              let rows = items as? [[String: Any]],
              let identity = rows.first?[kSecImportItemIdentity as String] else {
            throw IdentityError.message("Could not import the TLS identity (\(status))")
        }
        let secIdentity = try requireIdentity(identity)
        return (secIdentity, fingerprint(of: secIdentity))
    }

    private static func fingerprint(of identity: SecIdentity) -> String {
        var certificate: SecCertificate?
        SecIdentityCopyCertificate(identity, &certificate)
        guard let certificate else { return "" }
        let encoded = SecCertificateCopyData(certificate) as Data
        return SHA256.hash(data: encoded).map { String(format: "%02x", $0) }.joined()
    }

    private static func copyLabeled() -> SecIdentity? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassIdentity,
            kSecAttrLabel as String: label,
            kSecReturnRef as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess else { return nil }
        return try? requireIdentity(item)
    }

    private static func labelIdentity(_ identity: SecIdentity) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassIdentity,
            kSecValueRef as String: identity,
        ]
        let attrs: [String: Any] = [kSecAttrLabel as String: label]
        SecItemUpdate(query as CFDictionary, attrs as CFDictionary)
    }

    private static func readKeychain(account: String) -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess else { return nil }
        return item as? Data
    }

    private static func writeKeychain(_ data: Data, account: String) throws {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        SecItemDelete(query as CFDictionary)
        var add = query
        add[kSecValueData as String] = data
        add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        let status = SecItemAdd(add as CFDictionary, nil)
        guard status == errSecSuccess else {
            throw IdentityError.message("Could not store the identity in the keychain (\(status))")
        }
    }
}
