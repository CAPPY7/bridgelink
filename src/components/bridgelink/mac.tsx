import { useEffect, useRef, useState, type DragEvent } from "react";
import { bindVideo, ensureCamera, unbindVideo } from "@/lib/bridgelink/camera";
import { formatFingerprint } from "@/lib/bridgelink/protocol";
import { useBridge, type MacPane, TRACKS } from "@/lib/bridgelink/store";
import { Btn, ClockLabel, Empty, Field, KeyWall, Pill, Progress, SLIDES, ago, formatBytes } from "./ui";
import { PairMark } from "./phone";

const PANES: { id: MacPane; label: string }[] = [
  { id: "inbox", label: "Inbox" },
  { id: "clips", label: "Clips" },
  { id: "messages", label: "Messages" },
  { id: "mirror", label: "Mirror" },
  { id: "files", label: "Files" },
  { id: "camera", label: "Camera" },
  { id: "rules", label: "Rules" },
  { id: "settings", label: "Settings" },
];

export function MacPanel({ clock }: { clock: number }) {
  const paired = useBridge((s) => s.paired);
  return (
    <section aria-label="Mac" className="min-w-0">
      <p className="mb-2 text-xs font-medium tracking-wide text-subtle uppercase">macOS</p>
      <div className="relative flex h-[calc(100dvh-10.5rem)] min-h-[560px] flex-col overflow-hidden rounded-xl border border-line bg-elevated lg:h-[740px]">
        <MenuBar clock={clock} />
        <div className="relative min-h-0 flex-1">
          {paired ? <MacBody /> : <PairMac clock={clock} />}
          <KeyWall />
          <LockScreen clock={clock} />
        </div>
      </div>
    </section>
  );
}

function MenuBar({ clock }: { clock: number }) {
  const battery = useBridge((s) => s.phoneBattery);
  const charging = useBridge((s) => s.phoneCharging);
  const low = battery <= 20;
  const open = useBridge((s) => s.macMenuOpen);
  const setMenu = useBridge((s) => s.setMenu);
  const path = useBridge((s) => s.path);
  return (
    <div className="flex h-9 shrink-0 items-center gap-3 border-b border-line px-3 text-xs">
      <button className="font-medium" onClick={() => setMenu(!open)} aria-expanded={open} aria-label="BridgeLink menu">
        BridgeLink
      </button>
      <span className="text-subtle">{path === "lan" ? "LAN" : path === "manual" ? "Host" : "Relay"}</span>
      <span className="ml-auto flex items-center gap-3 tabular-nums">
        <span className={low ? "text-warn" : "text-muted"}>
          Phone {battery}%{charging ? " +" : ""}
        </span>
        <ClockLabel clock={clock} />
      </span>
    </div>
  );
}

function LockScreen({ clock }: { clock: number }) {
  const locked = useBridge((s) => s.macLocked);
  const near = useBridge((s) => s.phoneNear);
  const unlock = useBridge((s) => s.unlockMac);
  const paired = useBridge((s) => s.paired);
  if (!locked || !paired) return null;
  return (
    <div className="absolute inset-0 z-20 flex flex-col items-center justify-center bg-bg px-6 text-center">
      <p className="font-mono text-4xl">
        <ClockLabel clock={clock} />
      </p>
      <p className="mt-3 text-sm text-muted">Locked because the phone left.</p>
      <p className="mt-1 max-w-sm text-sm text-subtle">
        {near ? "The phone is back. That does not unlock the Mac." : "Auto-unlock is not available."}
      </p>
      <Btn variant="primary" className="mt-5" onClick={unlock}>
        Unlock manually
      </Btn>
    </div>
  );
}

function MacBody() {
  const pane = useBridge((s) => s.macPane);
  const setPane = useBridge((s) => s.setMacPane);
  const banner = useBridge((s) => s.notices.find((n) => !n.dismissed && !s.blockedApps.includes(n.app) && n.source !== "system"));
  const dismiss = useBridge((s) => s.dismissNotice);
  const features = useBridge((s) => s.features);
  if (pane !== "home") {
    return (
      <div className="scroll-thin h-full overflow-y-auto p-4">
        <button className="mb-3 text-sm text-muted" onClick={() => setPane("home")}>
          Back
        </button>
        {pane === "inbox" && <InboxPane />}
        {pane === "clips" && <ClipsPane />}
        {pane === "messages" && <MessagesPane />}
        {pane === "mirror" && <MirrorPane />}
        {pane === "files" && <FilesPane />}
        {pane === "camera" && <CameraPane />}
        {pane === "rules" && <RulesPane />}
        {pane === "settings" && <MacSettings />}
        {pane === "browser" && <BrowserPane />}
      </div>
    );
  }
  return (
    <div className="scroll-thin relative h-full overflow-y-auto p-4">
      {banner && features.notifications && (
        <div className="mb-3 rounded-lg border border-line bg-panel p-3">
          <div className="flex items-start justify-between gap-3">
            <div>
              <p className="text-sm font-medium">
                {banner.app} · {banner.title}
              </p>
              <p className="mt-1 text-sm text-muted">{banner.body}</p>
            </div>
            <button className="text-xs text-muted" onClick={() => dismiss(banner.id)}>
              Dismiss
            </button>
          </div>
        </div>
      )}
      <HomePane />
    </div>
  );
}

function HomePane() {
  const s = useBridge();
  const fileRef = useRef<HTMLInputElement>(null);
  const [note, setNote] = useState("");
  const onDrop = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    if (!s.features.quickdrop) {
      s.log("warn", "Quick drop is off.");
      return;
    }
    const file = e.dataTransfer.files?.[0];
    if (file) {
      void s.sendFile("mac", file);
      return;
    }
    const text = e.dataTransfer.getData("text");
    if (text) {
      const link = /^https?:\/\//i.test(text);
      void s.sendPayload("mac", link ? "link" : "text", link ? text : "Dropped note", text, text.length, 1);
    }
  };
  const grace =
    s.graceUntil && !s.phoneNear ? Math.max(0, Math.ceil((s.graceUntil - Date.now()) / 1000)) : null;
  return (
    <div className="space-y-4">
      <div className="flex items-end justify-between gap-3">
        <div>
          <p className="text-xs text-muted">{s.androidName}</p>
          <h2 className="text-2xl font-medium tracking-tight">{s.status === "connected" ? "Linked" : "Reconnecting"}</h2>
        </div>
        <Pill tone={s.status === "connected" ? "ok" : "warn"}>{s.latencyMs} ms</Pill>
      </div>
      <div
        className="flex min-h-32 flex-col items-center justify-center rounded-lg border border-dashed border-line-strong px-4 py-6 text-center"
        onDragOver={(e) => e.preventDefault()}
        onDrop={onDrop}
      >
        <p className="text-sm font-medium">Drop a file, link, or note</p>
        <p className="mt-1 text-sm text-muted">It goes to {s.androidName}. One device is paired, so there is nothing to pick.</p>
        <div className="mt-3 flex gap-2">
          <Btn onClick={() => fileRef.current?.click()}>Choose file</Btn>
          <Btn
            onClick={() => {
              const text = note.trim();
              if (!text) return;
              const link = /^https?:\/\//i.test(text);
              void s.sendPayload("mac", link ? "link" : "text", link ? text : "Note", text, text.length, 1);
              setNote("");
            }}
          >
            Send note
          </Btn>
        </div>
        <input
          value={note}
          onChange={(e) => setNote(e.target.value)}
          placeholder="Or type here"
          className="mt-3 h-11 w-full max-w-sm rounded-sm border border-line bg-inset px-3 text-sm"
          aria-label="Note for the phone"
        />
        <input
          ref={fileRef}
          type="file"
          className="sr-only"
          onChange={(e) => {
            const file = e.target.files?.[0];
            if (file) void s.sendFile("mac", file);
            e.target.value = "";
          }}
        />
      </div>
      <div className="grid gap-3 sm:grid-cols-2">
        <DeskPad />
        <div className="rounded-lg border border-line p-3">
          <p className="text-xs text-muted">Now playing on the phone</p>
          <p className="mt-1 font-medium">{s.media.title}</p>
          <p className="text-sm text-muted">{s.media.artist}</p>
          <div className="mt-3 flex flex-wrap gap-2">
            <Btn className="h-9" onClick={() => s.setMedia({ ...TRACKS[(s.media.index + TRACKS.length - 1) % TRACKS.length], index: (s.media.index + TRACKS.length - 1) % TRACKS.length, playing: true })}>
              Prev
            </Btn>
            <Btn className="h-9" variant="primary" onClick={() => s.setMedia({ playing: !s.media.playing })}>
              {s.media.playing ? "Pause" : "Play"}
            </Btn>
            <Btn className="h-9" onClick={() => s.setMedia({ ...TRACKS[(s.media.index + 1) % TRACKS.length], index: (s.media.index + 1) % TRACKS.length, playing: true })}>
              Next
            </Btn>
          </div>
        </div>
      </div>
      <div className="flex flex-wrap gap-2">
        <Btn variant="primary" onClick={() => s.startRing()}>
          Find phone
        </Btn>
        {s.ringing && <Btn onClick={() => s.stopRing()}>Stop ringing</Btn>}
        {s.features.proximity && grace !== null && <Pill tone="warn">Locks in {grace}s</Pill>}
      </div>
      {s.lastError && (
        <p className="text-sm text-danger" role="alert">
          {s.lastError}
        </p>
      )}
      <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
        {PANES.map((p) => (
          <Btn key={p.id} onClick={() => s.setMacPane(p.id)}>
            {p.label}
          </Btn>
        ))}
      </div>
      {s.transfers[0] && (
        <div>
          <div className="mb-1 flex justify-between text-xs text-muted">
            <span className="truncate">{s.transfers[0].name}</span>
            <span>{s.transfers[0].status}</span>
          </div>
          <Progress value={s.transfers[0].progress} />
        </div>
      )}
    </div>
  );
}

function DeskPad() {
  const mode = useBridge((s) => s.remoteMode);
  const cursor = useBridge((s) => s.cursor);
  const typed = useBridge((s) => s.typedOnMac);
  const slide = useBridge((s) => s.slide);
  const inputOn = useBridge((s) => s.features.input);
  return (
    <div className="relative h-44 overflow-hidden rounded-lg border border-line bg-inset">
      {!inputOn && <p className="p-3 text-sm text-muted">Remote input is off. Turn it on from the phone.</p>}
      {inputOn && mode === "slides" && (
        <div className="p-4">
          <p className="text-xs text-subtle">
            Slide {slide + 1} / {SLIDES.length}
          </p>
          <p className="mt-2 text-lg font-medium">{SLIDES[slide][0]}</p>
          <p className="text-sm text-muted">{SLIDES[slide][1]}</p>
        </div>
      )}
      {inputOn && mode === "keyboard" && (
        <div className="p-4">
          <p className="text-xs text-subtle">Typed from the phone</p>
          <p className="mt-2 text-sm">{typed || "Waiting for keys"}</p>
        </div>
      )}
      {inputOn && mode === "trackpad" && (
        <>
          <p className="absolute top-3 left-3 text-xs text-subtle">Pointer</p>
          <span
            className="absolute size-3.5"
            style={{ left: `${cursor.x}%`, top: `${cursor.y}%`, transform: "translate(-20%, -10%)" }}
          >
            <svg viewBox="0 0 16 16" className="size-4 fill-fg" aria-hidden="true">
              <path d="M3 1.5 13 8.2 8.2 9.2 6.6 14.2Z" />
            </svg>
          </span>
          {cursor.down && <span className="absolute right-3 bottom-3 text-xs text-muted">click</span>}
        </>
      )}
    </div>
  );
}

function InboxPane() {
  const notices = useBridge((s) => s.notices);
  const blocked = useBridge((s) => s.blockedApps);
  const dismiss = useBridge((s) => s.dismissNotice);
  const reply = useBridge((s) => s.replyNotice);
  const copy = useBridge((s) => s.copyText);
  const [q, setQ] = useState("");
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const items = notices.filter((n) => !blocked.includes(n.app) && `${n.app} ${n.title} ${n.body}`.toLowerCase().includes(q.toLowerCase()));
  return (
    <div className="space-y-3">
      <h2 className="text-lg font-medium">Inbox</h2>
      <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search" aria-label="Search Mac inbox" className="h-11 w-full rounded-sm border border-line bg-inset px-3 text-sm" />
      {items.length === 0 && <Empty title="Inbox is clear" body="Mirrored alerts show here unless the app is blocked." />}
      <ul className="space-y-2">
        {items.map((n) => (
          <li key={n.id} className="rounded-lg border border-line px-3 py-3">
            <div className="flex justify-between gap-2 text-sm">
              <span className="font-medium">
                {n.app} · {n.title}
              </span>
              <span className="text-subtle">{ago(n.ts)}</span>
            </div>
            <p className="mt-1 text-sm text-muted">{n.body}</p>
            {n.reply && <p className="mt-2 text-sm">Sent: {n.reply}</p>}
            <div className="mt-2 flex flex-wrap gap-2">
              {n.source === "otp" && (
                <Btn className="h-9" onClick={() => copy(n.body.match(/\d{4,8}/)?.[0] ?? "", "mac")}>
                  Copy code
                </Btn>
              )}
              {!n.dismissed && (
                <Btn className="h-9" onClick={() => dismiss(n.id)}>
                  Dismiss
                </Btn>
              )}
            </div>
            <form
              className="mt-2 flex gap-2"
              onSubmit={(e) => {
                e.preventDefault();
                reply(n.id, drafts[n.id] ?? "");
                setDrafts((d) => ({ ...d, [n.id]: "" }));
              }}
            >
              <input
                value={drafts[n.id] ?? ""}
                onChange={(e) => setDrafts((d) => ({ ...d, [n.id]: e.target.value }))}
                placeholder="Reply on the phone"
                className="h-11 min-w-0 flex-1 rounded-sm border border-line bg-inset px-3 text-sm"
                aria-label={`Reply to ${n.app}`}
              />
              <Btn type="submit">Reply</Btn>
            </form>
          </li>
        ))}
      </ul>
    </div>
  );
}

function ClipsPane() {
  const clips = useBridge((s) => s.clips);
  const hide = useBridge((s) => s.hideSensitive);
  const pin = useBridge((s) => s.togglePinClip);
  const copy = useBridge((s) => s.copyText);
  const setMac = useBridge((s) => s.setMacClipboard);
  const macText = useBridge((s) => s.macClipboard);
  const [q, setQ] = useState("");
  const [showHidden, setShowHidden] = useState(false);
  const items = clips.filter((c) => {
    if (hide && c.sensitive && !showHidden) return false;
    return c.text.toLowerCase().includes(q.toLowerCase());
  });
  return (
    <div className="space-y-3">
      <h2 className="text-lg font-medium">Clipboard history</h2>
      <Field label="Mac clipboard" value={macText} onChange={(v) => setMac(v)} />
      <p className="text-xs text-subtle">Editing this sends it to the phone while clipboard sync is on.</p>
      <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search clips" aria-label="Search clips" className="h-11 w-full rounded-sm border border-line bg-inset px-3 text-sm" />
      <label className="flex items-center gap-2 text-sm text-muted">
        <input type="checkbox" checked={showHidden} onChange={(e) => setShowHidden(e.target.checked)} />
        Show sensitive items
      </label>
      <ul className="space-y-2">
        {items.map((c) => (
          <li key={c.id} className="rounded-lg border border-line px-3 py-3">
            <p className="text-sm break-all">{c.sensitive ? "Sensitive item" : c.text}</p>
            {!c.sensitive && <p className="sr-only">{c.text}</p>}
            <div className="mt-2 flex gap-2">
              <Btn className="h-9" onClick={() => copy(c.text, "mac")}>
                Copy to phone
              </Btn>
              <Btn className="h-9" onClick={() => pin(c.id)}>
                {c.pinned ? "Unpin" : "Pin"}
              </Btn>
            </div>
          </li>
        ))}
      </ul>
    </div>
  );
}

function MessagesPane() {
  const s = useBridge();
  const [body, setBody] = useState("");
  const [ask, setAsk] = useState(false);
  const thread = s.threads.find((t) => t.id === s.activeThread) ?? s.threads[0];
  if (!s.features.sms || !s.permissions.android.sms) {
    return (
      <div className="space-y-3">
        <h2 className="text-lg font-medium">Messages</h2>
        <p className="text-sm leading-relaxed text-muted">
          Reading and sending texts needs the SMS permission on the phone, and contact names need Contacts. Distribution that can keep SMS access is the APK, not a Play listing that forbids it.
        </p>
        <Btn variant="primary" onClick={() => setAsk(true)}>
          Allow SMS
        </Btn>
        {ask && (
          <div className="rounded-lg border border-line p-3 text-sm">
            <p>Allow BridgeLink to read and send SMS, and to show contact names?</p>
            <div className="mt-3 flex gap-2">
              <Btn
                variant="primary"
                onClick={() => {
                  s.setPermission("android", "sms", true);
                  s.setPermission("android", "contacts", true);
                  s.setFeature("sms", true);
                  setAsk(false);
                }}
              >
                Allow
              </Btn>
              <Btn onClick={() => setAsk(false)}>Not now</Btn>
            </div>
          </div>
        )}
      </div>
    );
  }
  if (!thread) return null;
  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-medium">{thread.name}</h2>
        <span className="text-xs text-subtle">{thread.number}</span>
      </div>
      <ul className="space-y-2">
        {thread.messages.map((m) => (
          <li key={m.id} className={m.from === "me" ? "ml-8 rounded-lg bg-accent px-3 py-2 text-sm text-accent-fg" : "mr-8 rounded-lg border border-line px-3 py-2 text-sm"}>
            {m.body}
          </li>
        ))}
      </ul>
      <form
        className="flex gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          if (!body.trim()) return;
          s.sendSms(thread.id, body.trim(), "me");
          setBody("");
        }}
      >
        <input value={body} onChange={(e) => setBody(e.target.value)} placeholder="Reply" aria-label="SMS reply" className="h-11 min-w-0 flex-1 rounded-sm border border-line bg-inset px-3 text-sm" />
        <Btn variant="primary" type="submit">
          Send
        </Btn>
      </form>
      <Btn onClick={() => s.sendSms(thread.id, "On my way.", "them")}>Receive a text</Btn>
    </div>
  );
}

function MirrorPane() {
  const approved = useBridge((s) => s.mirrorApproved);
  const control = useBridge((s) => s.features.control);
  const screen = useBridge((s) => s.phoneScreen);
  const battery = useBridge((s) => s.phoneBattery);
  const media = useBridge((s) => s.media);
  const name = useBridge((s) => s.androidName);
  const setScreen = useBridge((s) => s.setPhoneScreen);
  if (!approved) {
    return (
      <div className="space-y-3">
        <h2 className="text-lg font-medium">Waiting for the phone</h2>
        <p className="text-sm text-muted">Open Screen mirror on the phone and approve this session. Capture is not remembered.</p>
      </div>
    );
  }
  return (
    <div className="space-y-3">
      <h2 className="text-lg font-medium">Live screen</h2>
      <p className="text-sm text-muted">{control ? "Clicks on this window control the phone through the accessibility path." : "View only. Turn on control on the phone to click through."}</p>
      <div className={control ? "mx-auto max-w-sm rounded-xl border border-line bg-bg p-4" : "mx-auto max-w-sm rounded-xl border border-line bg-bg p-4 pointer-events-none"}>
        <p className="text-xs text-subtle">{name} · {battery}%</p>
        <p className="mt-3 text-lg font-medium capitalize">{screen}</p>
        <p className="mt-1 text-sm text-muted">
          {media.playing ? "Playing" : "Paused"} · {media.title}
        </p>
        <div className="mt-4 grid grid-cols-2 gap-2">
          {(["home", "send", "inbox", "remote"] as const).map((id) => (
            <button key={id} className="h-11 rounded-sm border border-line text-sm capitalize" onClick={() => setScreen(id)}>
              {id}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}

function FilesPane() {
  const s = useBridge();
  const visible = s.files.filter((f) => s.sharedFolders.includes(f.folder));
  if (!s.features.files) {
    return (
      <div className="space-y-3">
        <h2 className="text-lg font-medium">Phone storage</h2>
        <p className="text-sm text-muted">Turn on shared folders on the phone first. Mount only works for the paired link.</p>
      </div>
    );
  }
  return (
    <div className="space-y-3">
      <h2 className="text-lg font-medium">Mounted folders</h2>
      <p className="text-sm text-muted">This is the paired file browser. Finder cannot mount a disk from the browser. Copy a file and it transfers.</p>
      {visible.length === 0 && <Empty title="Nothing shared" body="Mark a folder on the phone." />}
      <ul className="space-y-2">
        {visible.map((f) => (
          <li key={f.id} className="flex items-center justify-between gap-3 rounded-lg border border-line px-3 py-3 text-sm">
            <span className="min-w-0 truncate">
              {f.folder}/{f.name}
              <span className="ml-2 text-subtle">{formatBytes(f.size)}</span>
            </span>
            <button className="shrink-0" onClick={() => void s.sendPayload("android", "file", f.name, undefined, f.size, 3)}>
              Copy here
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}

function CameraPane() {
  const active = useBridge((s) => s.cameraActive);
  const error = useBridge((s) => s.cameraError);
  const video = useRef<HTMLVideoElement>(null);
  useEffect(() => {
    const node = video.current;
    if (!active) return;
    bindVideo(node);
    void ensureCamera();
    return () => unbindVideo(node);
  }, [active]);
  return (
    <div className="space-y-3">
      <h2 className="text-lg font-medium">Camera preview</h2>
      <p className="text-sm text-muted">Experimental. This window can be screen-captured. It is not a virtual camera other apps can select.</p>
      {active ? (
        <video ref={video} autoPlay playsInline muted className="aspect-video w-full rounded-lg bg-inset object-cover" />
      ) : (
        <Empty title={error || "Camera is off"} body="Start it from the phone. If the browser blocks the camera, the preview stays empty." />
      )}
    </div>
  );
}

function RulesPane() {
  const rules = useBridge((s) => s.rules);
  const on = useBridge((s) => s.features.automations);
  const toggle = useBridge((s) => s.toggleRule);
  return (
    <div className="space-y-3">
      <h2 className="text-lg font-medium">Automations</h2>
      {!on && <p className="text-sm text-muted">Automations are off on the phone.</p>}
      <ul className="space-y-2">
        {rules.map((r) => (
          <li key={r.id} className="flex items-center justify-between gap-3 rounded-lg border border-line px-3 py-3 text-sm">
            <span>
              <span className="block font-medium">{r.name}</span>
              <span className="text-muted">
                {r.trigger} · {r.triggerValue} → {r.action}
              </span>
            </span>
            <input type="checkbox" checked={r.enabled && on} onChange={(e) => toggle(r.id, e.target.checked)} aria-label={r.name} />
          </li>
        ))}
      </ul>
    </div>
  );
}

function MacSettings() {
  const s = useBridge();
  const perms = [
    ["localNetwork", "Local network", "Find the phone on the same Wi-Fi."],
    ["notifications", "Notifications", "Show mirrored alerts."],
    ["accessibility", "Accessibility", "Pointer, keyboard, and slides."],
    ["camera", "Camera", "Only if you present the phone camera."],
    ["microphone", "Microphone", "Audio that rides with the camera preview."],
    ["files", "Files", "Save incoming scans and transfers."],
  ] as const;
  return (
    <div className="space-y-4">
      <h2 className="text-lg font-medium">Mac settings</h2>
      <p className="font-mono text-xs text-muted">Phone {formatFingerprint(s.seenAndroidFp)}</p>
      <p className="font-mono text-xs text-muted">Mac {formatFingerprint(s.pinnedMacFp)}</p>
      <div className="flex flex-wrap gap-2">
        {(["lan", "manual", "relay"] as const).map((p) => (
          <Btn key={p} variant={s.path === p ? "primary" : "quiet"} onClick={() => s.setPath(p)}>
            {p === "lan" ? "Local" : p === "manual" ? "Saved host" : "Relay"}
          </Btn>
        ))}
      </div>
      <Field label="Saved host or Tailscale address" value={s.manualHost} onChange={s.setManualHost} placeholder="100.x.y.z or host:port" />
      <label className="flex items-start justify-between gap-3 text-sm">
        <span>
          <span className="block font-medium">Wake-up push</span>
          <span className="text-muted">A ping with no message content, so the phone can reconnect. Off unless you add a Firebase project later.</span>
        </span>
        <input type="checkbox" checked={s.fcmEnabled} onChange={(e) => s.setFcm(e.target.checked)} />
      </label>
      <label className="block text-sm">
        Lock delay {s.lockDelaySec}s
        <input className="mt-2 w-full" type="range" min={5} max={60} value={s.lockDelaySec} onChange={(e) => s.setLockDelay(Number(e.target.value))} />
      </label>
      <label className="flex items-center justify-between text-sm">
        <span>Proximity lock</span>
        <input type="checkbox" checked={s.features.proximity} onChange={(e) => s.setFeature("proximity", e.target.checked)} />
      </label>
      <ul className="space-y-2">
        {perms.map(([key, label, body]) => (
          <li key={key} className="rounded-lg border border-line px-3 py-3 text-sm">
            <div className="flex items-center justify-between gap-3">
              <span className="font-medium">{label}</span>
              <span className="text-xs text-muted">{s.permissions.mac[key] ? "Allowed" : "Not allowed"}</span>
            </div>
            <p className="mt-1 text-muted">{body}</p>
            <button
              className="mt-2 text-sm"
              onClick={() => s.setPermission("mac", key, !s.permissions.mac[key])}
            >
              {s.permissions.mac[key] ? "Revoke" : "Allow"}
            </button>
          </li>
        ))}
      </ul>
      <Btn onClick={() => s.dropLink()}>Drop the link</Btn>
      <Btn onClick={() => s.triggerKeyChange()}>Simulate a key change</Btn>
    </div>
  );
}

function BrowserPane() {
  const url = useBridge((s) => s.macBrowser);
  const close = useBridge((s) => s.closeBrowser);
  return (
    <div className="space-y-3">
      <h2 className="text-lg font-medium">Opened from the phone</h2>
      <p className="text-sm break-all">{url}</p>
      <Btn onClick={() => close("mac")}>Close</Btn>
    </div>
  );
}

function PairMac({ clock }: { clock: number }) {
  const pin = useBridge((s) => s.pin);
  const expires = useBridge((s) => s.pinExpires);
  const renew = useBridge((s) => s.beginPairing);
  const left = Math.max(0, Math.ceil((expires - clock) / 1000));
  return (
    <div className="flex h-full flex-col items-start justify-center px-6">
      <p className="text-xs text-subtle">Pairing</p>
      <h2 className="mt-1 text-2xl font-medium tracking-tight">Enter this code on the phone</h2>
      <p className="mt-4 font-mono text-4xl tracking-[0.3em] tabular-nums">{pin || "------"}</p>
      <p className="mt-3 text-sm text-muted">{left > 0 ? `Expires in ${left}s` : "Expired"}</p>
      <PairMark seed={pin || "waiting"} />
      <p className="mt-3 max-w-md text-sm text-muted">The mark is a check beside the PIN. After pairing, each side pins the other’s public key and rejects a mismatch.</p>
      {left === 0 && (
        <Btn className="mt-4" onClick={renew}>
          New code
        </Btn>
      )}
    </div>
  );
}
