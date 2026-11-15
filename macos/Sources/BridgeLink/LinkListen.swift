import AppKit
import BridgeLinkCore
import CoreImage
import Darwin
import Foundation
import Network
import Security
import ServiceManagement
import SwiftUI
import UniformTypeIdentifiers
import UserNotifications


extension LinkModel {
    func start() {
        listenAttempt = 0
        openListener()
    }

    func openListener() {
        listenerGen += 1
        let gen = listenerGen
        listener?.cancel()
        let portNumber = UInt16(8787 + listenAttempt)
        guard let port = NWEndpoint.Port(rawValue: portNumber) else { return }
        do {
            let loaded = try MacIdentity.load()
            fingerprint = loaded.fingerprint
            let tls = NWProtocolTLS.Options()
            guard let secIdentity = sec_identity_create(loaded.identity) else {
                phase = .failed("Could not use the TLS identity")
                return
            }
            sec_protocol_options_set_local_identity(tls.securityProtocolOptions, secIdentity)
            let params = NWParameters(tls: tls)
            params.allowLocalEndpointReuse = true
            let listener = try NWListener(using: params, on: port)
            listener.service = NWListener.Service(name: "BridgeLink", type: "_bridgelink._tcp")
            listener.newConnectionHandler = { [weak self] connection in
                Task { @MainActor in self?.accept(connection) }
            }
            listener.stateUpdateHandler = { [weak self] state in
                Task { @MainActor in
                    guard let self, self.listenerGen == gen else { return }
                    switch state {
                    case .ready:
                        self.listenPort = listener.port?.rawValue ?? portNumber
                        if case .paired = self.phase {} else { self.phase = .listening }
                    case .failed(let error):
                        self.listenAttempt += 1
                        if self.listenAttempt < 8 {
                            self.openListener()
                        } else {
                            let kind: LinkKind
                            if case .posix(let code) = error {
                                kind = LinkKind.fromErrno(code.rawValue)
                            } else {
                                kind = .firewall
                            }
                            let steps = LinkKind.firewall.message
                            self.phase = .failed(kind == .firewall ? steps : kind.message + " " + steps)
                        }
                    default:
                        break
                    }
                }
            }
            listener.start(queue: queue)
            self.listener = listener
        } catch {
            phase = .failed("Could not start the listener.")
        }
    }

    func watchNetwork() {
        let monitor = NWPathMonitor()
        pathMonitor = monitor
        monitor.pathUpdateHandler = { [weak self] path in
            Task { @MainActor in
                guard let self else { return }
                self.refreshAddresses()
                if path.status == .satisfied, self.listener == nil {
                    self.start()
                }
            }
        }
        monitor.start(queue: queue)
    }

    func refreshAddresses() {
        let list = Self.lanAddresses()
        addressChoices = list
        addresses = list.isEmpty ? "No Wi-Fi address yet" : list.joined(separator: "\n")
        if chosenAddress.isEmpty || !list.contains(chosenAddress) {
            chosenAddress = list.first ?? ""
        }
    }

    func armPinClock() {
        let clock = DispatchSource.makeTimerSource(queue: queue)
        clock.schedule(deadline: .now() + 1, repeating: 1)
        clock.setEventHandler { [weak self] in
            Task { @MainActor in
                guard let self else { return }
                if self.pinLeft <= 1 {
                    self.newPin()
                } else {
                    self.pinLeft -= 1
                }
            }
        }
        pinClock = clock
        clock.resume()
    }

    func armSilenceClock() {
        let clock = DispatchSource.makeTimerSource(queue: queue)
        clock.schedule(deadline: .now() + 15, repeating: 15)
        clock.setEventHandler { [weak self] in
            Task { @MainActor in
                guard let self else { return }
                guard case .paired = self.phase else { return }
                if Date().timeIntervalSince(self.lastRx) > 45 {
                    self.live?.cancel()
                    self.live = nil
                    self.liveDeviceId = ""
                    self.phase = .listening
                    self.note("The phone went quiet. Still listening.")
                } else {
                    self.sendMacBattery()
                }
            }
        }
        silenceClock = clock
        clock.resume()
    }

    func accept(_ connection: NWConnection) {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let ip = Self.endpointHost(connection)
        if !pendingGate.admit(ip: ip, openCount: pendingCount, now: now) {
            connection.cancel()
            note("Refused an extra connection so a real phone can still pair.")
            return
        }
        let id = UUID()
        pendingCount += 1
        pendingID = id
        pending = connection
        connection.stateUpdateHandler = { [weak self] state in
            Task { @MainActor in
                guard let self, self.pendingID == id, self.liveID != id else { return }
                if case .failed = state {
                    self.finishPending(id)
                    self.note("A connection failed before it paired. The current phone stays linked.")
                }
            }
        }
        connection.start(queue: queue)
        receive(connection, id: id, buffer: FrameBuffer())
        queue.asyncAfter(deadline: .now() + 10) { [weak self] in
            Task { @MainActor in
                guard let self, self.pendingID == id, self.liveID != id else { return }
                if self.sasConnectionID == id { return }
                connection.cancel()
                self.finishPending(id)
                self.note("A connection did not pair in time.")
            }
        }
        queue.asyncAfter(deadline: .now() + 120) { [weak self] in
            Task { @MainActor in
                guard let self, self.sasConnectionID == id, self.liveID != id else { return }
                connection.cancel()
                self.clearSas()
                self.finishPending(id)
                self.note("The match code expired. Connect again.")
            }
        }
    }

    func finishPending(_ id: UUID) {
        if pendingID == id { pending = nil }
        pendingCount = max(0, pendingCount - 1)
    }

    func receive(_ connection: NWConnection, id: UUID, buffer: FrameBuffer) {
        let pump = six.pump
        var buffer = buffer
        connection.receive(minimumIncompleteLength: 1, maximumLength: 65_536) { [weak self] data, _, isComplete, error in
            guard let self else { return }
            var failed = false
            var items: [Incoming] = []
            if let data {
                do {
                    items = try buffer.pushAll(data)
                } catch {
                    failed = true
                }
            }
            let lanes = PictureLane.route(items)
            pump.submit(lanes.pictures)
            let jsons = lanes.json
            Task { @MainActor in
                guard let self else { return }
                if self.liveID != id && self.pendingID != id { return }
                let stillPending = self.liveID != id
                if failed {
                    connection.cancel()
                    self.note("Closed a connection that sent too much data.")
                    return
                }
                for message in jsons {
                    self.handle(message, connection: connection, id: id, pending: stillPending)
                }
                if isComplete || error != nil {
                    if self.pendingID == id && self.liveID != id { self.pending = nil }
                    if self.liveID == id {
                        self.live = nil
                        self.phase = .listening
                        self.note("Phone disconnected")
                        self.armLeaveLock()
                        self.tryRelay()
                    }
                    return
                }
                self.receive(connection, id: id, buffer: buffer)
            }
        }
    }

}
