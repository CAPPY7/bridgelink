import CryptoKit
import Foundation
import Security

enum IdentityError: Error, CustomStringConvertible {
    case message(String)
    var description: String { switch self { case .message(let text): return text } }
}

/// Long-lived self-signed TLS identity. The PKCS#12 blob lives in the login
/// keychain so the certificate fingerprint stays the same across launches.
enum MacIdentity {
    private static let service = "app.bridgelink.mac.identity"
    private static let account = "p12"
    private static let password = "bridgelink-local"

    static func load() throws -> (identity: SecIdentity, fingerprint: String) {
        if let stored = readKeychain(), let imported = try? importP12(stored) {
            return imported
        }
        let created = try makeP12()
        try writeKeychain(created.p12)
        return (created.identity, created.fingerprint)
    }

    private static func makeP12() throws -> (p12: Data, identity: SecIdentity, fingerprint: String) {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("bridgelink-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: dir) }
        let keyURL = dir.appendingPathComponent("key.pem")
        let certURL = dir.appendingPathComponent("cert.pem")
        let p12URL = dir.appendingPathComponent("id.p12")
        try runOpenSSL([
            "ecparam", "-name", "prime256v1", "-genkey", "-noout", "-out", keyURL.path,
        ])
        try runOpenSSL([
            "req", "-new", "-x509", "-key", keyURL.path, "-out", certURL.path,
            "-days", "3650", "-subj", "/CN=BridgeLink",
        ])
        try runOpenSSL([
            "pkcs12", "-export",
            "-inkey", keyURL.path,
            "-in", certURL.path,
            "-out", p12URL.path,
            "-passout", "pass:\(password)",
        ])
        let p12 = try Data(contentsOf: p12URL)
        let imported = try importP12(p12)
        return (p12, imported.identity, imported.fingerprint)
    }

    private static func runOpenSSL(_ arguments: [String]) throws {
        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/usr/bin/openssl")
        process.arguments = arguments
        try process.run()
        process.waitUntilExit()
        guard process.terminationStatus == 0 else {
            throw IdentityError.message("openssl \(arguments.first ?? "") failed (\(process.terminationStatus))")
        }
    }

    private static func importP12(_ data: Data) throws -> (identity: SecIdentity, fingerprint: String) {
        let options = [kSecImportExportPassphrase as String: password] as CFDictionary
        var items: CFArray?
        let status = SecPKCS12Import(data as CFData, options, &items)
        guard status == errSecSuccess,
              let rows = items as? [[String: Any]],
              let identity = rows.first?[kSecImportItemIdentity as String] else {
            throw IdentityError.message("Could not import the TLS identity (\(status))")
        }
        let secIdentity = identity as! SecIdentity
        var certificate: SecCertificate?
        SecIdentityCopyCertificate(secIdentity, &certificate)
        guard let certificate else { throw IdentityError.message("Identity has no certificate") }
        let encoded = SecCertificateCopyData(certificate) as Data
        let fingerprint = SHA256.hash(data: encoded).map { String(format: "%02x", $0) }.joined()
        return (secIdentity, fingerprint)
    }

    private static func readKeychain() -> Data? {
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

    private static func writeKeychain(_ data: Data) throws {
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
