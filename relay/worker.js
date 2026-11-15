const MAX_MESSAGE = 512 * 1024;
const WINDOW_SEC = 120;
const MAX_PER_SECOND = 200;

async function sha256hex(text) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

async function ticketOk(hello, now) {
  const bits = String(hello || "").trim().split(" ");
  if (bits.length !== 4) return false;
  if (bits[0] !== "phone" && bits[0] !== "mac") return false;
  const stamp = Number(bits[1]);
  if (!Number.isFinite(stamp) || Math.abs(now - stamp) > WINDOW_SEC) return false;
  if (!/^[a-f0-9]{64}$/.test(bits[3])) return false;
  const expect = (await sha256hex(`${bits[3]}\n${bits[0]}\n${stamp}`)).slice(0, 32);
  return expect === bits[2];
}

export class Room {
  constructor(state) {
    this.state = state;
    this.key = "";
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
    const role = ws.role || "";
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
      const sockets = this.state.getWebSockets();
      const live = sockets.some((peer) => peer !== ws && peer.role === bits[0] && peer.ready);
      if (this.key && live && this.key !== offered) {
        ws.close(1008, "busy");
        return;
      }
      if (!this.key) this.key = offered;
      if (this.key !== offered) {
        ws.close(1008, "key");
        return;
      }
      const others = sockets.filter((peer) => peer !== ws && peer.ready);
      if (others.length >= 2) {
        ws.close(1008, "full");
        return;
      }
      ws.role = bits[0];
      ws.ready = true;
      ws.acceptedAt = Date.now();
      return;
    }
    if (typeof message === "string") {
      ws.close(1008, "plain");
      return;
    }
    const size = message.byteLength || message.size || 0;
    if (!size || size > MAX_MESSAGE) return;
    const second = now;
    const used = (this.counts.get(second) || 0) + 1;
    this.counts.set(second, used);
    if (used > MAX_PER_SECOND) {
      ws.close(1008, "rate");
      return;
    }
    const otherRole = role === "phone" ? "mac" : "phone";
    const other = this.state.getWebSockets().find((peer) => peer !== ws && peer.role === otherRole && peer.ready);
    if (!other) return;
    try {
      other.send(message);
    } catch (error) {
      other.close();
    }
    this.sweep();
  }

  async webSocketClose() {
    this.sweep();
  }

  sweep() {
    const sockets = this.state.getWebSockets();
    const now = Date.now();
    for (const peer of sockets) {
      if (peer.acceptedAt && now - peer.acceptedAt > 10 * 60 * 1000) peer.close(1000, "idle");
    }
    if (sockets.length === 0) this.key = "";
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
