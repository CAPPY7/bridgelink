// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "BridgeLink",
    platforms: [.macOS(.v13)],
    products: [
        .executable(name: "BridgeLink", targets: ["BridgeLink"])
    ],
    targets: [
        .target(name: "BridgeLinkCore"),
        .executableTarget(
            name: "BridgeLink",
            dependencies: ["BridgeLinkCore"]
        ),
        .testTarget(
            name: "BridgeLinkTests",
            dependencies: ["BridgeLinkCore"],
            resources: [.copy("Fixtures")]
        ),
    ]
)
