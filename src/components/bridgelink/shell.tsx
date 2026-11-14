import { useEffect, useState } from "react";
import { Toaster, toast } from "sonner";
import { useBridge, DEMO_NOW } from "@/lib/bridgelink/store";
import { GuideDialog, LogDialog } from "./guide";
import { MacPanel } from "./mac";
import { PhonePanel } from "./phone";
import { Btn, Mark } from "./ui";

export function Shell() {
  const hydrated = useBridge((s) => s.hydrated);
  const theme = useBridge((s) => s.theme);
  const setTheme = useBridge((s) => s.setTheme);
  const paired = useBridge((s) => s.paired);
  const tour = useBridge((s) => s.tourDismissed);
  const dismissTour = useBridge((s) => s.dismissTour);
  const heartbeat = useBridge((s) => s.heartbeat);
  const tick = useBridge((s) => s.tick);
  const [clock, setClock] = useState(DEMO_NOW);
  const [tab, setTab] = useState<"phone" | "mac">("phone");
  const [guide, setGuide] = useState(false);
  const [log, setLog] = useState(false);

  useEffect(() => {
    let alive = true;
    const finish = () => {
      if (!alive) return;
      const state = useBridge.getState();
      state.purgeExpired();
      useBridge.setState({
        hydrated: true,
        mirrorApproved: false,
        cameraActive: false,
        ringing: false,
        media: { ...state.media, index: state.media.index ?? 0 },
      });
    };
    const pending = useBridge.persist.rehydrate();
    if (pending && typeof pending.then === "function") pending.then(finish).catch(finish);
    else finish();
    return () => {
      alive = false;
    };
  }, []);

  useEffect(() => {
    if (!hydrated) return;
    document.documentElement.dataset.theme = theme;
    const timer = setInterval(() => {
      setClock(Date.now());
      tick();
      const beat = useBridge.getState().lastHeartbeat;
      if (Date.now() - beat > 15_000) heartbeat();
    }, 1000);
    return () => clearInterval(timer);
  }, [hydrated, theme, tick, heartbeat]);

  useEffect(() => {
    if (!hydrated) return;
    return useBridge.subscribe((state, prev) => {
      if (state.logs.length <= prev.logs.length) return;
      const last = state.logs[state.logs.length - 1];
      if (!last) return;
      if (last.msg.includes("Verification code") || last.level === "error" || last.msg.includes("Mac locked")) {
        toast(last.msg);
      }
    });
  }, [hydrated]);

  return (
    <main className="min-h-dvh bg-bg text-fg">
      <Toaster
        theme={theme}
        position="top-center"
        toastOptions={{
          style: {
            background: "var(--color-panel)",
            color: "var(--color-fg)",
            border: "1px solid var(--color-line)",
          },
        }}
      />
      <header className="flex items-center gap-2 border-b border-line px-3 py-2 sm:px-4">
        <Mark className="size-6 shrink-0" />
        <div className="min-w-0">
          <h1 className="truncate text-sm font-medium">BridgeLink interactive demo (simulated)</h1>
          <p className="text-xs text-subtle">Simulated, not connected to real devices</p>
        </div>
        <div className="ml-auto flex items-center gap-1.5">
          <Btn className="h-11 px-2.5" onClick={() => setTheme(theme === "dark" ? "light" : "dark")} aria-label="Toggle color theme">
            {theme === "dark" ? "Light" : "Dark"}
          </Btn>
          <Btn className="h-11 px-2.5" onClick={() => setLog(true)}>
            Log
          </Btn>
          <Btn className="h-11 px-2.5" variant="primary" onClick={() => setGuide(true)}>
            Guide
          </Btn>
        </div>
      </header>
      {!tour && paired && (
        <div className="flex items-start justify-between gap-3 border-b border-line px-4 py-3 text-sm">
          <p className="text-muted">Sample history is loaded. Forget the device in phone Settings when you want to pair with a PIN.</p>
          <button className="shrink-0 text-fg" onClick={dismissTour}>
            Dismiss
          </button>
        </div>
      )}
      <div className="flex gap-2 border-b border-line px-4 py-2 lg:hidden">
        <button className={tab === "phone" ? "h-11 flex-1 rounded-sm bg-accent text-sm text-accent-fg" : "h-11 flex-1 rounded-sm border border-line text-sm"} onClick={() => setTab("phone")}>
          Phone
        </button>
        <button className={tab === "mac" ? "h-11 flex-1 rounded-sm bg-accent text-sm text-accent-fg" : "h-11 flex-1 rounded-sm border border-line text-sm"} onClick={() => setTab("mac")}>
          Mac
        </button>
      </div>
      <div className="mx-auto grid w-full max-w-6xl gap-6 px-4 py-4 lg:grid-cols-[400px_minmax(0,1fr)] lg:px-6">
        <div className={tab === "mac" ? "hidden lg:block" : "block"}>
          <PhonePanel clock={clock} />
        </div>
        <div className={tab === "phone" ? "hidden lg:block" : "block"}>
          <MacPanel clock={clock} />
        </div>
      </div>
      <GuideDialog open={guide} onOpenChange={setGuide} />
      <LogDialog open={log} onOpenChange={setLog} />
    </main>
  );
}
