import AppKit
import AVFoundation
import CoreImage
import CoreMedia
import CoreVideo
import Foundation
import VideoToolbox

/// Turns phone H.264 into a picture, and plays short mic clips.
final class H264Decoder {
    var onImage: ((NSImage) -> Void)?
    private var format: CMVideoFormatDescription?
    private var session: VTDecompressionSession?

    func setParameterSets(sps: Data, pps: Data) {
        var desc: CMVideoFormatDescription?
        let status = sps.withUnsafeBytes { (sRaw: UnsafeRawBufferPointer) -> OSStatus in
            pps.withUnsafeBytes { (pRaw: UnsafeRawBufferPointer) -> OSStatus in
                guard let sPtr = sRaw.bindMemory(to: UInt8.self).baseAddress,
                      let pPtr = pRaw.bindMemory(to: UInt8.self).baseAddress else { return -1 }
                let ptrs: [UnsafePointer<UInt8>] = [sPtr, pPtr]
                let sizes = [sps.count, pps.count]
                return ptrs.withUnsafeBufferPointer { ptrBuf in
                    sizes.withUnsafeBufferPointer { sizeBuf in
                        CMVideoFormatDescriptionCreateFromH264ParameterSets(
                            allocator: kCFAllocatorDefault,
                            parameterSetCount: 2,
                            parameterSetPointers: ptrBuf.baseAddress!,
                            parameterSetSizes: sizeBuf.baseAddress!,
                            nalUnitHeaderLength: 4,
                            formatDescriptionOut: &desc
                        )
                    }
                }
            }
        }
        guard status == noErr, let desc else { return }
        format = desc
        if let session { VTDecompressionSessionInvalidate(session) }
        session = nil
        var callback = VTDecompressionOutputCallbackRecord(
            decompressionOutputCallback: { refCon, _, status, _, buffer, _, _ in
                guard status == noErr, let buffer, let refCon else { return }
                let me = Unmanaged<H264Decoder>.fromOpaque(refCon).takeUnretainedValue()
                me.emit(buffer)
            },
            decompressionOutputRefCon: Unmanaged.passUnretained(self).toOpaque()
        )
        var created: VTDecompressionSession?
        let spec: [CFString: Any] = [kCVPixelBufferPixelFormatTypeKey: kCVPixelFormatType_32BGRA]
        let made = VTDecompressionSessionCreate(
            allocator: kCFAllocatorDefault,
            formatDescription: desc,
            decoderSpecification: nil,
            imageBufferAttributes: spec as CFDictionary,
            outputCallback: &callback,
            decompressionSessionOut: &created
        )
        if made == noErr { session = created }
    }

    func decode(avcc: Data, key: Bool) {
        guard let session, let format else { return }
        var block: CMBlockBuffer?
        let created = CMBlockBufferCreateWithMemoryBlock(
            allocator: kCFAllocatorDefault,
            memoryBlock: nil,
            blockLength: avcc.count,
            blockAllocator: kCFAllocatorDefault,
            customBlockSource: nil,
            offsetToData: 0,
            dataLength: avcc.count,
            flags: 0,
            blockBufferOut: &block
        )
        guard created == kCMBlockBufferNoErr, let block else { return }
        let copied = avcc.withUnsafeBytes { raw -> OSStatus in
            guard let base = raw.baseAddress else { return -1 }
            return CMBlockBufferReplaceDataBytes(with: base, blockBuffer: block, offsetIntoDestination: 0, dataLength: avcc.count)
        }
        guard copied == kCMBlockBufferNoErr else { return }
        var timing = CMSampleTimingInfo(duration: .invalid, presentationTimeStamp: .invalid, decodeTimeStamp: .invalid)
        var sample: CMSampleBuffer?
        var size = avcc.count
        let ready = CMSampleBufferCreateReady(
            allocator: kCFAllocatorDefault,
            dataBuffer: block,
            formatDescription: format,
            sampleCount: 1,
            sampleTimingEntryCount: 1,
            sampleTimingArray: &timing,
            sampleSizeEntryCount: 1,
            sampleSizeArray: &size,
            sampleBufferOut: &sample
        )
        guard ready == noErr, let sample else { return }
        var info = VTDecodeInfoFlags()
        VTDecompressionSessionDecodeFrame(
            session,
            sampleBuffer: sample,
            flags: key ? VTDecodeFrameFlags(rawValue: 0) : VTDecodeFrameFlags(rawValue: 0),
            frameRefcon: nil,
            infoFlagsOut: &info
        )
    }

    private func emit(_ buffer: CVImageBuffer) {
        let image = CIImage(cvPixelBuffer: buffer)
        let rep = NSCIImageRep(ciImage: image)
        let picture = NSImage(size: rep.size)
        picture.addRepresentation(rep)
        let callback = onImage
        DispatchQueue.main.async { callback?(picture) }
    }
}

final class PcmPlay {
    private let engine = AVAudioEngine()
    private let player = AVAudioPlayerNode()
    private let format = AVAudioFormat(standardFormatWithSampleRate: 16_000, channels: 1)
    private var started = false

    func play(_ pcm: Data) {
        guard let format, pcm.count >= 2 else { return }
        if !started {
            engine.attach(player)
            engine.connect(player, to: engine.mainMixerNode, format: format)
            try? engine.start()
            player.play()
            started = true
        }
        let frames = pcm.count / 2
        guard let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: AVAudioFrameCount(frames)) else { return }
        buffer.frameLength = AVAudioFrameCount(frames)
        guard let dst = buffer.floatChannelData?[0] else { return }
        pcm.withUnsafeBytes { raw in
            let src = raw.bindMemory(to: Int16.self)
            for i in 0..<frames {
                dst[i] = Float(Int16(littleEndian: src[i])) / 32768
            }
        }
        player.scheduleBuffer(buffer, completionHandler: nil)
    }
}
