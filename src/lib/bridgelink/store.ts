import { create } from "zustand";
import { persist, createJSONStorage } from "zustand/middleware";
import {
  APP_VERSION,
  CHUNK_SIZE,
  compareVersions,
  extractOtp,
  looksSensitive,
  nextBackoff,
  randomHex,
  rememberId,
  type Envelope,
  type Side,
} from "./protocol";

export const APP_NAME = "BridgeLink";
export { APP_VERSION };

export type FeatureKey =
  | "transfers"
  | "share"
  | "notifications"
  | "media"
  | "battery"
  | "find"
  | "otp"
  | "links"
  | "input"
  | "clipboard"
  | "sms"
  | "mirror"
  | "control"
  | "camera"
  | "files"
  | "proximity"
  | "inbox"
  | "clipHistory"
  | "scan"
  | "automations"
  | "quickdrop";

export type ClipMethod = "tile" | "share" | "notification" | "bubble" | "accessibility";
export type LinkPath = "lan" | "manual";
export type PhoneScreen = "home" | "send" | "inbox" | "remote" | "system";
export type SystemPage =
  | "root"
  | "settings"
  | "permissions"
  | "clipboard"
  | "files"
  | "scan"
  | "automations"
  | "mirror"
  | "camera"
  | "conditions";
export type MacPane =
  | "home"
  | "inbox"
  | "clips"
  | "messages"
  | "mirror"
  | "files"
  | "camera"
  | "rules"
  | "settings"
  | "browser";
export type RemoteMode = "trackpad" | "keyboard" | "slides";

export type SavedPhone = { id: string; name: string; battery: number; charging: boolean };
export type SavedMac = { id: string; name: string };

export type Notice = {
  id: string;
  app: string;
  title: string;
  body: string;
  ts: number;
  dismissed: boolean;
  pinned: boolean;
  actions: string[];
  source: "notification" | "sms" | "otp" | "system";
  reply?: string;
};

export type SmsMessage = { id: string; threadId: string; from: "them" | "me"; body: string; ts: number };
export type Thread = { id: string; name: string; number: string; messages: SmsMessage[] };
export type ClipItem = {
  id: string;
  text: string;
  from: Side;
  ts: number;
  pinned: boolean;
  sensitive: boolean;
  source?: string;
};
export type Transfer = {
  id: string;
  from: Side;
  to: Side;
  kind: "text" | "link" | "file";
  name: string;
  text?: string;
  size: number;
  progress: number;
  status: "queued" | "sending" | "paused" | "done" | "failed";
  chunks: number;
  chunkDone: number;
  createdAt: number;
};
export type PhoneFile = { id: string; folder: string; name: string; size: number; kind: string };
export type Automation = {
  id: string;
  name: string;
  enabled: boolean;
  trigger: "wifi" | "charging" | "time" | "bluetooth";
  triggerValue: string;
  action: "notify" | "lock" | "sync" | "send";
  actionValue: string;
};
export type LogLine = { id: string; ts: number; level: "info" | "warn" | "error"; msg: string };
export type Media = { title: string; artist: string; playing: boolean; volume: number; index: number };

export const TRACKS = [
  { title: "Night Drive", artist: "Low Tide" },
  { title: "Glass Door", artist: "Harbor" },
  { title: "Late Platform", artist: "North Line" },
] as const;

export type Permissions = {
  android: {
    notifications: boolean;
    sms: boolean;
    contacts: boolean;
    battery: boolean;
    accessibility: boolean;
    overlay: boolean;
    camera: boolean;
    mic: boolean;
    storage: boolean;
  };
  mac: {
    localNetwork: boolean;
    notifications: boolean;
    accessibility: boolean;
    camera: boolean;
    microphone: boolean;
    files: boolean;
  };
};

const pauses = new Set<string>();
const fileBlobs = new Map<string, File>();
let reconnectTimer: ReturnType<typeof setTimeout> | null = null;

const now = () => Date.now();
const id = () => crypto.randomUUID();
export const DEMO_NOW = 1_763_136_000_000;

function seed(): Pick<
  BridgeState,
  | "paired"
  | "androidName"
  | "macName"
  | "androidId"
  | "macId"
  | "sharedKey"
  | "pinnedAndroidFp"
  | "pinnedMacFp"
  | "seenAndroidFp"
  | "seenMacFp"
  | "features"
  | "permissions"
  | "notices"
  | "threads"
  | "clips"
  | "transfers"
  | "files"
  | "rules"
  | "media"
  | "phoneBattery"
  | "macBattery"
  | "phoneClipboard"
  | "macClipboard"
> {
  const t = DEMO_NOW;
  return {
    paired: true,
    androidName: "Pixel",
    macName: "MacBook",
    androidId: "and-pixel-01",
    macId: "mac-book-01",
    sharedKey: "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff",
    pinnedAndroidFp: "a1b2c3d4e5f60718",
    pinnedMacFp: "9180716f5e4d3c2b",
    seenAndroidFp: "a1b2c3d4e5f60718",
    seenMacFp: "9180716f5e4d3c2b",
    features: {
      transfers: true,
      share: true,
      notifications: true,
      media: true,
      battery: true,
      find: true,
      otp: true,
      links: true,
      input: false,
      clipboard: true,
      sms: false,
      mirror: false,
      control: false,
      camera: false,
      files: false,
      proximity: false,
      inbox: true,
      clipHistory: true,
      scan: false,
      automations: false,
      quickdrop: true,
    },
    permissions: {
      android: {
        notifications: true,
        sms: false,
        contacts: false,
        battery: true,
        accessibility: false,
        overlay: false,
        camera: false,
        mic: false,
        storage: false,
      },
      mac: {
        localNetwork: true,
        notifications: true,
        accessibility: false,
        camera: false,
        microphone: false,
        files: false,
      },
    },
    notices: [
      {
        id: "n-otp",
        app: "Bank",
        title: "Verification code",
        body: "Your verification code is 482913. It expires in 10 minutes.",
        ts: t - 12 * 60_000,
        dismissed: false,
        pinned: true,
        actions: ["Copy code"],
        source: "otp",
      },
      {
        id: "n-mail",
        app: "Gmail",
        title: "Invoice 1042",
        body: "Northwind Studio sent a PDF invoice.",
        ts: t - 40 * 60_000,
        dismissed: false,
        pinned: false,
        actions: ["Reply", "Dismiss"],
        source: "notification",
      },
    ],
    threads: [
      {
        id: "t1",
        name: "Alex",
        number: "+1 415 555 0148",
        messages: [
          { id: "m1", threadId: "t1", from: "them", body: "Can you send the deck to the Mac?", ts: t - 50 * 60_000 },
          { id: "m2", threadId: "t1", from: "me", body: "Sending it now.", ts: t - 48 * 60_000 },
        ],
      },
    ],
    clips: [
      { id: "c1", text: "https://example.com/deck", from: "android", ts: t - 20 * 60_000, pinned: true, sensitive: false },
      { id: "c2", text: "Meet at 6 in the lobby", from: "mac", ts: t - 15 * 60_000, pinned: false, sensitive: false },
      { id: "c3", text: "482913", from: "android", ts: t - 12 * 60_000, pinned: false, sensitive: false },
    ],
    transfers: [
      {
        id: "x1",
        from: "android",
        to: "mac",
        kind: "file",
        name: "Deck.pdf",
        size: 2_400_000,
        progress: 1,
        status: "done",
        chunks: 10,
        chunkDone: 10,
        createdAt: t - 30 * 60_000,
      },
    ],
    files: [
      { id: "f1", folder: "Documents", name: "Deck.pdf", size: 2_400_000, kind: "pdf" },
      { id: "f2", folder: "DCIM", name: "IMG_2044.jpg", size: 1_800_000, kind: "image" },
      { id: "f3", folder: "Downloads", name: "Notes.txt", size: 1280, kind: "text" },
    ],
    rules: [
      {
        id: "r1",
        name: "Home Wi-Fi photo sync",
        enabled: false,
        trigger: "wifi",
        triggerValue: "Home",
        action: "sync",
        actionValue: "Pictures",
      },
      {
        id: "r2",
        name: "Charging ping",
        enabled: false,
        trigger: "charging",
        triggerValue: "start",
        action: "notify",
        actionValue: "Phone started charging",
      },
    ],
    media: { title: "Night Drive", artist: "Low Tide", playing: true, volume: 0.6, index: 0 },
    phoneBattery: 64,
    macBattery: 81,
    phoneClipboard: "https://example.com/deck",
    macClipboard: "Meet at 6 in the lobby",
  };
}

const seeded = seed();

export type BridgeState = {
  hydrated: boolean;
  tourDismissed: boolean;
  macSetupDone: boolean;
  phoneSetupDone: boolean;
  updateLine: string;
  phones: SavedPhone[];
  activePhoneId: string;
  macs: SavedMac[];
  activeMacId: string;
  paired: boolean;
  pin: string;
  pinExpires: number;
  androidName: string;
  macName: string;
  androidId: string;
  macId: string;
  sharedKey: string;
  pinnedAndroidFp: string;
  pinnedMacFp: string;
  seenAndroidFp: string;
  seenMacFp: string;
  status: "connected" | "reconnecting" | "offline";
  attempt: number;
  path: LinkPath;
  manualHost: string;
  fcmEnabled: boolean;
  latencyMs: number;
  lastHeartbeat: number;
  features: Record<FeatureKey, boolean>;
  permissions: Permissions;
  phoneBattery: number;
  phoneCharging: boolean;
  macBattery: number;
  lowBatterySent: boolean;
  wifi: string;
  headphones: boolean;
  phoneNear: boolean;
  lockDelaySec: number;
  graceUntil: number | null;
  macLocked: boolean;
  ringing: boolean;
  media: Media;
  notices: Notice[];
  threads: Thread[];
  clips: ClipItem[];
  transfers: Transfer[];
  files: PhoneFile[];
  sharedFolders: string[];
  rules: Automation[];
  timeMarks: Record<string, string>;
  phoneClipboard: string;
  macClipboard: string;
  clipMethod: ClipMethod;
  hideSensitive: boolean;
  retentionDays: number;
  blockedApps: string[];
  phoneScreen: PhoneScreen;
  systemPage: SystemPage;
  macPane: MacPane;
  remoteMode: RemoteMode;
  slide: number;
  cursor: { x: number; y: number; down: boolean };
  typedOnMac: string;
  androidBrowser: string | null;
  macBrowser: string | null;
  mirrorApproved: boolean;
  cameraActive: boolean;
  cameraError: string;
  debug: boolean;
  logs: LogLine[];
  seenIds: string[];
  lastError: string;
  activeThread: string;
  macMenuOpen: boolean;
  theme: "dark" | "light";
  log: (level: LogLine["level"], msg: string) => void;
  gate: (feature?: FeatureKey) => string | null;
  mismatch: () => boolean;
  setTheme: (theme: "dark" | "light") => void;
  dismissTour: () => void;
  finishMacSetup: () => void;
  finishPhoneSetup: () => void;
  replaySetup: () => void;
  checkUpdate: (offerNewer?: boolean) => void;
  resetDesk: () => void;
  beginPairing: () => void;
  submitPin: (pin: string) => void;
  forgetDevice: () => void;
  triggerKeyChange: () => void;
  trustKey: () => void;
  rejectKey: () => void;
  setFeature: (key: FeatureKey, on: boolean) => void;
  setPermission: (side: "android" | "mac", key: string, on: boolean) => void;
  setPath: (path: LinkPath) => void;
  setManualHost: (host: string) => void;
  setFcm: (on: boolean) => void;
  heartbeat: () => void;
  dropLink: () => void;
  sendPayload: (from: Side, kind: "text" | "link" | "file", name: string, text: string | undefined, size: number, chunks: number) => Promise<string | null>;
  pauseTransfer: (id: string) => void;
  resumeTransfer: (id: string) => void;
  attachFile: (transferId: string, file: File) => void;
  sendFile: (from: Side, file: File) => Promise<void>;
  pushNotice: (notice: Omit<Notice, "id" | "ts" | "dismissed" | "pinned" | "reply"> & { id?: string }) => void;
  dismissNotice: (id: string) => void;
  pinNotice: (id: string) => void;
  replyNotice: (id: string, text: string) => void;
  blockApp: (app: string, blocked: boolean) => void;
  setPhoneClipboard: (text: string, echo?: boolean) => void;
  setMacClipboard: (text: string, echo?: boolean) => void;
  pushClipboard: (from: Side) => void;
  copyText: (text: string, from: Side) => void;
  setClipMethod: (method: ClipMethod) => void;
  togglePinClip: (id: string) => void;
  setMedia: (patch: Partial<Media>) => void;
  setPhoneBattery: (n: number) => void;
  setPhoneCharging: (on: boolean) => void;
  setMacBattery: (n: number) => void;
  setWifi: (wifi: string) => void;
  setHeadphones: (on: boolean) => void;
  setPhoneNear: (near: boolean) => void;
  setLockDelay: (sec: number) => void;
  unlockMac: () => void;
  tick: () => void;
  startRing: () => void;
  stopRing: () => void;
  setNames: (androidName: string, macName: string) => void;
  switchPhone: (id: string) => void;
  addPhone: (name: string) => void;
  forgetPhone: (id: string) => void;
  switchMac: (id: string) => void;
  addMac: (name: string) => void;
  forgetMac: (id: string) => void;
  setPhoneScreen: (screen: PhoneScreen) => void;
  setSystemPage: (page: SystemPage) => void;
  setMacPane: (pane: MacPane) => void;
  setRemoteMode: (mode: RemoteMode) => void;
  setCursor: (x: number, y: number, down?: boolean) => void;
  typeOnMac: (text: string) => void;
  nudgeSlide: (dir: number) => void;
  closeBrowser: (side: Side) => void;
  approveMirror: (on: boolean) => void;
  setCamera: (active: boolean, error?: string) => void;
  sendSms: (threadId: string, body: string, from: "them" | "me") => void;
  setActiveThread: (id: string) => void;
  toggleFolder: (folder: string) => void;
  addRule: (rule: Omit<Automation, "id">) => void;
  toggleRule: (id: string, on: boolean) => void;
  removeRule: (id: string) => void;
  setRetention: (days: number) => void;
  setHideSensitive: (on: boolean) => void;
  setDebug: (on: boolean) => void;
  clearHistory: () => void;
  purgeExpired: () => void;
  exportDiagnostics: () => string;
  setMenu: (open: boolean) => void;
};

function within(ts: number, days: number) {
  return now() - ts < days * 86_400_000;
}

function applyRule(get: () => BridgeState, rule: Automation) {
  const s = get();
  if (rule.action === "notify") {
    s.pushNotice({
      app: "Automations",
      title: rule.name,
      body: rule.actionValue || rule.name,
      actions: ["Dismiss"],
      source: "system",
    });
  } else if (rule.action === "lock") {
    useBridge.setState({ macLocked: true, graceUntil: null });
    s.log("info", "automation locked the Mac");
  } else if (rule.action === "sync") {
    const photo = s.files.find((f) => f.folder === "DCIM");
    if (photo) {
      void s.sendPayload("android", "file", photo.name, undefined, photo.size, 4);
    }
    s.log("info", "automation synced photos");
  } else if (rule.action === "send") {
    void s.sendPayload("android", "text", "Automation", rule.actionValue, rule.actionValue.length, 1);
  }
}

function reactTo(prev: BridgeState, next: BridgeState) {
  if (!next.features.automations || !next.paired) return;
  for (const rule of next.rules) {
    if (!rule.enabled) continue;
    const hit =
      (rule.trigger === "wifi" && prev.wifi !== next.wifi && next.wifi === rule.triggerValue) ||
      (rule.trigger === "charging" && !prev.phoneCharging && next.phoneCharging && rule.triggerValue === "start") ||
      (rule.trigger === "bluetooth" && !prev.headphones && next.headphones && (rule.triggerValue === "Headphones" || rule.triggerValue === "on"));
    if (hit) applyRule(() => useBridge.getState(), rule);
  }
}

export const useBridge = create<BridgeState>()(
  persist(
    (set, get) => ({
      hydrated: false,
      tourDismissed: false,
      macSetupDone: false,
      phoneSetupDone: false,
      updateLine: "",
      phones: [
        { id: "and-pixel-01", name: "Pixel", battery: 64, charging: false },
        { id: "and-tab-02", name: "Tablet", battery: 91, charging: true },
      ],
      activePhoneId: "and-pixel-01",
      macs: [
        { id: "mac-book-01", name: "MacBook" },
        { id: "mac-studio-02", name: "Studio" },
      ],
      activeMacId: "mac-book-01",
      ...seeded,
      pin: "",
      pinExpires: 0,
      status: "connected",
      attempt: 0,
      path: "lan",
      manualHost: "",
      fcmEnabled: false,
      latencyMs: 18,
      lastHeartbeat: now(),
      phoneCharging: false,
      lowBatterySent: false,
      wifi: "Home",
      headphones: false,
      phoneNear: true,
      lockDelaySec: 15,
      graceUntil: null,
      macLocked: false,
      ringing: false,
      sharedFolders: ["Documents"],
      timeMarks: {},
      clipMethod: "tile",
      hideSensitive: true,
      retentionDays: 30,
      blockedApps: [],
      phoneScreen: "home",
      systemPage: "root",
      macPane: "home",
      remoteMode: "trackpad",
      slide: 0,
      cursor: { x: 50, y: 46, down: false },
      typedOnMac: "",
      androidBrowser: null,
      macBrowser: null,
      mirrorApproved: false,
      cameraActive: false,
      cameraError: "",
      debug: false,
      logs: [{ id: "log0", ts: DEMO_NOW, level: "info", msg: "Desk ready on the local link." }],
      seenIds: [],
      lastError: "",
      activeThread: "t1",
      macMenuOpen: false,
      theme: "dark",

      log: (level, msg) => {
        const line = { id: id(), ts: now(), level, msg };
        set((s) => ({ logs: [...s.logs, line].slice(-80) }));
      },

      mismatch: () => {
        const s = get();
        return s.paired && (s.seenAndroidFp !== s.pinnedAndroidFp || s.seenMacFp !== s.pinnedMacFp);
      },

      gate: (feature) => {
        const s = get();
        if (!s.paired) return "Pair the two devices first.";
        if (s.mismatch()) return "The peer key changed. Trust it or reject it before sending.";
        if (s.status !== "connected") return "The link is down. It will reconnect on its own.";
        if (feature && !s.features[feature]) return "Turn that feature on first.";
        return null;
      },

      setTheme: (theme) => set({ theme }),
      dismissTour: () => set({ tourDismissed: true }),

      finishMacSetup: () => {
        set({ macSetupDone: true });
        get().log("info", "Mac setup finished. Scan the QR on the phone.");
      },
      finishPhoneSetup: () => {
        set({ phoneSetupDone: true });
        get().log("info", "Phone setup finished.");
      },
      replaySetup: () => set({ macSetupDone: false, phoneSetupDone: false, updateLine: "" }),
      checkUpdate: (offerNewer = false) => {
        const remote = offerNewer ? "1.1.0" : APP_VERSION;
        const newer = compareVersions(APP_VERSION, remote) > 0;
        const updateLine = newer
          ? `BridgeLink ${remote} is ready. On a real Mac or phone this opens the release. This demo does not download it.`
          : `BridgeLink ${APP_VERSION} is the latest.`;
        set({ updateLine });
        get().log("info", updateLine);
      },
      resetDesk: () => {
        get().clearHistory();
        get().forgetDevice();
        set({
          macSetupDone: false,
          phoneSetupDone: false,
          updateLine: "",
          manualHost: "",
        });
        get().log("info", "BridgeLink was reset. Pair again.");
      },

      beginPairing: () => {
        const pin = String(Math.floor(100000 + Math.random() * 900000));
        set({
          paired: false,
          pin,
          pinExpires: now() + 120_000,
          status: "offline",
          lastError: "",
        });
        get().log("info", "Pairing code issued. It expires in 2 minutes.");
      },

      submitPin: (pin) => {
        const s = get();
        if (s.paired) return;
        if (!s.pin || now() > s.pinExpires) {
          set({ lastError: "That code expired. Ask the Mac for a new one." });
          return;
        }
        if (pin.trim() !== s.pin) {
          set({ lastError: "That code does not match." });
          return;
        }
        const androidFp = randomHex(8);
        const macFp = randomHex(8);
        set({
          paired: true,
          status: "connected",
          pin: "",
          pinExpires: 0,
          sharedKey: randomHex(32),
          pinnedAndroidFp: androidFp,
          seenAndroidFp: androidFp,
          pinnedMacFp: macFp,
          seenMacFp: macFp,
          lastError: "",
          lastHeartbeat: now(),
        });
        get().log("info", "Paired. Public keys pinned.");
      },

      forgetDevice: () => {
        if (reconnectTimer) clearTimeout(reconnectTimer);
        set({
          paired: false,
          status: "offline",
          pin: "",
          sharedKey: "",
          macLocked: false,
          ringing: false,
          mirrorApproved: false,
          lastError: "",
        });
        get().beginPairing();
      },

      triggerKeyChange: () => {
        if (!get().paired) return;
        set({ seenAndroidFp: randomHex(8) });
        get().log("warn", "Peer fingerprint changed. Sending is paused.");
      },

      trustKey: () => {
        const s = get();
        set({ pinnedAndroidFp: s.seenAndroidFp, pinnedMacFp: s.seenMacFp, lastError: "" });
        get().log("info", "New peer key pinned.");
      },

      rejectKey: () => {
        const s = get();
        set({ seenAndroidFp: s.pinnedAndroidFp, seenMacFp: s.pinnedMacFp, lastError: "" });
        get().log("warn", "Rejected the new peer key. The previous pin stays.");
      },

      setFeature: (key, on) => set((s) => ({ features: { ...s.features, [key]: on } })),

      setPermission: (side, key, on) =>
        set((s) => ({
          permissions: {
            ...s.permissions,
            [side]: { ...s.permissions[side], [key]: on },
          },
        })),

      setPath: (path) => {
        set({ path });
        get().log("info", path === "lan" ? "Using the local network." : "Using the saved host.");
      },

      setManualHost: (manualHost) => set({ manualHost }),
      setFcm: (fcmEnabled) => {
        set({ fcmEnabled });
        get().log("info", fcmEnabled ? "Wake-up pushes enabled. They carry no message content." : "Wake-up pushes off.");
      },

      heartbeat: () => {
        if (get().status !== "connected") return;
        set({ lastHeartbeat: now(), latencyMs: 12 + Math.floor(Math.random() * 20) });
      },

      dropLink: () => {
        if (!get().paired) return;
        if (reconnectTimer) clearTimeout(reconnectTimer);
        set({ status: "reconnecting", attempt: 0 });
        get().log("warn", "Link dropped.");
        const step = () => {
          const s = get();
          if (s.status !== "reconnecting") return;
          const wait = nextBackoff(s.attempt);
          const shown = Math.min(wait, 700);
          get().log("info", `Reconnect attempt ${s.attempt + 1}. Backoff ${wait} ms, desk waits ${shown} ms.`);
          reconnectTimer = setTimeout(() => {
            const cur = get();
            if (cur.status !== "reconnecting") return;
            if (cur.attempt >= 3) {
              set({ status: "connected", attempt: 0, lastHeartbeat: now(), lastError: "" });
              get().log("info", "Link restored.");
            } else {
              set({ attempt: cur.attempt + 1 });
              step();
            }
          }, shown);
        };
        step();
      },

      sendPayload: async (from, kind, name, text, size, chunks) => {
        const feature: FeatureKey = kind === "link" ? "links" : "transfers";
        const problem = get().gate(kind === "link" && !get().features.transfers ? "links" : feature);
        const linkProblem = kind === "link" ? get().gate("links") : null;
        const transferProblem = kind === "link" ? linkProblem : get().gate("transfers");
        const blocked = kind === "link" ? transferProblem : problem;
        if (blocked) {
          set({ lastError: blocked });
          return null;
        }
        const transferId = id();
        const total = Math.max(1, chunks);
        const transfer: Transfer = {
          id: transferId,
          from,
          to: from === "android" ? "mac" : "android",
          kind,
          name,
          text,
          size,
          progress: 0,
          status: "sending",
          chunks: total,
          chunkDone: 0,
          createdAt: now(),
        };
        set((s) => ({ transfers: [transfer, ...s.transfers].slice(0, 80), lastError: "" }));
        const env: Envelope = {
          v: 1,
          id: transferId,
          type: kind,
          from,
          ts: now(),
          payload: { name, text: text ?? "", size },
        };
        const remembered = rememberId(get().seenIds, env.id);
        if (!remembered.fresh) return transferId;
        set({ seenIds: remembered.seen });
        get().log("info", `${kind} ${name} · ${total} chunk${total === 1 ? "" : "s"} on ${get().path}.`);

        for (let i = 1; i <= total; i++) {
          if (pauses.has(transferId)) {
            pauses.delete(transferId);
            set((s) => ({
              transfers: s.transfers.map((t) =>
                t.id === transferId ? { ...t, status: "paused", chunkDone: i - 1, progress: (i - 1) / total } : t,
              ),
            }));
            return transferId;
          }
          if (get().status !== "connected") {
            set((s) => ({
              transfers: s.transfers.map((t) => (t.id === transferId ? { ...t, status: "paused", chunkDone: i - 1 } : t)),
            }));
            return transferId;
          }
          await new Promise((r) => setTimeout(r, kind === "file" ? 40 : 30));
          set((s) => ({
            transfers: s.transfers.map((t) =>
              t.id === transferId ? { ...t, chunkDone: i, progress: i / total, status: "sending" } : t,
            ),
          }));
        }

        set((s) => ({
          transfers: s.transfers.map((t) => (t.id === transferId ? { ...t, status: "done", progress: 1, chunkDone: total } : t)),
        }));

        if (kind === "link" && text) {
          if (from === "android") set({ macBrowser: text, macPane: "browser" });
          else set({ androidBrowser: text, phoneScreen: "home" });
        }
        if (kind === "file") {
          const folder = from === "android" ? "Mac inbox" : "Downloads";
          set((s) => ({
            files: [{ id: id(), folder, name, size, kind: "file" }, ...s.files],
          }));
        }
        return transferId;
      },

      pauseTransfer: (transferId) => {
        pauses.add(transferId);
      },

      attachFile: (transferId, file) => {
        fileBlobs.set(transferId, file);
      },

      resumeTransfer: (transferId) => {
        const current = get().transfers.find((t) => t.id === transferId);
        if (!current || current.status === "done") return;
        pauses.delete(transferId);
        const file = fileBlobs.get(transferId);
        void (async () => {
          const start = current.chunkDone;
          const total = current.chunks;
          if (file) {
            for (let i = start; i < total; i++) {
              if (pauses.has(transferId)) {
                pauses.delete(transferId);
                set((s) => ({
                  transfers: s.transfers.map((t) =>
                    t.id === transferId ? { ...t, status: "paused", chunkDone: i, progress: i / total } : t,
                  ),
                }));
                return;
              }
              const slice = file.slice(i * CHUNK_SIZE, Math.min(file.size, (i + 1) * CHUNK_SIZE));
              await slice.arrayBuffer();
              set((s) => ({
                transfers: s.transfers.map((t) =>
                  t.id === transferId ? { ...t, status: "sending", chunkDone: i + 1, progress: (i + 1) / total } : t,
                ),
              }));
            }
          } else {
            await get().sendPayload(current.from, current.kind, current.name, current.text, current.size, total - start);
            return;
          }
          set((s) => ({
            transfers: s.transfers.map((t) => (t.id === transferId ? { ...t, status: "done", progress: 1 } : t)),
          }));
        })();
      },

      sendFile: async (from, file) => {
        const chunks = Math.max(1, Math.ceil(file.size / CHUNK_SIZE));
        const transferId = await get().sendPayload(from, "file", file.name, undefined, file.size, chunks);
        if (!transferId) return;
        fileBlobs.set(transferId, file);
        let offset = 0;
        let index = 0;
        while (offset < file.size) {
          if (pauses.has(transferId)) return;
          const slice = file.slice(offset, Math.min(file.size, offset + CHUNK_SIZE));
          await slice.arrayBuffer();
          offset += CHUNK_SIZE;
          index += 1;
          set((s) => ({
            transfers: s.transfers.map((t) =>
              t.id === transferId ? { ...t, chunkDone: index, progress: Math.min(1, offset / file.size) } : t,
            ),
          }));
        }
      },

      pushNotice: (notice) => {
        const problem = get().features.notifications ? null : "Notification mirroring is off.";
        const full: Notice = {
          id: notice.id ?? id(),
          app: notice.app,
          title: notice.title,
          body: notice.body,
          ts: now(),
          dismissed: false,
          pinned: false,
          actions: notice.actions,
          source: notice.source,
        };
        set((s) => ({ notices: [full, ...s.notices].slice(0, 200) }));
        const code = get().features.otp ? extractOtp(`${notice.title} ${notice.body}`) : null;
        if (code) {
          get().copyText(code, "android");
          get().log("info", "Verification code copied to the Mac clipboard.");
        } else if (problem) {
          set({ lastError: problem });
        } else {
          get().log("info", `Mirrored ${notice.app}.`);
        }
        if (notice.source === "sms") {
          get().log("info", "SMS arrived.");
        }
      },

      dismissNotice: (nid) => {
        set((s) => ({ notices: s.notices.map((n) => (n.id === nid ? { ...n, dismissed: true } : n)) }));
        get().log("info", "Notification dismissed on both devices.");
      },

      pinNotice: (nid) =>
        set((s) => ({ notices: s.notices.map((n) => (n.id === nid ? { ...n, pinned: !n.pinned } : n)) })),

      replyNotice: (nid, text) => {
        const trimmed = text.trim();
        if (!trimmed) return;
        set((s) => ({
          notices: s.notices.map((n) => (n.id === nid ? { ...n, reply: trimmed } : n)),
        }));
        get().log("info", "Reply sent back to the phone.");
      },

      blockApp: (app, blocked) =>
        set((s) => ({
          blockedApps: blocked ? [...new Set([...s.blockedApps, app])] : s.blockedApps.filter((a) => a !== app),
        })),

      setPhoneClipboard: (text, echo = true) => {
        set({ phoneClipboard: text });
        const s = get();
        if (
          echo &&
          s.features.clipboard &&
          s.clipMethod === "accessibility" &&
          s.permissions.android.accessibility &&
          s.paired
        ) {
          s.copyText(text, "android");
        }
      },

      setMacClipboard: (text, echo = true) => {
        set({ macClipboard: text });
        const s = get();
        if (echo && s.features.clipboard && s.paired && !s.mismatch() && s.status === "connected") {
          set({ phoneClipboard: text });
          s.copyText(text, "mac");
          get().log("info", "Mac clipboard pushed to the phone.");
        }
      },

      pushClipboard: (from) => {
        const s = get();
        const problem = s.gate("clipboard");
        if (problem) {
          set({ lastError: problem });
          return;
        }
        const text = from === "android" ? s.phoneClipboard : s.macClipboard;
        if (!text.trim()) {
          set({ lastError: "Clipboard is empty." });
          return;
        }
        if (from === "android") set({ macClipboard: text });
        else set({ phoneClipboard: text });
        s.copyText(text, from);
        get().log("info", `Clipboard sent from ${from}.`);
      },

      copyText: (text, from) => {
        const source = from === "android" ? "phone" : "mac";
        const item: ClipItem = {
          id: id(),
          text,
          from,
          ts: now(),
          pinned: false,
          sensitive: looksSensitive(text, source),
        };
        set((s) => {
          const last = s.clips[0];
          if (last && last.text === text && now() - last.ts < 4000) return {};
          const clips = [item, ...s.clips];
          const pinned = clips.filter((c) => c.pinned);
          const rest = clips.filter((c) => !c.pinned).slice(0, Math.max(0, 100 - pinned.length));
          return { clips: [...pinned, ...rest].slice(0, 120), macClipboard: from === "android" ? text : s.macClipboard };
        });
      },

      setClipMethod: (clipMethod) => {
        set({ clipMethod });
        const labels: Record<ClipMethod, string> = {
          tile: "Quick Settings tile",
          share: "Share sheet",
          notification: "Persistent notification",
          bubble: "Floating bubble",
          accessibility: "Accessibility service",
        };
        get().log("info", `Phone clipboard method: ${labels[clipMethod]}.`);
      },

      togglePinClip: (cid) =>
        set((s) => ({ clips: s.clips.map((c) => (c.id === cid ? { ...c, pinned: !c.pinned } : c)) })),

      setMedia: (patch) => {
        if (!get().features.media) {
          set({ lastError: "Media control is off." });
          return;
        }
        set((s) => ({ media: { ...s.media, ...patch } }));
      },

      setPhoneBattery: (n) => {
        const phoneBattery = Math.max(1, Math.min(100, Math.round(n)));
        set((s) => ({
          phoneBattery,
          lowBatterySent: phoneBattery > 20 ? false : s.lowBatterySent,
          phones: s.phones.map((p) => (p.id === s.activePhoneId ? { ...p, battery: phoneBattery } : p)),
        }));
        if (phoneBattery <= 20 && get().features.battery && !get().lowBatterySent) {
          set({ lowBatterySent: true });
          get().pushNotice({
            app: "BridgeLink",
            title: "Phone battery low",
            body: `${phoneBattery}% remaining.`,
            actions: ["Dismiss"],
            source: "system",
          });
        }
      },

      setPhoneCharging: (phoneCharging) => {
        const prev = get();
        set((s) => ({
          phoneCharging,
          phones: s.phones.map((p) => (p.id === s.activePhoneId ? { ...p, charging: phoneCharging } : p)),
        }));
        reactTo(prev, get());
      },

      setMacBattery: (n) => set({ macBattery: Math.max(1, Math.min(100, Math.round(n))) }),

      setWifi: (wifi) => {
        const prev = get();
        set({ wifi });
        reactTo(prev, get());
      },

      setHeadphones: (headphones) => {
        const prev = get();
        set({ headphones });
        reactTo(prev, get());
      },

      setPhoneNear: (phoneNear) => {
        const s = get();
        if (!s.features.proximity) {
          set({ phoneNear, graceUntil: null });
          return;
        }
        if (!phoneNear) {
          set({ phoneNear, graceUntil: now() + s.lockDelaySec * 1000 });
          get().log("info", `Phone left. Mac locks in ${s.lockDelaySec}s. It will not unlock on its own.`);
        } else {
          set({ phoneNear, graceUntil: null });
          get().log("info", "Phone is back. The Mac stays locked until you unlock it.");
        }
      },

      setLockDelay: (lockDelaySec) => set({ lockDelaySec }),

      unlockMac: () => {
        set({ macLocked: false });
        get().log("info", "Mac unlocked manually.");
      },

      tick: () => {
        const s = get();
        if (s.features.proximity && s.paired && !s.phoneNear && s.graceUntil && now() > s.graceUntil && !s.macLocked) {
          set({ macLocked: true, graceUntil: null });
          get().log("info", "Mac locked because the phone left.");
        }
        if (!s.features.automations) return;
        const hm = new Date().toTimeString().slice(0, 5);
        const today = new Date().toDateString();
        for (const rule of s.rules) {
          if (!rule.enabled || rule.trigger !== "time" || rule.triggerValue !== hm) continue;
          if (s.timeMarks[rule.id] === today) continue;
          set((st) => ({ timeMarks: { ...st.timeMarks, [rule.id]: today } }));
          applyRule(get, rule);
        }
      },

      startRing: () => {
        const problem = get().gate("find");
        if (problem) {
          set({ lastError: problem });
          return;
        }
        set({ ringing: true, phoneScreen: "home" });
        get().log("info", "Find phone: alarm stream, full volume, even if silent.");
      },

      stopRing: () => {
        set({ ringing: false });
        get().log("info", "Ringing stopped.");
      },

      setNames: (androidName, macName) =>
        set((s) => {
          const phoneName = androidName.trim() || s.androidName;
          const nextMac = macName.trim() || s.macName;
          return {
            androidName: phoneName,
            macName: nextMac,
            phones: s.phones.map((p) => (p.id === s.activePhoneId ? { ...p, name: phoneName } : p)),
            macs: s.macs.map((m) => (m.id === s.activeMacId ? { ...m, name: nextMac } : m)),
          };
        }),

      switchPhone: (phoneId) => {
        const s = get();
        const phone = s.phones.find((p) => p.id === phoneId);
        if (!phone || phone.id === s.activePhoneId) return;
        const phones = s.phones.map((p) =>
          p.id === s.activePhoneId ? { ...p, battery: s.phoneBattery, charging: s.phoneCharging } : p,
        );
        const next = phones.find((p) => p.id === phoneId) ?? phone;
        set({
          phones,
          activePhoneId: next.id,
          androidId: next.id,
          androidName: next.name,
          phoneBattery: next.battery,
          phoneCharging: next.charging,
          ringing: false,
        });
        get().log("info", `Now linked to ${next.name}. The other phones stay saved.`);
      },

      addPhone: (raw) => {
        const name = raw.trim().slice(0, 40);
        if (!name) return;
        const phone: SavedPhone = { id: id(), name, battery: 80, charging: false };
        set((s) => ({ phones: [...s.phones, phone] }));
        get().switchPhone(phone.id);
      },

      forgetPhone: (phoneId) => {
        const s = get();
        const phone = s.phones.find((p) => p.id === phoneId);
        if (!phone) return;
        const rest = s.phones.filter((p) => p.id !== phoneId);
        if (rest.length === 0) {
          set({ phones: [], activePhoneId: "" });
          get().forgetDevice();
          get().log("info", `Forgot ${phone.name}. No phone is saved.`);
          return;
        }
        set({ phones: rest });
        if (s.activePhoneId === phoneId) get().switchPhone(rest[0].id);
        else get().log("info", `Forgot ${phone.name}. ${get().androidName} stays linked.`);
      },

      switchMac: (macId) => {
        const mac = get().macs.find((m) => m.id === macId);
        if (!mac || mac.id === get().activeMacId) return;
        set({ activeMacId: mac.id, macId: mac.id, macName: mac.name });
        get().log("info", `Phone is now linked to ${mac.name}. The other Macs stay saved.`);
      },

      addMac: (raw) => {
        const name = raw.trim().slice(0, 40);
        if (!name) return;
        const mac: SavedMac = { id: id(), name };
        set((s) => ({ macs: [...s.macs, mac] }));
        get().switchMac(mac.id);
      },

      forgetMac: (macId) => {
        const s = get();
        const mac = s.macs.find((m) => m.id === macId);
        if (!mac) return;
        const rest = s.macs.filter((m) => m.id !== macId);
        if (rest.length === 0) {
          set({ macs: [], activeMacId: "" });
          get().forgetDevice();
          get().log("info", `Forgot ${mac.name}. No Mac is saved.`);
          return;
        }
        set({ macs: rest });
        if (s.activeMacId === macId) get().switchMac(rest[0].id);
        else get().log("info", `Forgot ${mac.name}. ${get().macName} stays linked.`);
      },

      setPhoneScreen: (phoneScreen) => set({ phoneScreen, systemPage: phoneScreen === "system" ? get().systemPage : "root" }),
      setSystemPage: (systemPage) => set({ systemPage, phoneScreen: "system" }),
      setMacPane: (macPane) => set({ macPane, macMenuOpen: false }),
      setRemoteMode: (remoteMode) => set({ remoteMode }),
      setCursor: (x, y, down) =>
        set((s) => ({ cursor: { x: Math.max(4, Math.min(96, x)), y: Math.max(8, Math.min(92, y)), down: down ?? s.cursor.down } })),
      typeOnMac: (typedOnMac) => set({ typedOnMac }),
      nudgeSlide: (dir) => set((s) => ({ slide: Math.max(0, Math.min(4, s.slide + dir)), remoteMode: "slides" })),
      closeBrowser: (side) => (side === "android" ? set({ androidBrowser: null }) : set({ macBrowser: null, macPane: "home" })),
      approveMirror: (mirrorApproved) => {
        set({ mirrorApproved });
        get().log("info", mirrorApproved ? "Screen capture approved for this session." : "Screen capture ended.");
      },
      setCamera: (cameraActive, cameraError = "") => set({ cameraActive, cameraError }),
      setActiveThread: (activeThread) => set({ activeThread }),

      sendSms: (threadId, body, from) => {
        if (from === "me") {
          const problem = get().gate("sms");
          if (problem) {
            set({ lastError: problem });
            return;
          }
          if (!get().permissions.android.sms) {
            set({ lastError: "Allow SMS on the phone first." });
            return;
          }
        }
        const message: SmsMessage = { id: id(), threadId, from, body, ts: now() };
        set((s) => ({
          threads: s.threads.map((t) => (t.id === threadId ? { ...t, messages: [...t.messages, message] } : t)),
        }));
        if (from === "them") {
          const thread = get().threads.find((t) => t.id === threadId);
          get().pushNotice({
            app: "Messages",
            title: thread?.name ?? "SMS",
            body,
            actions: ["Reply"],
            source: "sms",
          });
        } else {
          get().log("info", "SMS reply sent from the Mac.");
        }
      },

      toggleFolder: (folder) =>
        set((s) => ({
          sharedFolders: s.sharedFolders.includes(folder)
            ? s.sharedFolders.filter((f) => f !== folder)
            : [...s.sharedFolders, folder],
        })),

      addRule: (rule) => set((s) => ({ rules: [...s.rules, { ...rule, id: id() }] })),
      toggleRule: (rid, enabled) =>
        set((s) => ({ rules: s.rules.map((r) => (r.id === rid ? { ...r, enabled } : r)) })),
      removeRule: (rid) => set((s) => ({ rules: s.rules.filter((r) => r.id !== rid) })),
      setRetention: (retentionDays) => set({ retentionDays }),
      setHideSensitive: (hideSensitive) => set({ hideSensitive }),
      setDebug: (debug) => set({ debug }),

      clearHistory: () =>
        set({
          notices: [],
          clips: [],
          transfers: [],
          logs: [{ id: id(), ts: now(), level: "info", msg: "History cleared on this desk." }],
          seenIds: [],
          androidBrowser: null,
          macBrowser: null,
          typedOnMac: "",
        }),

      purgeExpired: () => {
        const days = get().retentionDays;
        set((s) => ({
          notices: s.notices.filter((n) => n.pinned || within(n.ts, days)),
          clips: s.clips.filter((c) => c.pinned || within(c.ts, days)),
          transfers: s.transfers.filter((t) => within(t.createdAt, days)),
        }));
      },

      exportDiagnostics: () => {
        const s = get();
        return JSON.stringify(
          {
            app: APP_NAME,
            version: APP_VERSION,
            paired: s.paired,
            status: s.status,
            path: s.path,
            manualHostSet: Boolean(s.manualHost),
            fcmEnabled: s.fcmEnabled,
            features: s.features,
            permissions: s.permissions,
            counts: {
              notices: s.notices.length,
              clips: s.clips.length,
              transfers: s.transfers.length,
              threads: s.threads.length,
            },
            fingerprintPinned: s.pinnedAndroidFp === s.seenAndroidFp,
            logs: s.logs.map((l) => ({ t: l.ts, level: l.level, msg: l.msg })),
          },
          null,
          2,
        );
      },

      setMenu: (macMenuOpen) => set({ macMenuOpen }),
    }),
    {
      name: "bridgelink",
      storage: createJSONStorage(() => localStorage),
      skipHydration: true,
      partialize: (s) => {
        const {
          hydrated: _h,
          mirrorApproved: _m,
          cameraActive: _c,
          cameraError: _e,
          macMenuOpen: _o,
          ringing: _r,
          cursor: _cu,
          ...rest
        } = s;
        void _h;
        void _m;
        void _c;
        void _e;
        void _o;
        void _r;
        void _cu;
        return rest as BridgeState;
      },
    },
  ),
);
