import { useEffect, useRef, useState, type PointerEvent } from "react";
import {
  ChevronLeft,
  ClipboardCopy,
  House,
  Inbox,
  Pointer,
  Send,
  SlidersHorizontal,
} from "lucide-react";
import { bindVideo, ensureCamera, releaseCamera, unbindVideo } from "@/lib/bridgelink/camera";
import { pairingGrid } from "@/lib/bridgelink/protocol";
import { useBridge, type ClipMethod, type SystemPage, TRACKS } from "@/lib/bridgelink/store";
import { Ask, Btn, ClockLabel, Empty, Field, KeyWall, Pill, Progress, SLIDES, ago, formatBytes } from "./ui";

const NAV = [
  { id: "home", label: "Home", icon: House },
  { id: "send", label: "Send", icon: Send },
  { id: "inbox", label: "Inbox", icon: Inbox },
  { id: "remote", label: "Remote", icon: Pointer },
  { id: "system", label: "System", icon: SlidersHorizontal },
] as const;

export function PhonePanel({ clock }: { clock: number }) {
  const paired = useBridge((s) => s.paired);
  return (
    <section aria-label="Android phone" className="min-w-0">
      <p className="mb-2 text-xs font-medium tracking-wide text-subtle uppercase">Android</p>
      <div className="rounded-3xl border border-line bg-inset p-2">
        <div className="relative flex h-[calc(100dvh-10.5rem)] min-h-[560px] flex-col overflow-hidden rounded-2xl bg-bg lg:h-[740px]">
          {paired ? <PhoneApp clock={clock} /> : <PairPhone clock={clock} />}
          <KeyWall />
        </div>
      </div>
    </section>
  );
}

function StatusBar({ clock }: { clock: number }) {
  const battery = useBridge((s) => s.phoneBattery);
  const charging = useBridge((s) => s.phoneCharging);
  return (
    <div className="flex h-10 shrink-0 items-center justify-between px-4 text-xs text-muted tabular-nums">
      <ClockLabel clock={clock} />
      <span>
        {battery}%{charging ? " · charging" : ""}
      </span>
    </div>
  );
}

function PhoneApp({ clock }: { clock: number }) {
  const screen = useBridge((s) => s.phoneScreen);
  const setScreen = useBridge((s) => s.setPhoneScreen);
  const ringing = useBridge((s) => s.ringing);
  const stopRing = useBridge((s) => s.stopRing);
  const method = useBridge((s) => s.clipMethod);
  const overlay = useBridge((s) => s.permissions.android.overlay);
  const push = useBridge((s) => s.pushClipboard);
  const notices = useBridge((s) => s.notices);
  const badge = notices.filter((n) => !n.dismissed).length;
  const browser = useBridge((s) => s.androidBrowser);
  const closeBrowser = useBridge((s) => s.closeBrowser);

  return (
    <div className={ringing ? "phone-flash flex min-h-0 flex-1 flex-col" : "flex min-h-0 flex-1 flex-col"}>
      <StatusBar clock={clock} />
      {browser && (
        <div className="mx-3 mb-2 flex items-center gap-2 rounded-md border border-line bg-panel px-3 py-2">
          <p className="min-w-0 flex-1 truncate text-xs text-muted">{browser}</p>
          <button className="text-xs text-fg" onClick={() => closeBrowser("android")}>
            Close
          </button>
        </div>
      )}
      <div className="scroll-thin min-h-0 flex-1 overflow-y-auto px-3 pb-3">
        {screen === "home" && <Home />}
        {screen === "send" && <SendScreen />}
        {screen === "inbox" && <InboxScreen />}
        {screen === "remote" && <RemoteScreen />}
        {screen === "system" && <SystemScreen />}
      </div>
      {method === "notification" && (
        <button className="mx-3 mb-2 flex h-11 items-center justify-between rounded-md border border-line bg-panel px-3 text-left text-sm" onClick={() => push("android")}>
          <span>BridgeLink</span>
          <span className="text-muted">Send clipboard</span>
        </button>
      )}
      {ringing && (
        <div className="absolute inset-0 z-10 flex flex-col items-center justify-center bg-bg/90 px-6 text-center">
          <p className="text-lg font-medium">Phone is ringing</p>
          <p className="mt-2 text-sm text-muted">Alarm stream, full volume, even if the ringer is off.</p>
          <Btn variant="primary" className="mt-5 w-full max-w-xs" onClick={stopRing}>
            Stop ringing
          </Btn>
        </div>
      )}
      {method === "bubble" && overlay && (
        <button
          className="absolute right-3 bottom-20 z-10 flex size-14 items-center justify-center rounded-full border border-line bg-accent text-accent-fg shadow-none"
          aria-label="Send clipboard"
          onClick={() => push("android")}
        >
          <ClipboardCopy className="size-5" />
        </button>
      )}
      <nav className="grid shrink-0 grid-cols-5 border-t border-line" aria-label="Phone sections">
        {NAV.map((item) => {
          const Icon = item.icon;
          const on = screen === item.id;
          return (
            <button
              key={item.id}
              className={on ? "flex h-14 flex-col items-center justify-center gap-0.5 text-fg" : "flex h-14 flex-col items-center justify-center gap-0.5 text-subtle"}
              onClick={() => setScreen(item.id)}
              aria-current={on ? "page" : undefined}
            >
              <span className="relative">
                <Icon className="size-5" strokeWidth={1.75} />
                {item.id === "inbox" && badge > 0 && (
                  <span className="absolute -top-1 -right-2 min-w-4 rounded-full bg-accent px-1 text-center text-[10px] font-medium text-accent-fg tabular-nums">
                    {badge}
                  </span>
                )}
              </span>
              <span className="text-[11px]">{item.label}</span>
            </button>
          );
        })}
      </nav>
    </div>
  );
}

function Home() {
  const s = useBridge();
  const [note, setNote] = useState("");
  return (
    <div className="space-y-3">
      <div className="rounded-lg border border-line bg-panel p-4">
        <div className="flex items-start justify-between gap-3">
          <div>
            <p className="text-xs text-muted">Linked to</p>
            <p className="text-lg font-medium tracking-tight">{s.macName}</p>
          </div>
          <Pill tone={s.status === "connected" ? "ok" : "warn"}>{s.status === "connected" ? "Linked" : "Reconnecting"}</Pill>
        </div>
        <p className="mt-2 text-sm text-muted tabular-nums">
          {s.path === "lan" ? "Local network" : s.path === "manual" ? s.manualHost || "Saved host" : "Encrypted relay"} · {s.latencyMs} ms
        </p>
        <p className="mt-3 text-sm">
          Mac battery <span className="tabular-nums">{s.macBattery}%</span>
        </p>
      </div>

      {s.features.media && (
        <div className="rounded-lg border border-line bg-panel p-4">
          <p className="text-xs text-muted">Now playing</p>
          <p className="mt-1 font-medium">{s.media.title}</p>
          <p className="text-sm text-muted">{s.media.artist}</p>
          <div className="mt-3 flex gap-2">
            <Btn onClick={() => s.setMedia({ ...TRACKS[(s.media.index + TRACKS.length - 1) % TRACKS.length], index: (s.media.index + TRACKS.length - 1) % TRACKS.length, playing: true })}>Prev</Btn>
            <Btn variant="primary" onClick={() => s.setMedia({ playing: !s.media.playing })}>
              {s.media.playing ? "Pause" : "Play"}
            </Btn>
            <Btn onClick={() => s.setMedia({ ...TRACKS[(s.media.index + 1) % TRACKS.length], index: (s.media.index + 1) % TRACKS.length, playing: true })}>Next</Btn>
          </div>
        </div>
      )}

      <div className="grid grid-cols-2 gap-2">
        <Btn variant="primary" onClick={() => s.pushClipboard("android")}>
          Send clipboard
        </Btn>
        <Btn onClick={() => s.setPhoneScreen("send")}>New note</Btn>
      </div>

      <Field label="Quick note to the Mac" value={note} onChange={setNote} placeholder="Type a note or a link" />
      <Btn
        className="w-full"
        onClick={() => {
          const text = note.trim();
          if (!text) return;
          const link = /^https?:\/\//i.test(text);
          void s.sendPayload("android", link ? "link" : "text", link ? text : "Note", text, text.length, Math.max(1, Math.ceil(text.length / 64)));
          setNote("");
        }}
      >
        Send
      </Btn>
      {s.lastError && (
        <p className="text-sm text-danger" role="alert">
          {s.lastError}
        </p>
      )}
      <Latest />
    </div>
  );
}

function Latest() {
  const transfers = useBridge((s) => s.transfers);
  const item = transfers[0];
  if (!item) return null;
  return (
    <div className="rounded-lg border border-line px-3 py-3">
      <div className="flex items-center justify-between gap-2 text-sm">
        <span className="truncate">{item.name}</span>
        <span className="text-muted">{item.status}</span>
      </div>
      <div className="mt-2">
        <Progress value={item.progress} />
      </div>
      <p className="mt-1 text-xs text-subtle">
        {item.chunkDone}/{item.chunks} chunks · {item.from === "android" ? "to Mac" : "to phone"}
      </p>
    </div>
  );
}

function SendScreen() {
  const s = useBridge();
  const [text, setText] = useState("");
  const [sheet, setSheet] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);
  const sendText = () => {
    const value = text.trim();
    if (!value) return;
    const link = /^https?:\/\//i.test(value);
    void s.sendPayload("android", link ? "link" : "text", link ? value : "Note", value, value.length, Math.max(1, Math.ceil(value.length / 64)));
    setText("");
    setSheet(false);
  };
  return (
    <div className="space-y-3">
      <h2 className="text-lg font-medium tracking-tight">Send to {s.macName}</h2>
      <Field label="Note or link" value={text} onChange={setText} placeholder="https:// or a note" multiline />
      <div className="grid grid-cols-2 gap-2">
        <Btn variant="primary" onClick={sendText}>
          Send
        </Btn>
        <Btn onClick={() => setSheet(true)}>Share sheet</Btn>
      </div>
      <Btn className="w-full" onClick={() => fileRef.current?.click()}>
        Choose a file
      </Btn>
      <input
        ref={fileRef}
        type="file"
        className="sr-only"
        onChange={(e) => {
          const file = e.target.files?.[0];
          if (file) void s.sendFile("android", file);
          e.target.value = "";
        }}
      />
      {s.clipMethod === "share" && (
        <Btn className="w-full" onClick={() => s.pushClipboard("android")}>
          Share clipboard to Mac
        </Btn>
      )}
      {s.lastError && (
        <p className="text-sm text-danger" role="alert">
          {s.lastError}
        </p>
      )}
      <TransferList />
      {sheet && (
        <Ask
          title="Share to Mac"
          body="This is the Android share target. The note goes to the paired Mac only."
          confirm="Share to Mac"
          onConfirm={sendText}
          onClose={() => setSheet(false)}
        />
      )}
    </div>
  );
}

function TransferList() {
  const transfers = useBridge((s) => s.transfers);
  const pause = useBridge((s) => s.pauseTransfer);
  const resume = useBridge((s) => s.resumeTransfer);
  if (transfers.length === 0) return <Empty title="No transfers yet" body="Send a note, link, or file." />;
  return (
    <ul className="space-y-2">
      {transfers.slice(0, 8).map((t) => (
        <li key={t.id} className="rounded-lg border border-line px-3 py-3">
          <div className="flex items-center justify-between gap-2 text-sm">
            <span className="truncate font-medium">{t.name}</span>
            <span className="text-muted">{formatBytes(t.size)}</span>
          </div>
          <div className="mt-2">
            <Progress value={t.progress} />
          </div>
          <div className="mt-2 flex items-center justify-between text-xs text-subtle">
            <span>
              {t.status} · {t.chunkDone}/{t.chunks}
            </span>
            {t.status === "sending" && (
              <button className="text-fg" onClick={() => pause(t.id)}>
                Pause
              </button>
            )}
            {t.status === "paused" && (
              <button className="text-fg" onClick={() => resume(t.id)}>
                Resume
              </button>
            )}
          </div>
        </li>
      ))}
    </ul>
  );
}

function InboxScreen() {
  const notices = useBridge((s) => s.notices);
  const dismiss = useBridge((s) => s.dismissNotice);
  const pin = useBridge((s) => s.pinNotice);
  const reply = useBridge((s) => s.replyNotice);
  const block = useBridge((s) => s.blockApp);
  const blocked = useBridge((s) => s.blockedApps);
  const copy = useBridge((s) => s.copyText);
  const [q, setQ] = useState("");
  const [filter, setFilter] = useState<"all" | "otp" | "sms" | "notification">("all");
  const [draft, setDraft] = useState("");
  const [open, setOpen] = useState<string | null>(null);
  const items = notices.filter((n) => {
    if (filter !== "all" && n.source !== filter) return false;
    const blob = `${n.app} ${n.title} ${n.body}`.toLowerCase();
    return blob.includes(q.toLowerCase());
  });
  return (
    <div className="space-y-3">
      <h2 className="text-lg font-medium tracking-tight">Inbox</h2>
      <input
        value={q}
        onChange={(e) => setQ(e.target.value)}
        placeholder="Search alerts, codes, messages"
        className="h-11 w-full rounded-sm border border-line bg-inset px-3 text-sm outline-none"
        aria-label="Search inbox"
      />
      <div className="flex gap-2 overflow-x-auto">
        {(["all", "notification", "otp", "sms"] as const).map((f) => (
          <button
            key={f}
            className={filter === f ? "h-9 shrink-0 rounded-full bg-accent px-3 text-sm text-accent-fg" : "h-9 shrink-0 rounded-full border border-line px-3 text-sm text-muted"}
            onClick={() => setFilter(f)}
          >
            {f === "all" ? "All" : f === "notification" ? "Alerts" : f === "otp" ? "Codes" : "SMS"}
          </button>
        ))}
      </div>
      {items.length === 0 && <Empty title="Nothing matches" body="Alerts you mirror, codes, and texts show up here." />}
      <ul className="space-y-2">
        {items.map((n) => (
          <li key={n.id} className={n.dismissed ? "rounded-lg border border-line px-3 py-3 opacity-50" : "rounded-lg border border-line bg-panel px-3 py-3"}>
            <div className="flex items-center justify-between gap-2">
              <p className="text-sm font-medium">
                {n.app} · {n.title}
              </p>
              <span className="text-xs text-subtle">{ago(n.ts)}</span>
            </div>
            <p className="mt-1 text-sm text-muted">{n.body}</p>
            {n.reply && <p className="mt-2 text-sm">Reply: {n.reply}</p>}
            <div className="mt-2 flex flex-wrap gap-2">
              {n.source === "otp" && (
                <Btn className="h-9" onClick={() => copy(n.body.match(/\d{4,8}/)?.[0] ?? n.body, "android")}>
                  Copy code
                </Btn>
              )}
              <Btn className="h-9" onClick={() => pin(n.id)}>
                {n.pinned ? "Unpin" : "Pin"}
              </Btn>
              {!n.dismissed && (
                <Btn className="h-9" onClick={() => dismiss(n.id)}>
                  Dismiss
                </Btn>
              )}
              <Btn className="h-9" onClick={() => block(n.app, !blocked.includes(n.app))}>
                {blocked.includes(n.app) ? "Unblock" : "Block app"}
              </Btn>
              <Btn className="h-9" onClick={() => setOpen(open === n.id ? null : n.id)}>
                Reply
              </Btn>
            </div>
            {open === n.id && (
              <form
                className="mt-2 flex gap-2"
                onSubmit={(e) => {
                  e.preventDefault();
                  reply(n.id, draft);
                  setDraft("");
                  setOpen(null);
                }}
              >
                <input value={draft} onChange={(e) => setDraft(e.target.value)} className="h-11 min-w-0 flex-1 rounded-sm border border-line bg-inset px-3 text-sm" aria-label="Reply" />
                <Btn variant="primary" type="submit">
                  Send
                </Btn>
              </form>
            )}
          </li>
        ))}
      </ul>
      <Compose />
    </div>
  );
}

function Compose() {
  const push = useBridge((s) => s.pushNotice);
  const [app, setApp] = useState("Maps");
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  return (
    <form
      className="space-y-2 rounded-lg border border-line p-3"
      onSubmit={(e) => {
        e.preventDefault();
        if (!title.trim() && !body.trim()) return;
        push({ app, title: title || "Alert", body, actions: ["Reply", "Dismiss"], source: "notification" });
        setTitle("");
        setBody("");
      }}
    >
      <p className="text-sm font-medium">Mirror a new alert</p>
      <input value={app} onChange={(e) => setApp(e.target.value)} className="h-11 w-full rounded-sm border border-line bg-inset px-3 text-sm" aria-label="App name" />
      <input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Title" className="h-11 w-full rounded-sm border border-line bg-inset px-3 text-sm" />
      <input value={body} onChange={(e) => setBody(e.target.value)} placeholder="Text. Include a code to test auto-copy." className="h-11 w-full rounded-sm border border-line bg-inset px-3 text-sm" />
      <Btn variant="primary" type="submit" className="w-full">
        Show on Mac
      </Btn>
    </form>
  );
}

function RemoteScreen() {
  const s = useBridge();
  const [ask, setAsk] = useState(false);
  const pad = useRef<HTMLDivElement>(null);
  const move = (e: PointerEvent<HTMLDivElement>) => {
    const rect = pad.current?.getBoundingClientRect();
    if (!rect) return;
    s.setCursor(((e.clientX - rect.left) / rect.width) * 100, ((e.clientY - rect.top) / rect.height) * 100, e.buttons > 0 || e.type === "pointerdown");
  };
  if (!s.features.input) {
    return (
      <div className="space-y-3">
        <h2 className="text-lg font-medium">Remote input is off</h2>
        <p className="text-sm leading-relaxed text-muted">
          The Mac needs Accessibility permission so BridgeLink can move the pointer, type, and change slides. This does not grant it silently.
        </p>
        <Btn
          variant="primary"
          onClick={() => setAsk(true)}
        >
          Allow remote input
        </Btn>
        {ask && (
          <Ask
            title="Accessibility on the Mac"
            body="BridgeLink uses this to send clicks, drags, scrolling, and keystrokes. You can turn it off any time in System."
            confirm="Allow"
            onConfirm={() => {
              s.setPermission("mac", "accessibility", true);
              s.setFeature("input", true);
              setAsk(false);
            }}
            onClose={() => setAsk(false)}
          />
        )}
      </div>
    );
  }
  return (
    <div className="space-y-3">
      <div className="grid grid-cols-3 gap-2">
        {(["trackpad", "keyboard", "slides"] as const).map((m) => (
          <button
            key={m}
            className={s.remoteMode === m ? "h-11 rounded-sm bg-accent text-sm text-accent-fg" : "h-11 rounded-sm border border-line text-sm"}
            onClick={() => s.setRemoteMode(m)}
          >
            {m === "trackpad" ? "Trackpad" : m === "keyboard" ? "Keyboard" : "Slides"}
          </button>
        ))}
      </div>
      {s.remoteMode === "trackpad" && (
        <div
          ref={pad}
          role="application"
          aria-label="Trackpad"
          className="relative h-64 touch-none rounded-lg border border-line bg-inset"
          onPointerDown={(e) => {
            (e.currentTarget as HTMLDivElement).setPointerCapture(e.pointerId);
            move(e);
          }}
          onPointerMove={(e) => {
            if (e.buttons) move(e);
          }}
          onPointerUp={() => s.setCursor(s.cursor.x, s.cursor.y, false)}
        >
          <span className="absolute top-3 left-3 text-xs text-subtle">Drag to move the Mac pointer</span>
        </div>
      )}
      {s.remoteMode === "keyboard" && (
        <Field label="Type on the Mac" value={s.typedOnMac} onChange={s.typeOnMac} placeholder="Text appears on the Mac" multiline />
      )}
      {s.remoteMode === "slides" && (
        <div className="space-y-3">
          <div className="rounded-lg border border-line bg-panel px-4 py-6">
            <p className="text-xs text-muted">Slide {s.slide + 1} of {SLIDES.length}</p>
            <p className="mt-2 text-xl font-medium tracking-tight">{SLIDES[s.slide][0]}</p>
            <p className="mt-1 text-sm text-muted">{SLIDES[s.slide][1]}</p>
          </div>
          <div className="grid grid-cols-2 gap-2">
            <Btn onClick={() => s.nudgeSlide(-1)}>Previous</Btn>
            <Btn variant="primary" onClick={() => s.nudgeSlide(1)}>
              Next
            </Btn>
          </div>
        </div>
      )}
      {s.features.media && (
        <label className="block text-sm text-muted">
          Phone volume {Math.round(s.media.volume * 100)}
          <input
            type="range"
            min={0}
            max={100}
            value={Math.round(s.media.volume * 100)}
            onChange={(e) => s.setMedia({ volume: Number(e.target.value) / 100 })}
            className="mt-2 w-full"
          />
        </label>
      )}
    </div>
  );
}

const PAGES: { id: SystemPage; label: string; body: string }[] = [
  { id: "conditions", label: "Phone conditions", body: "Battery, Wi-Fi, charging, nearby" },
  { id: "clipboard", label: "Clipboard", body: "How the phone sends what you copy" },
  { id: "permissions", label: "Permissions", body: "Asked one at a time, with a reason" },
  { id: "files", label: "Shared folders", body: "What the Mac is allowed to browse" },
  { id: "scan", label: "Scan to Mac", body: "Clean a page and send it" },
  { id: "mirror", label: "Screen mirror", body: "Approve capture for this session" },
  { id: "camera", label: "Camera", body: "Preview on the Mac" },
  { id: "automations", label: "Automations", body: "Small rules you can actually run" },
  { id: "settings", label: "Settings", body: "Link, keys, history, diagnostics" },
];

function SystemScreen() {
  const page = useBridge((s) => s.systemPage);
  const setPage = useBridge((s) => s.setSystemPage);
  if (page === "root") {
    return (
      <div className="space-y-2">
        <h2 className="px-1 text-lg font-medium tracking-tight">System</h2>
        {PAGES.map((p) => (
          <button key={p.id} className="flex w-full items-center justify-between rounded-lg border border-line px-3 py-3 text-left" onClick={() => setPage(p.id)}>
            <span>
              <span className="block text-sm font-medium">{p.label}</span>
              <span className="block text-sm text-muted">{p.body}</span>
            </span>
            <ChevronLeft className="size-4 rotate-180 text-subtle" />
          </button>
        ))}
      </div>
    );
  }
  return (
    <div className="space-y-3">
      <button className="flex h-11 items-center gap-1 text-sm text-muted" onClick={() => setPage("root")}>
        <ChevronLeft className="size-4" /> System
      </button>
      {page === "conditions" && <Conditions />}
      {page === "clipboard" && <ClipboardPage />}
      {page === "permissions" && <PermissionsPage />}
      {page === "files" && <FilesPage />}
      {page === "scan" && <ScanPage />}
      {page === "mirror" && <MirrorPage />}
      {page === "camera" && <CameraPage />}
      {page === "automations" && <RulesPage />}
      {page === "settings" && <SettingsPage />}
    </div>
  );
}

function Conditions() {
  const s = useBridge();
  return (
    <div className="space-y-4">
      <h2 className="text-lg font-medium">Conditions</h2>
      <label className="block text-sm">
        Phone battery {s.phoneBattery}%
        <input className="mt-2 w-full" type="range" min={1} max={100} value={s.phoneBattery} onChange={(e) => s.setPhoneBattery(Number(e.target.value))} />
      </label>
      <label className="block text-sm">
        Mac battery {s.macBattery}%
        <input className="mt-2 w-full" type="range" min={1} max={100} value={s.macBattery} onChange={(e) => s.setMacBattery(Number(e.target.value))} />
      </label>
      <label className="flex h-11 items-center justify-between text-sm">
        Charging
        <input type="checkbox" checked={s.phoneCharging} onChange={(e) => s.setPhoneCharging(e.target.checked)} />
      </label>
      <label className="block text-sm">
        Wi-Fi
        <select className="mt-2 h-11 w-full rounded-sm border border-line bg-inset px-3" value={s.wifi} onChange={(e) => s.setWifi(e.target.value)}>
          {["Home", "Cafe", "Off"].map((w) => (
            <option key={w}>{w}</option>
          ))}
        </select>
      </label>
      <label className="flex h-11 items-center justify-between text-sm">
        Headphones
        <input type="checkbox" checked={s.headphones} onChange={(e) => s.setHeadphones(e.target.checked)} />
      </label>
      <label className="flex items-center justify-between gap-3 text-sm">
        <span>Phone is with the Mac</span>
        <input type="checkbox" checked={s.phoneNear} onChange={(e) => s.setPhoneNear(e.target.checked)} />
      </label>
      <p className="text-sm text-muted">Moving away can lock the Mac after a delay. It never unlocks the Mac.</p>
    </div>
  );
}

const METHODS: { id: ClipMethod; label: string; body: string }[] = [
  { id: "tile", label: "Quick Settings tile", body: "The Home button sends the clipboard. Active when this is selected." },
  { id: "share", label: "Share sheet", body: "Share clipboard to Mac from the Send tab." },
  { id: "notification", label: "Persistent notification", body: "A quiet bar with a send action, while the service is running." },
  { id: "bubble", label: "Floating bubble", body: "Needs the overlay permission. Tap the bubble to send." },
  { id: "accessibility", label: "Accessibility service", body: "Optional. Sends when the phone clipboard field changes. Off unless you opt in." },
];

function ClipboardPage() {
  const s = useBridge();
  const [ask, setAsk] = useState<ClipMethod | null>(null);
  const pick = (method: ClipMethod) => {
    if (method === "bubble" && !s.permissions.android.overlay) return setAsk(method);
    if (method === "accessibility" && !s.permissions.android.accessibility) return setAsk(method);
    s.setClipMethod(method);
    if (!s.features.clipboard) s.setFeature("clipboard", true);
  };
  return (
    <div className="space-y-3">
      <h2 className="text-lg font-medium">Clipboard</h2>
      <p className="text-sm leading-relaxed text-muted">
        Android does not let apps read the clipboard in the background. The active method is{" "}
        <span className="text-fg">{METHODS.find((m) => m.id === s.clipMethod)?.label}</span>. Mac to phone is automatic while clipboard sync is on.
      </p>
      <Field label="Phone clipboard" value={s.phoneClipboard} onChange={(v) => s.setPhoneClipboard(v)} multiline />
      <Btn onClick={() => s.pushClipboard("android")}>Send clipboard now</Btn>
      <ul className="space-y-2">
        {METHODS.map((m) => (
          <li key={m.id}>
            <button className={s.clipMethod === m.id ? "w-full rounded-lg border border-line-strong px-3 py-3 text-left" : "w-full rounded-lg border border-line px-3 py-3 text-left"} onClick={() => pick(m.id)}>
              <span className="block text-sm font-medium">{m.label}</span>
              <span className="mt-1 block text-sm text-muted">{m.body}</span>
            </button>
          </li>
        ))}
      </ul>
      {ask && (
        <Ask
          title={ask === "bubble" ? "Show over other apps" : "Accessibility service"}
          body={
            ask === "bubble"
              ? "The bubble sits on top of other apps and only sends the clipboard when you tap it."
              : "This optional service watches the clipboard field inside BridgeLink. It is never required for the other four methods."
          }
          confirm="Allow"
          onConfirm={() => {
            if (ask === "bubble") s.setPermission("android", "overlay", true);
            else s.setPermission("android", "accessibility", true);
            s.setClipMethod(ask);
            s.setFeature("clipboard", true);
            setAsk(null);
          }}
          onClose={() => setAsk(null)}
        />
      )}
    </div>
  );
}

function PermissionsPage() {
  const s = useBridge();
  const rows: { side: "android" | "mac"; key: string; label: string; body: string }[] = [
    { side: "android", key: "notifications", label: "Notifications", body: "Mirror alerts, and post the BridgeLink foreground notice on Android 13+." },
    { side: "android", key: "sms", label: "SMS", body: "Read and reply from the Mac. Play Store builds often cannot hold this permission, so the APK is the path that can." },
    { side: "android", key: "contacts", label: "Contacts", body: "Show names on message threads. Optional." },
    { side: "android", key: "battery", label: "Battery exemption", body: "Keeps the foreground service alive. Xiaomi, Oppo, Vivo, Samsung, OnePlus, and Huawei also need their own autostart toggle." },
    { side: "android", key: "storage", label: "Files", body: "Share only the folders you pick. Scoped storage still applies." },
    { side: "android", key: "camera", label: "Camera", body: "Scan pages and stream a preview." },
    { side: "android", key: "mic", label: "Microphone", body: "Send mic audio with the camera preview." },
    { side: "mac", key: "notifications", label: "Mac notifications", body: "Show mirrored alerts as normal Mac notifications." },
    { side: "mac", key: "files", label: "Files and folders", body: "Save scans and transfers where you choose." },
  ];
  return (
    <div className="space-y-3">
      <h2 className="text-lg font-medium">Permissions</h2>
      <p className="text-sm leading-relaxed text-muted">
        Each one is off until you allow it. Phone makers kill background apps. After allowing the battery exemption, also lock BridgeLink in the recent-apps list and allow autostart. Brand-specific steps live at dontkillmyapp.com — the in-app rule is the same: a foreground service, a visible notification, and no silent setting changes.
      </p>
      {rows.map((r) => {
        const on = (s.permissions[r.side] as Record<string, boolean>)[r.key];
        return (
          <label key={r.label} className="flex items-start justify-between gap-3 rounded-lg border border-line px-3 py-3">
            <span>
              <span className="block text-sm font-medium">{r.label}</span>
              <span className="mt-1 block text-sm text-muted">{r.body}</span>
            </span>
            <input
              type="checkbox"
              className="mt-1 size-5"
              checked={Boolean(on)}
              onChange={(e) => s.setPermission(r.side, r.key, e.target.checked)}
            />
          </label>
        );
      })}
    </div>
  );
}

function FilesPage() {
  const s = useBridge();
  const [ask, setAsk] = useState(false);
  const folders = ["DCIM", "Documents", "Downloads", "Mac inbox"];
  if (!s.features.files) {
    return (
      <div className="space-y-3">
        <h2 className="text-lg font-medium">Shared folders are off</h2>
        <p className="text-sm text-muted">The Mac can browse only folders you mark, and only while you are paired. It is not open to the rest of the network.</p>
        <Btn variant="primary" onClick={() => setAsk(true)}>
          Share folders
        </Btn>
        {ask && (
          <Ask
            title="Let BridgeLink see chosen folders"
            body="This uses the storage permission. Unchecked folders stay private. The Mac mounts nothing until you press Mount on that side."
            confirm="Allow files"
            onConfirm={() => {
              s.setPermission("android", "storage", true);
              s.setPermission("mac", "files", true);
              s.setFeature("files", true);
              setAsk(false);
            }}
            onClose={() => setAsk(false)}
          />
        )}
      </div>
    );
  }
  return (
    <div className="space-y-3">
      <h2 className="text-lg font-medium">Shared folders</h2>
      {folders.map((folder) => (
        <label key={folder} className="flex h-11 items-center justify-between text-sm">
          {folder}
          <input type="checkbox" checked={s.sharedFolders.includes(folder)} onChange={() => s.toggleFolder(folder)} />
        </label>
      ))}
      <ul className="space-y-2">
        {s.files
          .filter((f) => s.sharedFolders.includes(f.folder))
          .map((f) => (
            <li key={f.id} className="flex items-center justify-between gap-2 rounded-lg border border-line px-3 py-3 text-sm">
              <span className="min-w-0 truncate">
                {f.folder}/{f.name}
              </span>
              <button className="shrink-0 text-muted" onClick={() => void s.sendPayload("android", "file", f.name, undefined, f.size, 3)}>
                Send
              </button>
            </li>
          ))}
      </ul>
    </div>
  );
}

function ScanPage() {
  const s = useBridge();
  const canvas = useRef<HTMLCanvasElement>(null);
  const [ready, setReady] = useState(false);
  const [ask, setAsk] = useState(false);
  const draw = (file: File) => {
    const url = URL.createObjectURL(file);
    const img = new Image();
    img.onload = () => {
      const c = canvas.current;
      if (!c) return;
      const max = 720;
      const scale = Math.min(1, max / img.width);
      c.width = Math.max(1, Math.round(img.width * scale));
      c.height = Math.max(1, Math.round(img.height * scale));
      const ctx = c.getContext("2d");
      if (!ctx) return;
      ctx.drawImage(img, 0, 0, c.width, c.height);
      const image = ctx.getImageData(0, 0, c.width, c.height);
      const d = image.data;
      for (let i = 0; i < d.length; i += 4) {
        const g = d[i] * 0.3 + d[i + 1] * 0.59 + d[i + 2] * 0.11;
        const v = Math.min(255, Math.max(0, (g - 128) * 1.35 + 140));
        d[i] = d[i + 1] = d[i + 2] = v;
      }
      ctx.putImageData(image, 0, 0);
      setReady(true);
      URL.revokeObjectURL(url);
    };
    img.src = url;
  };
  if (!s.features.scan) {
    return (
      <div className="space-y-3">
        <h2 className="text-lg font-medium">Scan to Mac</h2>
        <p className="text-sm text-muted">Take a page, flatten the contrast, and send a scan to the Mac. On-device OCR is not running in this desk.</p>
        <Btn variant="primary" onClick={() => setAsk(true)}>
          Enable scanner
        </Btn>
        {ask && (
          <Ask
            title="Camera for scanning"
            body="The next system prompt would be the camera. You can also pick a picture you already have."
            confirm="Enable"
            onConfirm={() => {
              s.setPermission("android", "camera", true);
              s.setFeature("scan", true);
              setAsk(false);
            }}
            onClose={() => setAsk(false)}
          />
        )}
      </div>
    );
  }
  return (
    <div className="space-y-3">
      <h2 className="text-lg font-medium">Scan to Mac</h2>
      <input
        type="file"
        accept="image/*"
        capture="environment"
        className="block w-full text-sm"
        onChange={(e) => {
          const file = e.target.files?.[0];
          if (file) draw(file);
        }}
      />
      <canvas ref={canvas} className="w-full rounded-lg border border-line bg-inset" />
      <p className="text-xs text-subtle">Contrast is cleaned here. A text layer needs on-device OCR, which this desk does not run.</p>
      <Btn
        variant="primary"
        disabled={!ready}
        onClick={() => void s.sendPayload("android", "file", "Scan.pdf", undefined, 480_000, 2)}
      >
        Send scan to Mac
      </Btn>
    </div>
  );
}

function MirrorPage() {
  const s = useBridge();
  return (
    <div className="space-y-3">
      <h2 className="text-lg font-medium">Screen mirror</h2>
      <p className="text-sm leading-relaxed text-muted">
        Capture is approved per session, the same way the system capture dialog works. The Mac shows this phone screen live. Control from the Mac uses the accessibility path, not a hidden debug channel.
      </p>
      <Btn variant="primary" onClick={() => { s.setFeature("mirror", true); s.approveMirror(true); s.setMacPane("mirror"); }}>
        {s.mirrorApproved ? "Session active" : "Approve this session"}
      </Btn>
      {s.mirrorApproved && (
        <Btn onClick={() => s.approveMirror(false)}>End session</Btn>
      )}
      <label className="flex items-start justify-between gap-3 text-sm">
        <span>Let the Mac click and type through this screen</span>
        <input
          type="checkbox"
          checked={s.features.control && s.permissions.android.accessibility}
          onChange={(e) => {
            s.setPermission("android", "accessibility", e.target.checked);
            s.setFeature("control", e.target.checked);
          }}
        />
      </label>
    </div>
  );
}

function CameraPage() {
  const active = useBridge((s) => s.cameraActive);
  const error = useBridge((s) => s.cameraError);
  const setCamera = useBridge((s) => s.setCamera);
  const setFeature = useBridge((s) => s.setFeature);
  const setPermission = useBridge((s) => s.setPermission);
  const video = useRef<HTMLVideoElement>(null);
  useEffect(() => {
    const node = video.current;
    if (!active) return;
    bindVideo(node);
    void ensureCamera().then((problem) => {
      if (problem) setCamera(false, problem);
    });
    return () => unbindVideo(node);
  }, [active, setCamera]);
  return (
    <div className="space-y-3">
      <h2 className="text-lg font-medium">Camera preview</h2>
      <p className="text-sm text-muted">The Mac can show this preview. A virtual camera device is not available from the browser, so video-call apps will not see it as a camera.</p>
      {active && <video ref={video} autoPlay playsInline muted className="aspect-[3/4] w-full rounded-lg bg-inset object-cover" />}
      {error && <p className="text-sm text-danger">{error}</p>}
      <Btn
        variant="primary"
        onClick={() => {
          if (active) {
            releaseCamera();
            setCamera(false, "");
            return;
          }
          setPermission("android", "camera", true);
          setPermission("android", "mic", true);
          setFeature("camera", true);
          setCamera(true, "");
        }}
      >
        {active ? "Stop camera" : "Start camera"}
      </Btn>
    </div>
  );
}

function RulesPage() {
  const s = useBridge();
  const [name, setName] = useState("Evening note");
  const [trigger, setTrigger] = useState<"wifi" | "charging" | "time" | "bluetooth">("time");
  const [triggerValue, setTriggerValue] = useState("18:00");
  const [action, setAction] = useState<"notify" | "lock" | "sync" | "send">("notify");
  const [actionValue, setActionValue] = useState("It is 18:00");
  if (!s.features.automations) {
    return (
      <div className="space-y-3">
        <h2 className="text-lg font-medium">Automations are off</h2>
        <p className="text-sm text-muted">Rules run only after you enable them. Triggers are Wi-Fi, charging, time, and headphones.</p>
        <Btn variant="primary" onClick={() => s.setFeature("automations", true)}>
          Enable automations
        </Btn>
      </div>
    );
  }
  return (
    <div className="space-y-3">
      <h2 className="text-lg font-medium">Automations</h2>
      <ul className="space-y-2">
        {s.rules.map((r) => (
          <li key={r.id} className="rounded-lg border border-line px-3 py-3">
            <div className="flex items-center justify-between gap-2">
              <p className="text-sm font-medium">{r.name}</p>
              <input type="checkbox" checked={r.enabled} onChange={(e) => s.toggleRule(r.id, e.target.checked)} aria-label={`Enable ${r.name}`} />
            </div>
            <p className="mt-1 text-sm text-muted">
              When {r.trigger} is {r.triggerValue} → {r.action}
            </p>
            <button className="mt-2 text-xs text-subtle" onClick={() => s.removeRule(r.id)}>
              Remove
            </button>
          </li>
        ))}
      </ul>
      <form
        className="space-y-2"
        onSubmit={(e) => {
          e.preventDefault();
          s.addRule({ name, enabled: true, trigger, triggerValue, action, actionValue });
        }}
      >
        <Field label="Name" value={name} onChange={setName} />
        <label className="block text-sm text-muted">
          Trigger
          <select className="mt-1 h-11 w-full rounded-sm border border-line bg-inset px-3 text-fg" value={trigger} onChange={(e) => setTrigger(e.target.value as typeof trigger)}>
            <option value="wifi">Wi-Fi name</option>
            <option value="charging">Charging starts</option>
            <option value="time">Time</option>
            <option value="bluetooth">Headphones</option>
          </select>
        </label>
        <Field label="Trigger value" value={triggerValue} onChange={setTriggerValue} placeholder="Home, start, 18:00, Headphones" />
        <label className="block text-sm text-muted">
          Action
          <select className="mt-1 h-11 w-full rounded-sm border border-line bg-inset px-3 text-fg" value={action} onChange={(e) => setAction(e.target.value as typeof action)}>
            <option value="notify">Notify</option>
            <option value="lock">Lock Mac</option>
            <option value="sync">Sync a photo</option>
            <option value="send">Send text</option>
          </select>
        </label>
        <Field label="Action detail" value={actionValue} onChange={setActionValue} />
        <Btn variant="primary" type="submit" className="w-full">
          Add rule
        </Btn>
      </form>
    </div>
  );
}

function SettingsPage() {
  const s = useBridge();
  const [confirm, setConfirm] = useState<"forget" | "clear" | null>(null);
  const [androidName, setAndroidName] = useState(s.androidName);
  const [macName, setMacName] = useState(s.macName);
  return (
    <div className="space-y-3">
      <h2 className="text-lg font-medium">Settings</h2>
      <Field label="Phone name" value={androidName} onChange={setAndroidName} />
      <Field label="Mac name" value={macName} onChange={setMacName} />
      <Btn onClick={() => s.setNames(androidName, macName)}>Save names</Btn>
      <label className="block text-sm">
        Keep history for {s.retentionDays} days
        <input className="mt-2 w-full" type="range" min={1} max={90} value={s.retentionDays} onChange={(e) => s.setRetention(Number(e.target.value))} />
      </label>
      <label className="flex h-11 items-center justify-between text-sm">
        Hide sensitive clipboard items
        <input type="checkbox" checked={s.hideSensitive} onChange={(e) => s.setHideSensitive(e.target.checked)} />
      </label>
      <label className="flex h-11 items-center justify-between text-sm">
        Debug log
        <input type="checkbox" checked={s.debug} onChange={(e) => s.setDebug(e.target.checked)} />
      </label>
      <Btn onClick={() => s.dropLink()}>Drop the link</Btn>
      <Btn
        onClick={() => {
          const blob = new Blob([s.exportDiagnostics()], { type: "application/json" });
          const url = URL.createObjectURL(blob);
          const a = document.createElement("a");
          a.href = url;
          a.download = "bridgelink-diagnostics.json";
          a.click();
          URL.revokeObjectURL(url);
        }}
      >
        Export diagnostics
      </Btn>
      <Btn variant="danger" onClick={() => setConfirm("clear")}>
        Clear history
      </Btn>
      <Btn variant="danger" onClick={() => setConfirm("forget")}>
        Forget device
      </Btn>
      <Btn onClick={() => s.triggerKeyChange()}>Simulate a key change</Btn>
      {confirm && (
        <Ask
          title={confirm === "forget" ? "Forget this Mac?" : "Clear history?"}
          body={
            confirm === "forget"
              ? "Pins, keys, and the link are removed. You can pair again with a new PIN. History stays until you clear it."
              : "Clipboard, alerts, and transfers on this desk are removed. Pairing stays."
          }
          confirm={confirm === "forget" ? "Forget" : "Clear"}
          onConfirm={() => {
            if (confirm === "forget") s.forgetDevice();
            else s.clearHistory();
            setConfirm(null);
          }}
          onClose={() => setConfirm(null)}
        />
      )}
    </div>
  );
}

function PairPhone({ clock }: { clock: number }) {
  const pin = useBridge((s) => s.pin);
  const expires = useBridge((s) => s.pinExpires);
  const submit = useBridge((s) => s.submitPin);
  const renew = useBridge((s) => s.beginPairing);
  const error = useBridge((s) => s.lastError);
  const [digits, setDigits] = useState(["", "", "", "", "", ""]);
  const refs = useRef<Array<HTMLInputElement | null>>([]);
  const left = Math.max(0, Math.ceil((expires - clock) / 1000));
  const expired = left === 0;
  const push = (next: string[]) => {
    setDigits(next);
    if (next.every((d) => d)) submit(next.join(""));
  };
  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <StatusBar clock={clock} />
      <div className="flex flex-1 flex-col justify-center px-4 pb-8">
        <h2 className="text-xl font-medium tracking-tight">Enter the Mac code</h2>
        <p className="mt-2 text-sm text-muted">{expired ? "The code expired." : `Expires in ${left}s. Keys are pinned after this.`}</p>
        <div className="mt-5 grid grid-cols-6 gap-2">
          {digits.map((d, i) => (
            <input
              key={i}
              ref={(el) => {
                refs.current[i] = el;
              }}
              inputMode="numeric"
              aria-label={`Digit ${i + 1}`}
              maxLength={1}
              value={d}
              disabled={expired}
              className="h-12 rounded-sm border border-line bg-inset text-center font-mono text-lg outline-none"
              onChange={(e) => {
                const v = e.target.value.replace(/\D/g, "").slice(-1);
                const next = [...digits];
                next[i] = v;
                push(next);
                if (v) refs.current[i + 1]?.focus();
              }}
              onKeyDown={(e) => {
                if (e.key === "Backspace" && !digits[i]) refs.current[i - 1]?.focus();
              }}
              onPaste={(e) => {
                const text = e.clipboardData.getData("text").replace(/\D/g, "").slice(0, 6);
                if (!text) return;
                e.preventDefault();
                const next = Array.from({ length: 6 }, (_, idx) => text[idx] ?? "");
                push(next);
              }}
            />
          ))}
        </div>
        {error && (
          <p className="mt-3 text-sm text-danger" role="alert">
            {error}
          </p>
        )}
        {expired && (
          <Btn className="mt-4" onClick={renew}>
            New code
          </Btn>
        )}
        <p className="mt-6 text-xs text-subtle">Shown on the Mac as {pin || "------"} while this desk is one session. On two real devices you would read it there.</p>
        <PairMark seed={pin || "waiting"} />
      </div>
    </div>
  );
}

export function PairMark({ seed }: { seed: string }) {
  const grid = pairingGrid(seed);
  return (
    <div className="mt-4 inline-grid grid-cols-[repeat(21,minmax(0,1fr))] gap-px" aria-hidden="true">
      {grid.flatMap((row, y) =>
        row.map((on, x) => <span key={`${x}-${y}`} className={on ? "size-1.5 bg-fg" : "size-1.5 bg-transparent"} />),
      )}
    </div>
  );
}
