import BridgeLinkCore
import CoreBluetooth
import Foundation

/// The Mac tells nearby phones it is here. The phone decides when the signal is weak.
final class LeaveBeacon: NSObject, CBPeripheralManagerDelegate {
    private var manager: CBPeripheralManager?
    private var started = false

    func start() {
        started = true
        if manager == nil {
            manager = CBPeripheralManager(delegate: self, queue: nil)
        } else {
            addService()
        }
    }

    func stop() {
        started = false
        manager?.stopAdvertising()
        manager?.removeAllServices()
    }

    func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        addService()
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, didAdd service: CBService, error: Error?) {
        guard error == nil, started else { return }
        peripheral.startAdvertising([
            CBAdvertisementDataServiceUUIDsKey: [service.uuid],
            CBAdvertisementDataLocalNameKey: "BridgeLink",
        ])
    }

    private func addService() {
        guard started, let manager, manager.state == .poweredOn else { return }
        if manager.isAdvertising { return }
        let service = CBMutableService(type: CBUUID(string: StageFour.bleUUID), primary: true)
        manager.add(service)
    }
}
