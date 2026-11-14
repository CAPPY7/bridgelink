// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "BridgeLink",
    platforms: [.macOS(.v13)],
    products: [
        .executable(name: "BridgeLink", targets: ["BridgeLink"])
    ],
    dependencies: [
        .package(url: "https://github.com/apple/swift-certificates.git", from: "1.6.0"),
        .package(url: "https://github.com/apple/swift-crypto.git", from: "3.8.0"),
        .package(url: "https://github.com/apple/swift-asn1.git", from: "1.3.0"),
    ],
    targets: [
        .target(name: "BridgeLinkCore"),
        .executableTarget(
            name: "BridgeLink",
            dependencies: [
                "BridgeLinkCore",
                .product(name: "X509", package: "swift-certificates"),
                .product(name: "Crypto", package: "swift-crypto"),
                .product(name: "SwiftASN1", package: "swift-asn1"),
            ]
        ),
        .testTarget(
            name: "BridgeLinkTests",
            dependencies: ["BridgeLinkCore"],
            resources: [.copy("Fixtures")]
        ),
    ]
)
