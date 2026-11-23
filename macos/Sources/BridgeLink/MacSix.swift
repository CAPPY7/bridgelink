import AppKit
import BridgeLinkCore
import Foundation
import Network
import Security

extension LinkModel {
    func takePicture(_ raw: Data) {
        six.pump.submit([raw])
    }

    func startMirror() {
        let picture = StageSix.quality(six.quality)
        six.picW = picture.width
        six.picH = picture.height
        six.mirrorOn = true
        six.mirrorStatus = "Asking the phone to share its screen."
        send(type: "mirror_start", payload: ["quality": StageSix.qualityName(six.quality)], on: live)
    }

    func stopMirror() {
        guard StageSix.stopWhenClosed(false) else { return }
        six.mirrorOn = false
        six.mirrorStatus = "Mirror is off."
        send(type: "mirror_stop", payload: [:], on: live)
    }

    func startCamera() {
        six.camOn = true
        six.camStatus = "Asking the phone for the camera."
        send(type: "cam_start", payload: [
            "facing": StageSix.facing(six.facing),
            "zoom": String(StageSix.zoom(six.zoom)),
            "flash": StageSix.flashOn(six.facing, six.flash) ? "1" : "0",
            "mic": six.mic ? "1" : "0",
        ], on: live)
    }

    func stopCamera() {
        six.camOn = false
        six.camStatus = "Camera is off."
        send(type: "cam_stop", payload: [:], on: live)
    }

    func sendControl(_ kind: String, _ x: Int, _ y: Int, _ x2: Int, _ y2: Int, _ text: String) {
        guard let tap = StageSix.control(kind, x, y, x2, y2, text) else {
            note("That touch was not sent.")
            return
        }
        send(type: "control", payload: [
            "kind": tap.kind,
            "x": String(tap.x),
            "y": String(tap.y),
            "x2": String(tap.x2),
            "y2": String(tap.y2),
            "text": tap.text,
        ], on: live)
    }

    func handleSix(_ envelope: ParsedEnvelope, _ connection: NWConnection) -> Bool {
        switch envelope.type {
        case "mirror_stop":
            six.mirrorOn = false
            six.mirrorStatus = "The phone stopped sharing."
            return true
        case "cam_stop":
            six.camOn = false
            six.camStatus = "The phone stopped the camera."
            return true
        default:
            return false
        }
    }
}
