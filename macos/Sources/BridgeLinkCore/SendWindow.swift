import Foundation

/// Caps how many file chunks are waiting on the network. A 7th chunk waits.
/// A non-blocking check must not call the semaphore. That call traps the Mac tests.
public final class SendWindow {
    public static let maxInFlight = 6
    private let lock = NSLock()
    private var held = 0
    private let wake = DispatchSemaphore(value: 0)

    public init() {}

    public func acquire() {
        lock.lock()
        while held >= SendWindow.maxInFlight {
            lock.unlock()
            wake.wait()
            lock.lock()
        }
        held += 1
        lock.unlock()
    }

    public func tryAcquire() -> Bool {
        lock.lock()
        defer { lock.unlock() }
        if held >= SendWindow.maxInFlight { return false }
        held += 1
        return true
    }

    public func release() {
        lock.lock()
        if held > 0 { held -= 1 }
        lock.unlock()
        wake.signal()
    }

    public func inFlight() -> Int {
        lock.lock()
        defer { lock.unlock() }
        return held
    }
}
