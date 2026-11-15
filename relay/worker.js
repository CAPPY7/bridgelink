export class Room {
  constructor(state) {
    this.state = state;
    this.peers = new Map();
  }

  async fetch(request) {
    if (request.headers.get("Upgrade") !== "websocket") {
      return new Response("BridgeLink relay. Frames stay locked.", { status: 426 });
    }
    const pair = new WebSocketPair();
    const client = pair[0];
    const server = pair[1];
    server.accept();
    const slot = { role: "", ready: false, socket: server };
    server.addEventListener("message", (event) => {
      const data = event.data;
      if (!slot.ready) {
        if (typeof data !== "string" || data.length > 80) {
          server.close(1008, "hello");
          return;
        }
        const bits = data.split(" ");
        if ((bits[0] !== "phone" && bits[0] !== "mac") || !/^[a-f0-9]{32}$/.test(bits[1] || "")) {
          server.close(1008, "hello");
          return;
        }
        slot.role = bits[0];
        slot.ready = true;
        this.peers.set(slot.role, server);
        return;
      }
      if (typeof data === "string") {
        server.close(1008, "plain");
        return;
      }
      const size = data.byteLength || data.size || 0;
      if (size < 28 || size > 1048576) return;
      const other = slot.role === "phone" ? this.peers.get("mac") : this.peers.get("phone");
      if (!other) return;
      try {
        other.send(data);
      } catch (error) {
        other.close();
      }
    });
    server.addEventListener("close", () => {
      if (this.peers.get(slot.role) === server) this.peers.delete(slot.role);
    });
    return new Response(null, { status: 101, webSocket: client });
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
