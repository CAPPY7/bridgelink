import assert from "node:assert/strict";
import test from "node:test";
import { Room, keptRole, nextIdleStamp, noteRate, readMeta, sha256hex, socketStillOpen, writeMeta } from "./worker.js";

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

function socket(preset) {
  let attachment = preset === undefined ? null : preset;
  return {
    sent: [],
    closed: false,
    why: "",
    serializeAttachment(value) { attachment = value; },
    deserializeAttachment() { return attachment; },
    send(message) { this.sent.push(message); },
    close(_code, why) { this.closed = true; this.why = why || ""; },
  };
}

function wake(old) {
  const saved = old.deserializeAttachment();
  const copy = saved ? JSON.parse(JSON.stringify(saved)) : null;
  return socket(copy);
}

async function hello(role, stamp, key) {
  const mac = (await sha256hex(`${key}\n${role}\n${stamp}`)).slice(0, 32);
  return `${role} ${stamp} ${mac} ${key}`;
}

test("a wake-up still forwards a frame", async () => {
  const stamp = Math.floor(Date.now() / 1000);
  const key = "ab".repeat(32);
  const phone = socket();
  const mac = socket();
  const state = memoryState([phone, mac]);
  const room = new Room(state);
  await room.webSocketMessage(phone, await hello("phone", stamp, key));
  await room.webSocketMessage(mac, await hello("mac", stamp, key));
  assert.equal(await state.storage.get("roomKey"), key);

  const phone2 = wake(phone);
  const mac2 = wake(mac);
  assert.notEqual(phone2, phone);
  const woken = new Room(memoryState([phone2, mac2]));
  woken.state.storage = state.storage;
  const frame = new Uint8Array([9, 8, 7]);
  await woken.webSocketMessage(phone2, frame);
  assert.equal(phone2.closed, false);
  assert.notEqual(phone2.why, "hello");
  assert.equal(mac2.sent.length, 1);
  assert.deepEqual([...mac2.sent[0]], [9, 8, 7]);
  assert.equal(keptRole(readMeta(phone2)), "phone");
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

test("a closed socket with no closed field still drops the room key", async () => {
  const stamp = Math.floor(Date.now() / 1000);
  const key = "ef".repeat(32);
  let attachment = null;
  const phone = {
    sent: [],
    readyState: 1,
    why: "",
    serializeAttachment(value) { attachment = value; },
    deserializeAttachment() { return attachment; },
    send(message) { this.sent.push(message); },
    close(_code, why) { this.readyState = 3; this.why = why || ""; },
  };
  assert.equal(Object.hasOwn(phone, "closed"), false);
  const state = memoryState([phone]);
  const room = new Room(state);
  await room.webSocketMessage(phone, await hello("phone", stamp, key));
  writeMeta(phone, { ...readMeta(phone), lastFrame: Date.now() - 11 * 60 * 1000 });
  await room.sweep(Date.now());
  assert.equal(phone.readyState, 3);
  assert.equal(socketStillOpen(phone), false);
  assert.equal(await state.storage.get("roomKey"), "");
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
