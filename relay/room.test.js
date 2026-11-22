import assert from "node:assert/strict";
import test from "node:test";
import { Room, keptRole, nextIdleStamp, noteRate, readMeta, sha256hex, writeMeta } from "./worker.js";

function memoryState(sockets) {
  const saved = new Map();
  return {
    sockets,
    storage: {
      async get(key) { return saved.get(key) || ""; },
      async put(key, value) { saved.set(key, value); },
      async delete(key) { saved.delete(key); },
    },
    acceptWebSocket(ws) { sockets.push(ws); },
    getWebSockets() { return sockets; },
  };
}

function socket() {
  let attachment = null;
  return {
    sent: [],
    closed: false,
    serializeAttachment(value) { attachment = value; },
    deserializeAttachment() { return attachment; },
    send(message) { this.sent.push(message); },
    close() { this.closed = true; },
  };
}

async function hello(role, stamp, key) {
  const mac = (await sha256hex(`${key}\n${role}\n${stamp}`)).slice(0, 32);
  return `${role} ${stamp} ${mac} ${key}`;
}

test("a wake-up still knows the role and the room key", async () => {
  const stamp = Math.floor(Date.now() / 1000);
  const key = "ab".repeat(32);
  const phone = socket();
  const state = memoryState([phone]);
  const room = new Room(state);
  await room.webSocketMessage(phone, await hello("phone", stamp, key));
  assert.equal(keptRole(readMeta(phone)), "phone");
  assert.equal(await state.storage.get("roomKey"), key);

  const woken = new Room(memoryState(state.getWebSockets()));
  woken.state.storage = state.storage;
  const again = woken.state.getWebSockets()[0];
  assert.equal(keptRole(readMeta(again)), "phone");
  assert.equal(await woken.state.storage.get("roomKey"), key);
});

test("silence closes the room, a recent frame does not, and a new join works", async () => {
  const stamp = Math.floor(Date.now() / 1000);
  const key = "cd".repeat(32);
  const phone = socket();
  const state = memoryState([phone]);
  const room = new Room(state);
  await room.webSocketMessage(phone, await hello("phone", stamp, key));
  const fresh = readMeta(phone);
  writeMeta(phone, { ...fresh, acceptedAt: Date.now() - 11 * 60 * 1000, lastFrame: Date.now() });
  await room.sweep(Date.now());
  assert.equal(phone.closed, false);

  writeMeta(phone, { ...readMeta(phone), lastFrame: Date.now() - 11 * 60 * 1000 });
  await room.sweep(Date.now());
  assert.equal(phone.closed, true);
  assert.equal(await state.storage.get("roomKey"), "");

  const again = socket();
  const next = new Room(memoryState([again]));
  next.state.storage = state.storage;
  await next.webSocketMessage(again, await hello("phone", stamp, key));
  assert.equal(keptRole(readMeta(again)), "phone");
  assert.equal(await next.state.storage.get("roomKey"), key);
});

test("the idle clock is written at most once every 30 seconds", () => {
  const start = 1_000_000;
  assert.equal(nextIdleStamp(start, start + 1_000), start);
  assert.equal(nextIdleStamp(start, start + 29_999), start);
  assert.equal(nextIdleStamp(start, start + 30_000), start + 30_000);
  assert.equal(nextIdleStamp(0, start), start);
});

test("each direction has its own rate limit and old counts are dropped", () => {
  const counts = new Map();
  counts.set("phone:1", 200);
  counts.set("mac:1", 200);
  assert.equal(noteRate(counts, "phone", 10, 200), true);
  assert.equal(noteRate(counts, "mac", 10, 200), true);
  for (let i = 0; i < 200; i++) noteRate(counts, "phone", 10, 200);
  assert.equal(noteRate(counts, "phone", 10, 200), false);
  assert.equal(noteRate(counts, "mac", 10, 200), true);
  assert.equal(counts.has("phone:1"), false);
  assert.equal(counts.has("mac:1"), false);
});
