import Foundation

/// Every connection that is still pairing stays on the list.
public struct PendingSet {
    private var ids: Set<String> = []

    public init() {}

    public mutating func add(_ id: String) {
        ids.insert(id)
    }

    public mutating func remove(_ id: String) -> Bool {
        ids.remove(id) != nil
    }

    public func contains(_ id: String) -> Bool {
        ids.contains(id)
    }

    public var count: Int { ids.count }
}
