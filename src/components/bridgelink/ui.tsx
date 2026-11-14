import { useEffect, useState, type ButtonHTMLAttributes, type ReactNode } from "react";
import { cn } from "@/lib/utils";
import { formatFingerprint } from "@/lib/bridgelink/protocol";
import { DEMO_NOW, useBridge } from "@/lib/bridgelink/store";

export function Mark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 32 32" className={className} aria-hidden="true">
      <rect x="3.5" y="6.5" width="10" height="19" rx="2" fill="none" stroke="currentColor" strokeWidth="1.6" />
      <rect x="18.5" y="9.5" width="10" height="13" rx="2" fill="none" stroke="currentColor" strokeWidth="1.6" />
      <path d="M13.5 16h5" stroke="currentColor" strokeWidth="1.6" />
    </svg>
  );
}

type BtnProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: "primary" | "quiet" | "ghost" | "danger";
};

export function Btn({ variant = "quiet", className, type = "button", ...props }: BtnProps) {
  return (
    <button
      type={type}
      className={cn(
        "inline-flex h-11 items-center justify-center gap-2 rounded-sm px-3 text-sm font-medium",
        "transition-[opacity,transform] duration-150 ease-[cubic-bezier(0.22,1,0.36,1)]",
        "active:scale-[0.98] disabled:pointer-events-none disabled:opacity-40",
        "focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent",
        variant === "primary" && "bg-accent text-accent-fg",
        variant === "quiet" && "border border-line bg-panel text-fg",
        variant === "ghost" && "bg-transparent text-muted",
        variant === "danger" && "border border-line bg-transparent text-danger",
        className,
      )}
      {...props}
    />
  );
}

export function Field({
  label,
  value,
  onChange,
  placeholder,
  multiline,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  multiline?: boolean;
}) {
  const className =
    "w-full rounded-sm border border-line bg-inset px-3 text-sm text-fg outline-none placeholder:text-subtle focus:border-line-strong";
  return (
    <label className="block">
      <span className="mb-1.5 block text-sm text-muted">{label}</span>
      {multiline ? (
        <textarea
          value={value}
          placeholder={placeholder}
          onChange={(e) => onChange(e.target.value)}
          className={cn(className, "min-h-24 py-2")}
        />
      ) : (
        <input value={value} placeholder={placeholder} onChange={(e) => onChange(e.target.value)} className={cn(className, "h-11")} />
      )}
    </label>
  );
}

export function Progress({ value }: { value: number }) {
  return (
    <div className="h-1.5 overflow-hidden rounded-full bg-inset" aria-hidden="true">
      <div className="h-full bg-accent transition-[width] duration-150 ease-[cubic-bezier(0.22,1,0.36,1)]" style={{ width: `${Math.round(value * 100)}%` }} />
    </div>
  );
}

export function Pill({ children, tone = "neutral" }: { children: ReactNode; tone?: "neutral" | "ok" | "warn" | "danger" }) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full border border-line px-2 py-0.5 text-xs font-medium tabular-nums",
        tone === "neutral" && "text-muted",
        tone === "ok" && "text-ok",
        tone === "warn" && "text-warn",
        tone === "danger" && "text-danger",
      )}
    >
      {children}
    </span>
  );
}

export function Empty({ title, body }: { title: string; body: string }) {
  return (
    <div className="rounded-lg border border-dashed border-line px-4 py-8 text-center">
      <p className="text-sm font-medium">{title}</p>
      <p className="mt-1 text-sm text-muted">{body}</p>
    </div>
  );
}

export function formatBytes(n: number): string {
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${Math.round(n / 1024)} KB`;
  return `${(n / 1024 / 1024).toFixed(1)} MB`;
}

export function ClockLabel({ clock }: { clock: number }) {
  const [live, setLive] = useState(false);
  useEffect(() => setLive(true), []);
  const text = live ? new Date(clock).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" }) : "--:--";
  return <span className="tabular-nums">{text}</span>;
}

export function ago(ts: number, nowMs = DEMO_NOW): string {
  const s = Math.max(1, Math.round((nowMs - ts) / 1000));
  if (s < 60) return `${s}s ago`;
  const m = Math.round(s / 60);
  if (m < 60) return `${m}m ago`;
  const h = Math.round(m / 60);
  if (h < 48) return `${h}h ago`;
  return `${Math.round(h / 24)}d ago`;
}

export const SLIDES = [
  ["BridgeLink", "Two devices. One desk."],
  ["Pair once", "A PIN, then pinned keys."],
  ["Send", "Notes, links, and files, both ways."],
  ["Point and type", "The phone can drive the Mac."],
  ["Leave, and it locks", "Coming back does not unlock it."],
] as const;

export function KeyWall() {
  const mismatch = useBridge((s) => s.mismatch());
  const trust = useBridge((s) => s.trustKey);
  const reject = useBridge((s) => s.rejectKey);
  const seen = useBridge((s) => s.seenAndroidFp);
  const pinned = useBridge((s) => s.pinnedAndroidFp);
  if (!mismatch) return null;
  return (
    <div className="absolute inset-0 z-30 flex flex-col justify-end bg-bg/92 p-4" role="alertdialog" aria-labelledby="key-title">
      <div className="rounded-xl border border-line bg-elevated p-4">
        <p id="key-title" className="text-base font-medium">
          Peer key changed
        </p>
        <p className="mt-2 text-sm text-muted">
          The phone is presenting a different public key than the one you pinned. Nothing will be sent until you choose.
        </p>
        <dl className="mt-3 space-y-1 font-mono text-xs text-muted">
          <div className="flex justify-between gap-3">
            <dt>Pinned</dt>
            <dd className="text-fg">{formatFingerprint(pinned)}</dd>
          </div>
          <div className="flex justify-between gap-3">
            <dt>Seen now</dt>
            <dd className="text-warn">{formatFingerprint(seen)}</dd>
          </div>
        </dl>
        <div className="mt-4 flex gap-2">
          <Btn variant="primary" className="flex-1" onClick={trust}>
            Trust new key
          </Btn>
          <Btn className="flex-1" onClick={reject}>
            Reject
          </Btn>
        </div>
      </div>
    </div>
  );
}

export function Ask({
  title,
  body,
  confirm,
  onConfirm,
  onClose,
}: {
  title: string;
  body: string;
  confirm: string;
  onConfirm: () => void;
  onClose: () => void;
}) {
  return (
    <div className="absolute inset-0 z-20 flex items-end bg-bg/80 p-3" role="dialog" aria-labelledby="ask-title">
      <div className="w-full rounded-xl border border-line bg-elevated p-4">
        <p id="ask-title" className="text-base font-medium">
          {title}
        </p>
        <p className="mt-2 text-sm leading-relaxed text-muted">{body}</p>
        <div className="mt-4 flex gap-2">
          <Btn variant="primary" className="flex-1" onClick={onConfirm}>
            {confirm}
          </Btn>
          <Btn className="flex-1" onClick={onClose}>
            Not now
          </Btn>
        </div>
      </div>
    </div>
  );
}
