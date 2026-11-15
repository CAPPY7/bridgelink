import Foundation
import IOKit.ps

/// The Mac battery, if this Mac has one. A desktop returns nil.
enum MacPower {
    static func sample() -> (level: Int, charging: Bool)? {
        guard let blob = IOPSCopyPowerSourcesInfo()?.takeRetainedValue() else { return nil }
        guard let list = IOPSCopyPowerSourcesList(blob)?.takeRetainedValue() as? [CFTypeRef] else { return nil }
        for item in list {
            guard let desc = IOPSGetPowerSourceDescription(blob, item)?.takeUnretainedValue() as? [String: Any] else { continue }
            guard let current = desc[kIOPSCurrentCapacityKey as String] as? Int else { continue }
            let max = desc[kIOPSMaxCapacityKey as String] as? Int ?? 100
            if max <= 0 { continue }
            let charging = desc[kIOPSIsChargingKey as String] as? Bool ?? false
            let level = max == 100 ? current : Int((Double(current) / Double(max) * 100.0).rounded())
            if level < 0 || level > 100 { continue }
            return (level, charging)
        }
        return nil
    }
}
