export const PROTOCOL_VERSION = 1 as const;
export const APP_VERSION = "1.0.0";
export const APPCAST_URL = "https://github.com/CAPPY7/bridgelink/releases/latest/download/appcast.xml";

/** 1 when remote is newer, -1 when local is newer, 0 when they match. */
export function compareVersions(local: string, remote: string): number {
  const a = versionParts(local);
  const b = versionParts(remote);
  const n = Math.max(a.length, b.length);
  for (let i = 0; i < n; i++) {
    const av = a[i] ?? 0;
    const bv = b[i] ?? 0;
    if (bv !== av) return bv > av ? 1 : -1;
  }
  return 0;
}

export function versionInAppcast(xml: string): string {
  const match = xml.match(/sparkle:shortVersionString="([^"]+)"/);
  return match?.[1]?.trim() ?? "";
}

function versionParts(raw: string): number[] {
  return raw.split(".").map((bit) => {
    const n = Number.parseInt(bit, 10);
    return Number.isFinite(n) ? n : 0;
  });
}
export const CHUNK_SIZE = 256 * 1024;

export type Side = "android" | "mac";

export type Envelope = {
  v: typeof PROTOCOL_VERSION;
  id: string;
  type: string;
  from: Side;
  ts: number;
  payload: Record<string, unknown>;
};

export function encodeEnvelope(envelope: Envelope): string {
  return JSON.stringify(envelope);
}

export function decodeEnvelope(raw: string): Envelope {
  let value: unknown;
  try {
    value = JSON.parse(raw);
  } catch {
    throw new Error("Envelope is not JSON");
  }
  if (!value || typeof value !== "object") throw new Error("Envelope is empty");
  const e = value as Partial<Envelope>;
  if (e.v !== PROTOCOL_VERSION) throw new Error("Unsupported protocol version");
  if (typeof e.id !== "string" || !e.id) throw new Error("Missing message id");
  if (typeof e.type !== "string" || !e.type) throw new Error("Missing message type");
  if (e.from !== "android" && e.from !== "mac") throw new Error("Bad sender");
  if (typeof e.ts !== "number") throw new Error("Missing timestamp");
  if (!e.payload || typeof e.payload !== "object") throw new Error("Missing payload");
  return e as Envelope;
}

export function splitChunks(bytes: Uint8Array, size = CHUNK_SIZE): Uint8Array[] {
  if (size < 1) throw new Error("Chunk size must be positive");
  if (bytes.length === 0) return [new Uint8Array()];
  const out: Uint8Array[] = [];
  for (let i = 0; i < bytes.length; i += size) out.push(bytes.subarray(i, i + size));
  return out;
}

export function rememberId(seen: string[], id: string, cap = 500): { seen: string[]; fresh: boolean } {
  if (seen.includes(id)) return { seen, fresh: false };
  return { seen: [...seen, id].slice(-cap), fresh: true };
}

/** Production reconnect delay. The desk shortens the wait so you can see it finish. */
export function nextBackoff(attempt: number): number {
  const n = Math.max(0, attempt);
  return Math.min(30_000, 500 * 2 ** n);
}

export function extractOtp(text: string): string | null {
  if (!/(code|otp|verify|verification|passcode|\bpin\b)/i.test(text)) return null;
  const match = text.match(/\b(\d{4,8})\b/);
  return match?.[1] ?? null;
}

export function looksSensitive(text: string, source?: string): boolean {
  if (source && /1password|bitwarden|keepass|keychain|password manager/i.test(source)) return true;
  const t = text.trim();
  return t.length >= 12 && !/\s/.test(t) && /[A-Za-z]/.test(t) && /\d/.test(t);
}

export function bytesToHex(bytes: Uint8Array): string {
  return [...bytes].map((b) => b.toString(16).padStart(2, "0")).join("");
}

export function hexToBytes(hex: string): Uint8Array {
  if (hex.length % 2 !== 0) throw new Error("Bad hex");
  const out = new Uint8Array(hex.length / 2);
  for (let i = 0; i < out.length; i++) out[i] = Number.parseInt(hex.slice(i * 2, i * 2 + 2), 16);
  return out;
}

export function randomHex(bytes = 32): string {
  const buf = new Uint8Array(bytes);
  crypto.getRandomValues(buf);
  return bytesToHex(buf);
}

export function formatFingerprint(hex: string): string {
  return hex.slice(0, 16).toUpperCase().replace(/(.{4})/g, "$1 ").trim();
}

export function pairingGrid(seed: string, n = 21): boolean[][] {
  let h = 2166136261;
  for (const c of seed) h = Math.imul(h ^ c.charCodeAt(0), 16777619);
  const grid = Array.from({ length: n }, () => Array<boolean>(n).fill(false));
  const rnd = () => {
    h = Math.imul(h ^ (h >>> 15), 2246822519);
    h = Math.imul(h ^ (h >>> 13), 3266489917);
    h ^= h >>> 16;
    return (h >>> 0) / 4294967296;
  };
  for (let y = 0; y < n; y++) {
    for (let x = 0; x < n; x++) grid[y][x] = rnd() > 0.58;
  }
  const finder = (ox: number, oy: number) => {
    for (let y = 0; y < 7; y++) {
      for (let x = 0; x < 7; x++) {
        const edge = x === 0 || y === 0 || x === 6 || y === 6 || (x >= 2 && x <= 4 && y >= 2 && y <= 4);
        grid[oy + y][ox + x] = edge;
      }
    }
  };
  finder(0, 0);
  finder(n - 7, 0);
  finder(0, n - 7);
  return grid;
}

async function aesKey(hex: string): Promise<CryptoKey> {
  const raw = hexToBytes(hex);
  if (raw.length !== 32) throw new Error("Expected a 32-byte key");
  return crypto.subtle.importKey("raw", raw.buffer.slice(raw.byteOffset, raw.byteOffset + raw.byteLength) as ArrayBuffer, "AES-GCM", false, ["encrypt", "decrypt"]);
}

function bytesToB64(bytes: Uint8Array): string {
  let binary = "";
  for (let i = 0; i < bytes.length; i += 0x8000) {
    binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  }
  return btoa(binary);
}

function b64ToBytes(b64: string): Uint8Array {
  const binary = atob(b64);
  const out = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) out[i] = binary.charCodeAt(i);
  return out;
}

/** AES-GCM seal for relay blobs. The relay would only see this string. */
export async function sealJson(value: unknown, keyHex: string): Promise<string> {
  const key = await aesKey(keyHex);
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const data = new TextEncoder().encode(JSON.stringify(value));
  const cipher = new Uint8Array(await crypto.subtle.encrypt({ name: "AES-GCM", iv }, key, data));
  const packed = new Uint8Array(iv.length + cipher.length);
  packed.set(iv, 0);
  packed.set(cipher, iv.length);
  return bytesToB64(packed);
}

export async function openJson<T>(sealed: string, keyHex: string): Promise<T> {
  const packed = b64ToBytes(sealed);
  const iv = packed.slice(0, 12);
  const cipher = packed.slice(12);
  const key = await aesKey(keyHex);
  const plain = await crypto.subtle.decrypt({ name: "AES-GCM", iv }, key, cipher);
  return JSON.parse(new TextDecoder().decode(plain)) as T;
}
