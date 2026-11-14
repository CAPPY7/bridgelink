import Crypto
import Foundation
import Security
import SwiftASN1
import X509

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
        let signingKey = P256.Signing.PrivateKey()
        let privateKey = Certificate.PrivateKey(signingKey)
        let name = try DistinguishedName { CommonName("BridgeLink") }
        let now = Date()
        let extensions = try Certificate.Extensions {
            Critical(BasicConstraints.notCertificateAuthority)
            KeyUsage(digitalSignature: true)
        }
        let certificate = try Certificate(
            version: .v3,
            serialNumber: Certificate.SerialNumber(),
            publicKey: privateKey.publicKey,
            notValidBefore: now.addingTimeInterval(-60),
            notValidAfter: now.addingTimeInterval(60 * 60 * 24 * 365 * 5),
            issuer: name,
            subject: name,
            signatureAlgorithm: .ecdsaWithSHA256,
            extensions: extensions,
            issuerPrivateKey: privateKey
        )
        let der = try DER.Serializer.serialized(element: certificate)
        let body = Data(der).base64EncodedString(options: [.lineLength64Characters, .endLineWithLineFeed])
        let certPem = "-----BEGIN CERTIFICATE-----\n\(body)\n-----END CERTIFICATE-----\n"
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("bridgelink-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: dir) }
        let certURL = dir.appendingPathComponent("cert.pem")
        let keyURL = dir.appendingPathComponent("key.pem")
        let p12URL = dir.appendingPathComponent("id.p12")
        try Data(certPem.utf8).write(to: certURL)
        try Data(signingKey.pemRepresentation.utf8).write(to: keyURL, options: .atomic)
        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/usr/bin/openssl")
        process.arguments = [
            "pkcs12", "-export",
            "-inkey", keyURL.path,
            "-in", certURL.path,
            "-out", p12URL.path,
            "-passout", "pass:\(password)",
        ]
        try process.run()
        process.waitUntilExit()
        guard process.terminationStatus == 0 else {
            throw IdentityError.message("Could not build the TLS identity (openssl \(process.terminationStatus))")
        }
        let p12 = try Data(contentsOf: p12URL)
        let imported = try importP12(p12)
        return (p12, imported.identity, imported.fingerprint)
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
