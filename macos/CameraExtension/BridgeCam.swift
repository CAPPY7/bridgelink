import CoreMedia
import CoreMediaIO
import Foundation

/// System camera for Zoom, Meet, and FaceTime.
/// build.sh does not compile this unless APPLE_TEAM_ID is set.
/// A paid Apple Developer ID is still required before macOS will load it.
@available(macOS 13.0, *)
final class BridgeCamProvider: NSObject, CMIOExtensionProviderSource {
    private(set) var provider: CMIOExtensionProvider!

    init(clientQueue: DispatchQueue?) {
        super.init()
        provider = CMIOExtensionProvider(source: self, clientQueue: clientQueue)
    }

    func connect(to provider: CMIOExtensionProvider) throws {
        self.provider = provider
    }

    func availableProperties() -> Set<CMIOExtensionProperty> {
        []
    }

    func providerProperties(forProperties properties: Set<CMIOExtensionProperty>) throws -> CMIOExtensionProviderProperties {
        CMIOExtensionProviderProperties(dictionary: [:])
    }

    func setProviderProperties(_ providerProperties: CMIOExtensionProviderProperties) throws {
    }
}

/// The free app never calls this. A future signed build can start the extension.
enum BridgeCamGate {
    static func enabled(appleSecrets: Bool) -> Bool {
        appleSecrets
    }
}
