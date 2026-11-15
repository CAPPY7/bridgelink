import assert from "node:assert/strict";
import { test } from "node:test";
import {
  decodeEnvelope,
  encodeEnvelope,
  extractOtp,
  looksSensitive,
  nextBackoff,
  openJson,
  rememberId,
  sealJson,
  splitChunks,
  compareVersions,
  versionInAppcast,
} from "./protocol.ts";

test("release compare", () => {
  assert.equal(compareVersions("0.13.0", "1.0.0"), 1);
  assert.equal(compareVersions("1.0.0", "1.0.0"), 0);
  assert.equal(compareVersions("1.1.0", "1.0.0"), -1);
  assert.equal(compareVersions("1.0", "1.0.0"), 0);
  const xml = '<item sparkle:shortVersionString="1.0.0" sparkle:version="13"></item>';
  assert.equal(versionInAppcast(xml), "1.0.0");
  assert.equal(versionInAppcast("nope"), "");
});

test("envelope roundtrip", () => {
  const raw = encodeEnvelope({
    v: 1,
    id: "abc",
    type: "clipboard",
    from: "android",
    ts: 10,
    payload: { text: "hello" },
  });
  const back = decodeEnvelope(raw);
  assert.equal(back.payload.text, "hello");
  assert.throws(() => decodeEnvelope("{"), /JSON/);
  assert.throws(() => decodeEnvelope(JSON.stringify({ v: 2 })), /version/);
});

test("chunks and dedupe", () => {
  const parts = splitChunks(new Uint8Array(500), 200);
  assert.equal(parts.length, 3);
  assert.equal(parts[2].length, 100);
  assert.equal(splitChunks(new Uint8Array(), 10).length, 1);
  const first = rememberId([], "a");
  assert.equal(first.fresh, true);
  const second = rememberId(first.seen, "a");
  assert.equal(second.fresh, false);
});

test("backoff doubles then caps", () => {
  assert.equal(nextBackoff(0), 500);
  assert.equal(nextBackoff(1), 1000);
  assert.equal(nextBackoff(2), 2000);
  assert.equal(nextBackoff(20), 30_000);
});

test("otp and sensitive detection", () => {
  assert.equal(extractOtp("Your verification code is 482913."), "482913");
  assert.equal(extractOtp("See you at 6"), null);
  assert.equal(looksSensitive("abc123XYZ890"), true);
  assert.equal(looksSensitive("Meet at the lobby"), false);
  assert.equal(looksSensitive("hello", "1Password"), true);
});

test("relay seal is not plaintext and opens", async () => {
  const key = "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff";
  const sealed = await sealJson({ text: "secret-note" }, key);
  assert.equal(sealed.includes("secret-note"), false);
  const opened = await openJson<{ text: string }>(sealed, key);
  assert.equal(opened.text, "secret-note");
  await assert.rejects(() => openJson(sealed, key.replace("00", "ff")));
});
