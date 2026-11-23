import * as Dialog from "@radix-ui/react-dialog";
import type { ReactNode } from "react";
import { useBridge } from "@/lib/bridgelink/store";
import { Btn } from "./ui";

const FEATURES: { n: number; name: string; status: "Working" | "Partial" | "Removed"; note: string }[] = [
  { n: 1, name: "Send files, links, and text", status: "Working", note: "Both ways, with pause and resume. Files are read in chunks." },
  { n: 2, name: "Share sheet and Mac drop", status: "Working", note: "Share to Mac on the phone. Drop a real file on the Mac pane." },
  { n: 3, name: "Notification mirror", status: "Working", note: "Dismiss and reply stay in sync. Apps can be blocked." },
  { n: 4, name: "Media control", status: "Working", note: "Play, pause, and volume follow the phone." },
  { n: 5, name: "Battery", status: "Working", note: "Set the levels in Phone conditions. Low battery raises an alert." },
  { n: 6, name: "Find phone", status: "Working", note: "Rings on the alarm stream until you stop it." },
  { n: 7, name: "OTP auto-copy", status: "Working", note: "A code in an alert is copied to the Mac clipboard." },
  { n: 8, name: "Open a link", status: "Working", note: "A URL opens on the other device." },
  { n: 9, name: "Trackpad, keyboard, slides", status: "Working", note: "Needs the Mac accessibility switch, which you grant in the app." },
  { n: 10, name: "Clipboard sync", status: "Working", note: "Mac to phone is automatic. The phone has all five send methods." },
  { n: 11, name: "SMS", status: "Working", note: "Turn it on to read, reply, and keep contact names." },
  { n: 12, name: "Screen mirror", status: "Partial", note: "The installable apps send H.264. This browser demo only shows a stand-in picture." },
  { n: 13, name: "Control while mirroring", status: "Partial", note: "Clicks use the accessibility path. There is no wireless ADB channel here." },
  { n: 14, name: "Phone as webcam", status: "Partial", note: "Browser preview only. No virtual camera other apps can select." },
  { n: 15, name: "Shared folder", status: "Partial", note: "Browse folders you mark and copy files. Finder does not mount a disk." },
  { n: 16, name: "Lock when you leave", status: "Working", note: "Delay is configurable. The Mac never unlocks by itself." },
  { n: 17, name: "Remote link", status: "Removed", note: "This path is gone. The phone and the Mac stay on the same Wi-Fi." },
  { n: 18, name: "Combined list", status: "Removed", note: "Notifications, texts, and files stay in their own places. The combined list is gone." },
  { n: 19, name: "Clipboard history", status: "Working", note: "Last 100, pins, and a sensitive filter." },
  { n: 20, name: "Scan to Mac", status: "Partial", note: "Contrast cleanup and send. No on-device OCR." },
  { n: 21, name: "Automations", status: "Working", note: "Wi-Fi, charging, time, and headphones can notify, lock, sync, or send." },
  { n: 22, name: "Quick drop", status: "Working", note: "The Mac drop zone targets the only paired phone." },
  { n: 23, name: "Pairing", status: "Working", note: "Six-digit PIN, two-minute expiry, forget, and pair again." },
  { n: 24, name: "Encrypted local link", status: "Working", note: "The local link uses TLS and a pinned key." },
  { n: 25, name: "Key pin", status: "Working", note: "A changed fingerprint blocks sending until you trust or reject it." },
];

export function GuideDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-40 bg-bg/70" />
        <Dialog.Content className="fixed inset-3 z-50 flex flex-col overflow-hidden rounded-xl border border-line bg-elevated lg:inset-y-8 lg:left-1/2 lg:w-[680px] lg:-translate-x-1/2">
          <div className="flex items-center justify-between border-b border-line px-4 py-3">
            <Dialog.Title className="text-base font-medium">Guide</Dialog.Title>
            <Dialog.Close asChild>
              <Btn>Close</Btn>
            </Dialog.Close>
          </div>
          <div className="scroll-thin min-h-0 flex-1 space-y-8 overflow-y-auto px-4 py-4 text-sm leading-relaxed">
            <section>
              <h3 className="text-lg font-medium tracking-tight">Use the desk</h3>
              <ol className="mt-3 list-decimal space-y-2 pl-5 text-muted">
                <li>You are looking at the phone and the Mac together. On a small screen, switch with Phone and Mac.</li>
                <li>The link pill should say Linked. Sample history is already there so you can try things immediately.</li>
                <li>On the phone, open Send and deliver a note. Watch the chunk count finish on both sides.</li>
                <li>Drop a file on the Mac drop zone, or type a link. A link opens on the other device.</li>
                <li>Open Alerts on the Mac and dismiss an alert. It clears on the phone too.</li>
                <li>Copy the bank code from Alerts. It lands on the Mac clipboard.</li>
                <li>On the phone, open Remote and allow accessibility, then drag the trackpad. The Mac pointer follows.</li>
                <li>Approve Screen mirror for this session. Turn on control if you want clicks on the Mac to change the phone.</li>
                <li>In Phone conditions, mark the phone as away and turn on proximity lock in Mac settings. Wait out the delay. Unlock only with the button.</li>
                <li>Settings can export a diagnostics file with no message text, or forget the device so you can pair again with the PIN.</li>
              </ol>
            </section>
            <section>
              <h3 className="text-lg font-medium tracking-tight">What this is</h3>
              <p className="mt-2 text-muted">
                This window is a simulation. Nothing here is connected to a phone or a Mac. The labels below describe the demo only, not the installable apps. Download those from the GitHub releases page.
              </p>
            </section>
            <section>
              <h3 className="text-lg font-medium tracking-tight">Features</h3>
              <ul className="mt-3 space-y-3">
                {FEATURES.map((f) => (
                  <li key={f.n} className="border-b border-line pb-3">
                    <div className="flex items-baseline justify-between gap-3">
                      <p className="font-medium">
                        {f.n}. {f.name}
                      </p>
                      <span className={f.status === "Working" ? "text-xs text-ok" : "text-xs text-warn"}>{f.status}</span>
                    </div>
                    <p className="mt-1 text-muted">{f.note}</p>
                  </li>
                ))}
              </ul>
            </section>
            <Section title="If the link looks dead">
              Drop the link in Settings and wait. It backs off and reconnects. Local is tried first. A saved host is for an address you already have, such as a Tailscale IP.
            </Section>
            <Section title="If the phone would be killed in the background">
              Allow the battery exemption, leave the foreground notice up, and on Xiaomi, Oppo, Vivo, Samsung, OnePlus, or Huawei also allow autostart and lock the app in recents. The permission screen says this in plain language.
            </Section>
            <Section title="Mac permissions">
              Local network, notifications, accessibility, camera, microphone, and files are listed with a reason. Allow is explicit. Nothing is flipped silently.
            </Section>
            <Section title="Not attempted">
              iMessage, FaceTime, Apple’s own AirDrop or Handoff, unlocking the Mac, changing hotspot or airplane mode, running Android apps on the Mac, and phone-call audio are out of scope. There are no fake buttons for them.
            </Section>
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section>
      <h3 className="text-lg font-medium tracking-tight">{title}</h3>
      <p className="mt-2 text-muted">{children}</p>
    </section>
  );
}

export function LogDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-40 bg-bg/70" />
        <Dialog.Content className="fixed inset-3 z-50 flex flex-col overflow-hidden rounded-xl border border-line bg-elevated lg:inset-y-10 lg:left-1/2 lg:w-[560px] lg:-translate-x-1/2">
          <div className="flex items-center justify-between border-b border-line px-4 py-3">
            <Dialog.Title className="text-base font-medium">Link log</Dialog.Title>
            <Dialog.Close asChild>
              <Btn>Close</Btn>
            </Dialog.Close>
          </div>
          <LogBody />
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

function LogBody() {
  const logs = useBridge((s) => s.logs);
  const debug = useBridge((s) => s.debug);
  const rows = debug ? logs : logs.slice(-12);
  return (
    <ol className="scroll-thin min-h-0 flex-1 space-y-2 overflow-y-auto px-4 py-4 font-mono text-xs leading-relaxed text-muted">
      {rows.map((line) => (
        <li key={line.id}>
          <span className="text-subtle">{new Date(line.ts).toLocaleTimeString()} </span>
          {line.msg}
        </li>
      ))}
      {!debug && <li className="text-subtle">Debug detail is off. Turn it on in phone Settings if you need the longer trail.</li>}
    </ol>
  );
}
