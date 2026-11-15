import Foundation

/// JSON is for the UI thread. Picture bytes are for the decoder thread.
public enum PictureLane {
    public static func route(_ items: [Incoming]) -> (json: [Data], pictures: [Data]) {
        var json: [Data] = []
        var pictures: [Data] = []
        for item in items {
            if let body = item.json { json.append(body) }
            if let body = item.binary { pictures.append(body) }
        }
        return (json, pictures)
    }
}
