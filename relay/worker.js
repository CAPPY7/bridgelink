const MAX_MESSAGE = 512 * 1024;
const WINDOW_SEC = 120;
const MAX_PER_SECOND = 200;
const IDLE_MS = 10 * 60 * 1000;
const IDLE_STAMP_MS = 30 * 1000;

/** Write the idle clock at most once every 30 seconds. */
export function nextIdleStamp(lastFrame, now, gap = IDLE_STAMP_MS) {
  const last = Number(lastFrame) || 0;
  if (last > 0 && now - last < gap) return last;
  return now;
}

export async function sha256hex(text) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

export async function ticketOk(hello, now) {
  const bits = String(hello || "").trim().split(" ");
  if (bits.length !== 4) return false;
  if (bits[0] !== "phone" && bits[0] !== "mac") return false;
  const stamp = Number(bits[1]);
  if (!Number.isFinite(stamp) || Math.abs(now - stamp) > WINDOW_SEC) return false;
  if (!/^[a-f0-9]{64}$/.test(bits[3])) return false;
  const expect = (await sha256hex(`${bits[3]}\n${bits[0]}\n${stamp}`)).slice(0, 32);
  return expect === bits[2];
}

export function readMeta(ws) {
  if (!ws || typeof ws.deserializeAttachment !== "function") return {};
  const meta = ws.deserializeAttachment();
  return meta && typeof meta === "object" ? meta : {};
}

export function writeMeta(ws, meta) {
  ws.serializeAttachment(meta);
}

/** Cloudflare sockets have no `closed` field. 1 means still open. */
export function socketStillOpen(peer) {
  if (!peer) return false;
  const state = peer.readyState;
  if (state === 1 || state === "open" || state === "OPEN") return true;
  if (state === 0 || state === 2 || state === 3) return false;
  if (state === "connecting" || state === "closing" || state === "closed") return false;
  return peer.closed !== true;
}

/** A wake-up must still know the role. The room key lives in storage, not memory. */
export function keptRole(meta) {
  return meta && meta.role ? meta.role : "";
}

export function noteRate(counts, role, second, max) {
  for (const key of [...counts.keys()]) {
    const sec = Number(String(key).split(":")[1]);
    if (!Number.isFinite(sec) || sec < second - 2) counts.delete(key);
  }
  const bucket = `${role}:${second}`;
  const used = (counts.get(bucket) || 0) + 1;
  counts.set(bucket, used);
  return used <= max;
}

export class Room {
  constructor(state) {
    this.state = state;
    this.counts = new Map();
  }

  async fetch(request) {
    if (request.headers.get("Upgrade") !== "websocket") {
      return new Response("BridgeLink relay. Frames stay locked.", { status: 426 });
    }
    const pair = new WebSocketPair();
    this.state.acceptWebSocket(pair[1]);
    return new Response(null, { status: 101, webSocket: pair[0] });
  }

  async webSocketMessage(ws, message) {
    const now = Math.floor(Date.now() / 1000);
    const meta = readMeta(ws);
    const role = keptRole(meta);
    if (!role) {
      if (typeof message !== "string" || message.length > 200) {
        ws.close(1008, "hello");
        return;
      }
      if (!(await ticketOk(message, now))) {
        ws.close(1008, "ticket");
        return;
      }
      const bits = message.trim().split(" ");
      const offered = bits[3];
      const stored = (await this.state.storage.get("roomKey")) || "";
      const sockets = this.state.getWebSockets();
      const live = sockets.some((peer) => {
        if (peer === ws) return false;
        const peerMeta = readMeta(peer);
        return peerMeta.role === bits[0] && peerMeta.ready;
      });
      if (stored && live && stored !== offered) {
        ws.close(1008, "busy");
        return;
      }
      const key = stored || offered;
      if (!stored) await this.state.storage.put("roomKey", key);
      if (key !== offered) {
        ws.close(1008, "key");
        return;
      }
      const others = sockets.filter((peer) => {
        if (peer === ws) return false;
        return readMeta(peer).ready;
      });
      if (others.length >= 2) {
        ws.close(1008, "full");
        return;
      }
      writeMeta(ws, {
        role: bits[0],
        ready: true,
        acceptedAt: Date.now(),
        lastFrame: Date.now(),
      });
      return;
    }
    if (typeof message === "string") {
      ws.close(1008, "plain");
      return;
    }
    const size = message.byteLength || message.size || 0;
    if (!size || size > MAX_MESSAGE) return;
    const second = now;
    if (!noteRate(this.counts, role, second, MAX_PER_SECOND)) {
      ws.close(1008, "rate");
      return;
    }
    const stamp = nextIdleStamp(meta.lastFrame, Date.now());
    if (stamp !== meta.lastFrame) writeMeta(ws, { ...meta, lastFrame: stamp });
    const otherRole = role === "phone" ? "mac" : "phone";
    const other = this.state.getWebSockets().find((peer) => {
      if (peer === ws) return false;
      const peerMeta = readMeta(peer);
      return peerMeta.role === otherRole && peerMeta.ready;
    });
    if (!other) return;
    try {
      other.send(message);
    } catch (error) {
      other.close();
    }
    await this.sweep();
  }

  async webSocketClose() {
    await this.sweep();
  }

  async sweep(now = Date.now()) {
    const sockets = this.state.getWebSockets();
    const justClosed = new Set();
    for (const peer of sockets) {
      const meta = readMeta(peer);
      if (!meta.ready || !meta.lastFrame) continue;
      if (now - meta.lastFrame > IDLE_MS) {
        justClosed.add(peer);
        try {
          peer.close(1000, "idle");
        } catch (error) {
          /* already gone */
        }
      }
    }
    const alive = sockets.filter((peer) => !justClosed.has(peer) && socketStillOpen(peer));
    if (alive.length === 0) await this.state.storage.delete("roomKey");
  }
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const match = url.pathname.match(/^\/room\/([a-f0-9]{32})$/);
    if (!match) return new Response("not found", { status: 404 });
    const id = env.ROOMS.idFromName(match[1]);
    return env.ROOMS.get(id).fetch(request);
  },
};
