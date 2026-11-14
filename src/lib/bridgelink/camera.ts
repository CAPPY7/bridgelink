let stream: MediaStream | null = null;
const videos = new Set<HTMLVideoElement>();

function attachAll() {
  if (!stream) return;
  for (const video of videos) video.srcObject = stream;
}

export async function ensureCamera(): Promise<string | null> {
  if (!navigator.mediaDevices?.getUserMedia) return "This browser has no camera API.";
  if (stream) {
    attachAll();
    return null;
  }
  try {
    stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true });
    attachAll();
    return null;
  } catch (err) {
    return err instanceof Error ? err.message : "Camera unavailable";
  }
}

export function bindVideo(node: HTMLVideoElement | null) {
  if (!node) return;
  videos.add(node);
  if (stream) node.srcObject = stream;
}

export function unbindVideo(node: HTMLVideoElement | null) {
  if (!node) return;
  videos.delete(node);
  node.srcObject = null;
}

export function releaseCamera() {
  stream?.getTracks().forEach((track) => track.stop());
  stream = null;
  for (const video of videos) video.srcObject = null;
}
