import AppKit
import BridgeLinkCore
import CoreImage
import SwiftUI

func pairingText(_ model: LinkModel) -> String {
    let host = model.chosenAddress
    let usable = host.isEmpty || host == "No Wi-Fi address yet" ? "" : host
    return pairingLink(
        host: usable,
        port: Int(model.listenPort),
        fingerprint: model.fingerprint,
        pin: model.pin,
        hosts: model.addressChoices
    )
}

func qrCode(_ text: String) -> NSImage? {
    guard text.contains("host="), !text.contains("host=&"),
          let filter = CIFilter(name: "CIQRCodeGenerator") else { return nil }
    filter.setValue(Data(text.utf8), forKey: "inputMessage")
    filter.setValue("M", forKey: "inputCorrectionLevel")
    guard let output = filter.outputImage else { return nil }
    let scaled = output.transformed(by: CGAffineTransform(scaleX: 6, y: 6))
    let rep = NSCIImageRep(ciImage: scaled)
    let image = NSImage(size: rep.size)
    image.addRepresentation(rep)
    return image
}
