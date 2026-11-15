import Foundation

/// Caps how many file chunks are waiting on the network. A 7th chunk waits.
public final class SendWindow {
    public static let maxInFlight = 6
    private let slots = DispatchSemaphore(value: SendWindow.maxInFlight)
    private let lock = NSLock()
    private var held = 0

    public init() {}

    public func acquire() {
        slots.wait()
        lock.lock()
        held += 1
        lock.unlock()
    }

    public func tryAcquire() -> Bool {
        if slots.wait(timeout: .now()) == .timedOut { return false }
        lock.lock()
        held += 1
        lock.unlock()
        return true
    }

    public func release() {
        lock.lock()
        if held > 0 { held -= 1 }
        lock.unlock()
        slots.signal()
    }

    public func inFlight() -> Int {
        lock.lock()
        defer { lock.unlock() }
        return held
    }
}
