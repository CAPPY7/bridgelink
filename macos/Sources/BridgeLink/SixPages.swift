import AppKit
import BridgeLinkCore
import SwiftUI

@MainActor
final class SixHold: ObservableObject {
    @Published var mirror: NSImage?
    @Published var camera: NSImage?
    @Published var mirrorOn = false
    @Published var camOn = false
    @Published var quality = "medium"
    @Published var facing = "back"
    @Published var zoom = 1
    @Published var flash = false
    @Published var mic = false
    @Published var relayUrl = UserDefaults.standard.string(forKey: "relayUrl") ?? ""
    @Published var mirrorStatus = "Waiting for the phone to allow the screen."
    @Published var camStatus = "The phone camera shows here. Share this window in a call if you need it there."
    @Published var picW = 1280
    @Published var picH = 720
    @Published var typeText = ""
    let video = H264Decoder()
    let audio = PcmPlay()
    var mirrorParts: [Data] = []
    var camParts: [Data] = []
    var sps: Data?
    var pps: Data?

    init() {
        video.onImage = { [weak self] image in
            Task { @MainActor in self?.mirror = image }
        }
    }
}

struct MirrorPane: View {
    @EnvironmentObject private var model: LinkModel

    var body: some View {
        MirrorBody(six: model.six)
    }
}

private struct MirrorBody: View {
    @ObservedObject var six: SixHold
    @EnvironmentObject private var model: LinkModel

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(six.mirrorStatus).foregroundStyle(.secondary)
            Text(StageSix.mirrorSteps).font(.callout).foregroundStyle(.secondary)
            HStack {
                Button("Low") { six.quality = "low" }
                Button("Medium") { six.quality = "medium" }
                Button("High") { six.quality = "high" }
                Text(six.quality)
            }
            HStack {
                Button("Start mirror") { model.startMirror() }
                Button("Stop") { model.stopMirror() }
            }
            picture
            HStack {
                Button("Back") { model.sendControl("back", 0, 0, 0, 0, "") }
                Button("Home") { model.sendControl("home", 0, 0, 0, 0, "") }
                Button("Recents") { model.sendControl("recents", 0, 0, 0, 0, "") }
                Button("Scroll") { model.sendControl("scroll", 5000, 7000, 5000, 3000, "") }
            }
            TextField("Type on the phone", text: $six.typeText)
            Button("Type") { model.sendControl("text", 0, 0, 0, 0, six.typeText) }
            Text(StageSix.controlSteps).font(.callout).foregroundStyle(.secondary)
        }
        .onDisappear { model.stopMirror() }
    }

    private var picture: some View {
        GeometryReader { geo in
            ZStack {
                RoundedRectangle(cornerRadius: 12).fill(Color.black.opacity(0.9))
                if let image = six.mirror {
                    Image(nsImage: image).resizable().scaledToFit()
                } else {
                    Text("Phone screen").foregroundStyle(.white)
                }
            }
            .gesture(DragGesture(minimumDistance: 0).onEnded { value in
                let viewW = Int(geo.size.width)
                let viewH = Int(geo.size.height)
                guard let start = StageSix.picturePoint(
                    viewW: viewW, viewH: viewH, picW: six.picW, picH: six.picH,
                    px: Int(value.startLocation.x), py: Int(value.startLocation.y)
                ), let end = StageSix.picturePoint(
                    viewW: viewW, viewH: viewH, picW: six.picW, picH: six.picH,
                    px: Int(value.location.x), py: Int(value.location.y)
                ) else { return }
                let moved = abs(start.0 - end.0) + abs(start.1 - end.1) > 80
                model.sendControl(moved ? "drag" : "tap", start.0, start.1, end.0, end.1, "")
            })
        }
        .frame(height: 360)
    }
}

struct WebcamPane: View {
    @EnvironmentObject private var model: LinkModel

    var body: some View {
        WebcamBody(six: model.six)
    }
}

private struct WebcamBody: View {
    @ObservedObject var six: SixHold
    @EnvironmentObject private var model: LinkModel

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(six.camStatus).foregroundStyle(.secondary)
            Text(StageSix.camNote).font(.callout)
            HStack {
                Button(six.facing == "front" ? "Front camera" : "Back camera") {
                    six.facing = six.facing == "front" ? "back" : "front"
                }
                Button("Zoom \(six.zoom)") { six.zoom = six.zoom >= 8 ? 1 : six.zoom + 1 }
                Button(six.flash ? "Flash on" : "Flash off") { six.flash.toggle() }
                Button(six.mic ? "Mic on" : "Mic off") { six.mic.toggle() }
            }
            HStack {
                Button("Start camera") { model.startCamera() }
                Button("Stop") { model.stopCamera() }
            }
            ZStack {
                RoundedRectangle(cornerRadius: 12).fill(Color.black.opacity(0.9))
                if let image = six.camera {
                    Image(nsImage: image).resizable().scaledToFit()
                } else {
                    Text("Camera").foregroundStyle(.white)
                }
            }
            .frame(height: 360)
        }
        .onDisappear { model.stopCamera() }
    }
}

struct RelayPane: View {
    @EnvironmentObject private var model: LinkModel

    var body: some View {
        RelayBody(six: model.six)
    }
}

private struct RelayBody: View {
    @ObservedObject var six: SixHold
    @EnvironmentObject private var model: LinkModel

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Away from home").font(.headline)
            Text(StageSix.relaySteps).font(.callout).foregroundStyle(.secondary)
            TextField("wss:// relay address", text: $six.relayUrl)
            HStack {
                Button("Save relay") { model.saveRelay() }
                Button("Use away relay") { model.tryRelay() }
            }
        }
    }
}
