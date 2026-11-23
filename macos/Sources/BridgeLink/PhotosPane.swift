import AppKit
import SwiftUI

struct PhotosPane: View {
    @EnvironmentObject private var model: LinkModel

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Recent photos from the phone, newest first. Thirty at a time. Nothing is listed while phone photos are off.")
                .font(.callout).foregroundStyle(.secondary)
            if !model.photosOn {
                Text("Turn on phone photos in Settings.")
                Button("Turn phone photos on") { model.setPhotos(true) }
            }
            Button("Refresh") { model.askPhotos(0) }
            Button("Save selected to Downloads/BridgeLink") { model.saveSelectedPhotos() }
            if model.phonePhotos.isEmpty {
                Text("No photos yet.").foregroundStyle(.secondary)
            }
            ForEach(model.phonePhotos) { photo in
                HStack {
                    Toggle("", isOn: Binding(
                        get: { model.photoPicked.contains(photo.id) },
                        set: { on in
                            if on { model.photoPicked.insert(photo.id) } else { model.photoPicked.remove(photo.id) }
                        }
                    ))
                    .labelsHidden()
                    if let data = photo.thumb, let image = image(data) {
                        Image(nsImage: image).resizable().frame(width: 48, height: 48)
                    }
                    Text(photo.name).lineLimit(1)
                }
            }
            if model.photoMore {
                Button("Load more") { model.askPhotos(model.phonePhotos.count) }
            }
        }
        .onAppear { if model.photosOn { model.askPhotos(0) } }
    }

    private func image(_ data: Data) -> NSImage? {
        NSImage(data: data)
    }
}
