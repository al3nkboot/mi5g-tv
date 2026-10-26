import { useEffect, useMemo, useState, type ReactNode } from "react";
import {
  Blocks,
  Camera,
  Check,
  ChevronLeft,
  Gamepad2,
  House,
  Keyboard,
  Lightbulb,
  Moon,
  Power,
  Shield,
  Sparkles,
  Tv,
  Volume2,
  VolumeX,
  Wifi,
  WifiOff,
} from "lucide-react";
import {
  APPS,
  CODES,
  IDEAS,
  KEPT,
  MODULES,
  PLAN,
  TVS,
  healthScore,
  platformLabel,
  type ScreenId,
  type TvApp,
  type TvDevice,
} from "@/components/mi5g/model";

type Job = "restore" | "disable" | "launcher" | "done";
type Anim = "1" | "0.5" | "0";
type AppFilter = "user" | "system";

const NAV: { id: ScreenId; label: string; icon: typeof House }[] = [
  { id: "home", label: "الرئيسية", icon: House },
  { id: "clean", label: "تنظيف", icon: Sparkles },
  { id: "remote", label: "ريموت", icon: Gamepad2 },
  { id: "apps", label: "تطبيقات", icon: Blocks },
  { id: "modules", label: "وحدات", icon: Tv },
];

const JOB_LABEL: Record<Job, string> = {
  restore: "حفظ نقطة الاستعادة",
  disable: "إيقاف الإعلانات والتتبع",
  launcher: "تثبيت الشاشة الهادئة",
  done: "تم",
};

export function Studio() {
  const [screen, setScreen] = useState<ScreenId>("home");
  const [tvId, setTvId] = useState(TVS[0].id);
  const [online, setOnline] = useState<Record<string, boolean>>(() =>
    Object.fromEntries(TVS.map((tv) => [tv.id, tv.online])),
  );
  const [gone, setGone] = useState<Record<string, boolean>>({});
  const [picker, setPicker] = useState(false);
  const [planOn, setPlanOn] = useState<Record<string, Record<string, boolean>>>(() =>
    Object.fromEntries(TVS.map((item) => [item.id, Object.fromEntries(PLAN.map((group) => [group.id, true]))])),
  );
  const [cleaned, setCleaned] = useState<Record<string, boolean>>({});
  const [restore, setRestore] = useState<Record<string, boolean>>({});
  const [ramFreed, setRamFreed] = useState<Record<string, boolean>>({});
  const [dnsSafe, setDnsSafe] = useState<Record<string, boolean>>({});
  const [evening, setEvening] = useState<Record<string, boolean>>({});
  const [adhan, setAdhan] = useState<Record<string, boolean>>({});
  const [anim, setAnim] = useState<Record<string, Anim>>({});
  const [modulesOn, setModulesOn] = useState<Record<string, Record<string, boolean>>>({});
  const [frozen, setFrozen] = useState<Record<string, Record<string, boolean>>>({});
  const [blocked, setBlocked] = useState<Record<string, Record<string, boolean>>>({});
  const [job, setJob] = useState<Job | null>(null);
  const [jobTv, setJobTv] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);
  const [sheetApp, setSheetApp] = useState<TvApp | null>(null);
  const [uninstallFor, setUninstallFor] = useState<TvApp | null>(null);
  const [uninstallWord, setUninstallWord] = useState("");
  const [removed, setRemoved] = useState<Record<string, Record<string, boolean>>>({});
  const [pairFor, setPairFor] = useState<TvDevice | null>(null);
  const [pairCode, setPairCode] = useState("");
  const [appFilter, setAppFilter] = useState<AppFilter>("user");
  const [glass, setGlass] = useState("الشاشة الهادئة");
  const [standby, setStandby] = useState(false);
  const [muted, setMuted] = useState(false);
  const [volume, setVolume] = useState(18);
  const [castText, setCastText] = useState("");
  const [advancedCode, setAdvancedCode] = useState(false);
  const [rawCode, setRawCode] = useState("");
  const [hydrated, setHydrated] = useState(false);

  const household = TVS.filter((item) => !gone[item.id]);
  const tv = household.find((item) => item.id === tvId) ?? household[0] ?? TVS[0];
  const isOnline = online[tv.id] ?? false;
  const android = tv.platform === "android";
  const currentPlan = planOn[tv.id] ?? {};
  const tvModules = modulesOn[tv.id] ?? {};
  const tvFrozen = frozen[tv.id] ?? {};
  const tvBlocked = blocked[tv.id] ?? {};
  const tvRemoved = removed[tv.id] ?? {};
  const score = healthScore({
    platform: tv.platform,
    cleaned: Boolean(cleaned[tv.id]),
    ramFreed: Boolean(ramFreed[tv.id]),
    dnsSafe: Boolean(dnsSafe[tv.id]),
    evening: Boolean(evening[tv.id]),
    online: isOnline,
  });

  useEffect(() => {
    try {
      const raw = localStorage.getItem("mi5g-applied");
      if (!raw) return;
      const saved = JSON.parse(raw) as {
        tvId?: string;
        online?: Record<string, boolean>;
        gone?: Record<string, boolean>;
      };
      if (saved.tvId && TVS.some((item) => item.id === saved.tvId)) setTvId(saved.tvId);
      if (saved.online) setOnline((prev) => ({ ...prev, ...saved.online }));
      if (saved.gone) setGone(saved.gone);
    } catch {
      /* ignore broken local preview state */
    } finally {
      setHydrated(true);
    }
  }, []);

  useEffect(() => {
    if (!hydrated) return;
    localStorage.setItem("mi5g-applied", JSON.stringify({ tvId, online, gone }));
  }, [hydrated, tvId, online, gone]);

  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(null), 2800);
    return () => window.clearTimeout(timer);
  }, [toast]);

  useEffect(() => {
    if (!job || job === "done" || !jobTv) return;
    const order: Job[] = ["restore", "disable", "launcher", "done"];
    const next = order[order.indexOf(job) + 1];
    const id = jobTv;
    const timer = window.setTimeout(() => {
      if (next === "done") {
        setRestore((prev) => ({ ...prev, [id]: true }));
        setCleaned((prev) => ({ ...prev, [id]: true }));
        setGlass("الشاشة الهادئة جاهزة");
      }
      setJob(next);
    }, 700);
    return () => window.clearTimeout(timer);
  }, [job, jobTv]);

  const selectedCount = useMemo(
    () => PLAN.filter((group) => currentPlan[group.id] !== false).reduce((sum, group) => sum + group.count, 0),
    [currentPlan],
  );

  function notify(message: string) {
    setToast(message);
  }

  function forgetTv(id: string) {
    const left = household.filter((item) => item.id !== id);
    if (left.length === 0) {
      notify("أبقِ تلفازاً واحداً في البيت");
      return;
    }
    setGone((prev) => ({ ...prev, [id]: true }));
    if (tv.id === id) setTvId(left[0].id);
    setPicker(false);
    notify("نُسي هذا التلفاز. الباقي وخططه كما هي.");
  }

  function startClean() {
    if (!android || !isOnline || (job && job !== "done")) return;
    if (selectedCount === 0) {
      notify("لا يوجد ما يُغيَّر");
      return;
    }
    setJobTv(tv.id);
    setJob("restore");
    setScreen("clean");
  }

  function toggleEvening() {
    const next = !evening[tv.id];
    setEvening((prev) => ({ ...prev, [tv.id]: next }));
    if (next) {
      setAnim((prev) => ({ ...prev, [tv.id]: "0.5" }));
      setDnsSafe((prev) => ({ ...prev, [tv.id]: true }));
      setModulesOn((prev) => ({ ...prev, [tv.id]: { ...(prev[tv.id] ?? {}), dns: true } }));
      notify("وضع المساء على هذا التلفاز: قوائم 0.5× و DNS عائلي. الأذان لم يُشغَّل.");
    } else {
      notify("أُلغي وضع المساء على هذا التلفاز");
    }
  }

  function applyDnsSafe() {
    setDnsSafe((prev) => ({ ...prev, [tv.id]: true }));
    setModulesOn((prev) => ({ ...prev, [tv.id]: { ...(prev[tv.id] ?? {}), dns: true } }));
    notify(
      tv.dnsPort === "blocked"
        ? "الوضع الآمن: 94.140.14.14 — منفذ 853 لن يُستخدم"
        : "منفذ 853 متاح. ثُبّت DNS المشفر.",
    );
  }

  function keyLabel(label: string) {
    if (standby) return;
    setGlass(label);
  }

  const ramLabel = ramFreed[tv.id] ? "0.9 / 2.0 غ.ب" : "1.4 / 2.0 غ.ب";
  const bloatLabel = cleaned[tv.id] ? "0 متبقٍ" : "23 جاهز للإيقاف";

  return (
    <div className="min-h-screen bg-bg text-fg">
      <div className="mx-auto flex min-h-screen w-full max-w-md flex-col">
        <header className="flex items-start justify-between gap-3 px-4 pt-4">
          <div className="min-w-0">
            <p className="text-sm font-semibold tracking-wide text-primary">Mi5G TV</p>
            <button
              type="button"
              className="mt-1 flex max-w-full items-center gap-1 text-start"
              onClick={() => setPicker(true)}
            >
              <span className="truncate text-lg font-semibold">{tv.room}</span>
              <ChevronLeft className="size-4 shrink-0 text-muted" />
            </button>
            <p className="truncate text-sm text-muted">
              {tv.model}
              {" · "}
              {platformLabel(tv.platform)}
              {" · "}
              {isOnline ? "متصل" : "غير متصل"}
            </p>
          </div>
          <button
            type="button"
            onClick={() => setScreen("ideas")}
            className="flex h-11 items-center gap-1 rounded-full border border-border bg-surface px-3 text-sm text-fg"
          >
            <Lightbulb className="size-4 text-accent" />
            الجديد
          </button>
        </header>

        <p className="mx-4 mt-3 rounded-lg border border-border bg-surface px-3 py-2 text-sm text-muted">
          الاقتراحات الثمانية مفعّلة. المعاينة لا تتصل بتلفاز حقيقي.
        </p>

        <main className="flex-1 px-4 pb-28 pt-4">
          {screen === "home" ? (
            <HomeScreen
              android={android}
              platform={tv.platform}
              dnsPort={tv.dnsPort}
              online={isOnline}
              score={score}
              ramLabel={ramLabel}
              bloatLabel={bloatLabel}
              storage="4.6 / 8 غ.ب"
              cleaned={Boolean(cleaned[tv.id])}
              dnsSafe={Boolean(dnsSafe[tv.id])}
              evening={Boolean(evening[tv.id])}
              restore={Boolean(restore[tv.id])}
              onClean={startClean}
              onEvening={toggleEvening}
              onDns={applyDnsSafe}
              onPair={() => {
                setPairCode("");
                setPairFor(tv);
              }}
              onOpen={setScreen}
            />
          ) : null}

          {screen === "clean" ? (
            <CleanScreen
              android={android}
              online={isOnline}
              planOn={currentPlan}
              setPlanOn={(next) => setPlanOn((prev) => ({ ...prev, [tv.id]: next }))}
              selectedCount={selectedCount}
              job={jobTv === tv.id ? job : null}
              cleaned={Boolean(cleaned[tv.id])}
              onStart={startClean}
            />
          ) : null}

          {screen === "remote" ? (
            <RemoteScreen
              android={android}
              online={isOnline}
              standby={standby}
              muted={muted}
              volume={volume}
              glass={glass}
              anim={anim[tv.id] ?? "1"}
              castText={castText}
              onCastText={setCastText}
              onAnim={(value) => {
                setAnim((prev) => ({ ...prev, [tv.id]: value }));
                notify(value === "1" ? "سرعة القوائم: افتراضي" : value === "0.5" ? "سرعة القوائم: 0.5×" : "سرعة القوائم: فوري");
              }}
              onKey={keyLabel}
              onStandby={() => {
                setStandby((value) => !value);
                setGlass(standby ? "عادت الشاشة" : "وضع الاستعداد");
              }}
              onMute={() => {
                setMuted((value) => !value);
                keyLabel(muted ? "الصوت عاد" : "كتم");
              }}
              onVolume={(delta) => {
                setVolume((value) => Math.max(0, Math.min(40, value + delta)));
                setMuted(false);
                keyLabel("الصوت");
              }}
              onSend={() => {
                const text = castText.trim();
                if (!text) return;
                setGlass(text);
                notify("أُرسل النص إلى المعاينة");
              }}
              onShot={() => notify("حُفظت لقطة المعاينة على هذا الجهاز")}
              onRam={() => {
                setRamFreed((prev) => ({ ...prev, [tv.id]: true }));
                notify("أُغلقت تطبيقات الخلفية في المعاينة");
              }}
            />
          ) : null}

          {screen === "apps" ? (
            <AppsScreen
              android={android}
              filter={appFilter}
              onFilter={setAppFilter}
              frozen={tvFrozen}
              blocked={tvBlocked}
              removed={tvRemoved}
              onOpen={setSheetApp}
            />
          ) : null}

          {screen === "modules" ? (
            <ModulesScreen
              android={android}
              online={isOnline}
              modulesOn={tvModules}
              dnsSafe={Boolean(dnsSafe[tv.id])}
              dnsPort={tv.dnsPort}
              adhan={Boolean(adhan[tv.id])}
              advanced={advancedCode}
              rawCode={rawCode}
              onAdvanced={setAdvancedCode}
              onRaw={setRawCode}
              onAdhan={() => {
                setAdhan((prev) => ({ ...prev, [tv.id]: !prev[tv.id] }));
                notify(adhan[tv.id] ? "أُوقف الأذان على هذا التلفاز" : "الأذان خيار مستقل عن وضع المساء");
              }}
              onToggle={(id) => {
                if (id === "dns" && tv.dnsPort === "blocked" && !dnsSafe[tv.id]) {
                  applyDnsSafe();
                  return;
                }
                setModulesOn((prev) => {
                  const cur = prev[tv.id] ?? {};
                  const nextOn = !cur[id];
                  if (id === "dns" && nextOn) setDnsSafe((safe) => ({ ...safe, [tv.id]: true }));
                  if (id === "prayer" && !nextOn) setAdhan((prevAdhan) => ({ ...prevAdhan, [tv.id]: false }));
                  return { ...prev, [tv.id]: { ...cur, [id]: nextOn } };
                });
              }}
              onInstallCode={(name) => notify(`طُلب تثبيت ${name} في المعاينة`)}
            />
          ) : null}

          {screen === "ideas" ? (
            <IdeasScreen onOpen={setScreen} />
          ) : null}
        </main>

        {screen !== "ideas" ? (
          <nav className="fixed inset-x-0 bottom-0 z-20 mx-auto flex w-full max-w-md border-t border-border bg-surface px-1 pb-3 pt-1">
            {NAV.map((item) => {
              const Icon = item.icon;
              const active = screen === item.id;
              return (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => setScreen(item.id)}
                  aria-current={active ? "page" : undefined}
                  className={`flex min-h-12 flex-1 flex-col items-center justify-center gap-0.5 rounded-lg text-xs ${
                    active ? "text-primary" : "text-muted"
                  }`}
                >
                  <Icon className="size-5" />
                  {item.label}
                </button>
              );
            })}
          </nav>
        ) : (
          <div className="fixed inset-x-0 bottom-0 z-20 mx-auto w-full max-w-md border-t border-border bg-surface p-3">
            <button
              type="button"
              onClick={() => setScreen("home")}
              className="h-12 w-full rounded-xl bg-primary font-semibold text-primary-fg"
            >
              العودة للوحة
            </button>
          </div>
        )}
      </div>

      {toast ? (
        <div className="fixed inset-x-0 bottom-24 z-40 mx-auto w-[min(100%-2rem,24rem)] rounded-xl bg-fg px-4 py-3 text-sm text-bg">
          {toast}
        </div>
      ) : null}

      {picker ? (
        <Sheet title="أجهزة البيت" onClose={() => setPicker(false)}>
          <ul className="space-y-2">
            {household.map((item) => (
              <li key={item.id} className="rounded-xl border border-border bg-surface-2">
                <button
                  type="button"
                  onClick={() => {
                    setTvId(item.id);
                    setPicker(false);
                    setGlass("الشاشة الهادئة");
                    setStandby(false);
                  }}
                  className="flex w-full items-center justify-between gap-3 px-3 py-3 text-start"
                >
                  <span>
                    <span className="block font-semibold">{item.room}</span>
                    <span className="block text-sm text-muted">
                      {item.model} · {platformLabel(item.platform)} · {item.host}
                    </span>
                    <span className="block text-sm text-muted">
                      {restore[item.id] ? "نقطة استعادة محفوظة" : "بلا نقطة استعادة"}
                      {cleaned[item.id] ? " · نُظّف" : ""}
                    </span>
                  </span>
                  {online[item.id] ? <Wifi className="size-4 text-primary" /> : <WifiOff className="size-4 text-accent" />}
                </button>
                <div className="px-3 pb-3">
                  <button type="button" onClick={() => forgetTv(item.id)} className="h-11 text-sm text-accent">
                    انسَ هذا التلفاز
                  </button>
                </div>
              </li>
            ))}
          </ul>
        </Sheet>
      ) : null}

      {pairFor ? (
        <Sheet title="رمز الإقران" onClose={() => setPairFor(null)}>
          <p className="text-sm text-muted">
            من خيارات المطوّر على {pairFor.model} فعّل التصحيح اللاسلكي. في المعاينة الرمز هو 482916 والمنفذ
            5555.
          </p>
          <label className="mt-3 block text-sm" htmlFor="pair-code">
            الرمز المكوّن من 6 أرقام
          </label>
          <input
            id="pair-code"
            inputMode="numeric"
            value={pairCode}
            onChange={(event) => setPairCode(event.target.value.replace(/\D/g, "").slice(0, 6))}
            className="mt-1 h-12 w-full rounded-xl border border-border bg-bg px-3 tabular-nums text-fg"
          />
          <button
            type="button"
            disabled={pairCode !== "482916"}
            onClick={() => {
              setOnline((prev) => ({ ...prev, [pairFor.id]: true }));
              setPairFor(null);
              notify("تم الإقران في المعاينة");
            }}
            className="mt-3 h-12 w-full rounded-xl bg-primary font-semibold text-primary-fg disabled:opacity-40"
          >
            إقران
          </button>
        </Sheet>
      ) : null}

      {sheetApp ? (
        <Sheet title={sheetApp.name} onClose={() => setSheetApp(null)}>
          <p className="text-sm text-muted">{sheetApp.detail}</p>
          {sheetApp.protected ? (
            <p className="mt-3 rounded-xl border border-border bg-surface-2 px-3 py-3 text-sm">
              تطبيق بث محمي. يمكن فتحه أو إيقافه، ولا يُجمَّد ولا يُحذف.
            </p>
          ) : null}
          <div className="mt-3 grid grid-cols-2 gap-2">
            <Action
              label="فتح"
              onClick={() => {
                setGlass(sheetApp.name);
                setSheetApp(null);
                notify(`فُتح ${sheetApp.name} في المعاينة`);
              }}
            />
            <Action
              label="إيقاف إجباري"
              onClick={() => {
                setSheetApp(null);
                notify(`أُوقف ${sheetApp.name}`);
              }}
            />
            {sheetApp.kind === "user" && !sheetApp.protected ? (
              <Action
                label={tvBlocked[sheetApp.id] ? "السماح بالخلفية" : "منع الخلفية"}
                onClick={() => {
                  setBlocked((prev) => ({
                    ...prev,
                    [tv.id]: { ...(prev[tv.id] ?? {}), [sheetApp.id]: !prev[tv.id]?.[sheetApp.id] },
                  }));
                  setSheetApp(null);
                }}
              />
            ) : null}
            {sheetApp.kind === "user" ? (
              <Action
                label="مسح الكاش"
                onClick={() => {
                  setRamFreed((prev) => ({ ...prev, [tv.id]: true }));
                  setSheetApp(null);
                  notify("مُسح الكاش في المعاينة");
                }}
              />
            ) : null}
            {sheetApp.kind === "system" ? (
              <Action
                label={tvFrozen[sheetApp.id] ? "فك التجميد" : "تجميد"}
                onClick={() => {
                  const wasFrozen = Boolean(tvFrozen[sheetApp.id]);
                  setFrozen((prev) => ({
                    ...prev,
                    [tv.id]: { ...(prev[tv.id] ?? {}), [sheetApp.id]: !wasFrozen },
                  }));
                  setSheetApp(null);
                  notify(wasFrozen ? "عاد التطبيق" : "جُمّد التطبيق ويمكن التراجع");
                }}
              />
            ) : null}
          </div>
          {sheetApp.kind === "system" ? (
            <button
              type="button"
              onClick={() => {
                setUninstallWord("");
                setUninstallFor(sheetApp);
                setSheetApp(null);
              }}
              className="mt-3 h-12 w-full rounded-xl border border-accent text-accent"
            >
              حذف نهائي…
            </button>
          ) : null}
        </Sheet>
      ) : null}

      {uninstallFor ? (
        <Sheet title={`حذف ${uninstallFor.name}`} onClose={() => setUninstallFor(null)}>
          <p className="text-sm text-muted">
            هذا حذف للمستخدم، لا زر تراجع سريع. التجمييد هو الخيار الآمن. للمتابعة اكتب «احذف».
            {restore[tv.id] ? " نقطة الاستعادة محفوظة." : " احفظ نقطة استعادة من التنظيف أولاً إن استطعت."}
          </p>
          <input
            value={uninstallWord}
            onChange={(event) => setUninstallWord(event.target.value)}
            className="mt-3 h-12 w-full rounded-xl border border-border bg-bg px-3 text-fg"
            aria-label="اكتب احذف للتأكيد"
          />
          <button
            type="button"
            disabled={uninstallWord.trim() !== "احذف" || !restore[tv.id]}
            onClick={() => {
              setRemoved((prev) => ({
                ...prev,
                [tv.id]: { ...(prev[tv.id] ?? {}), [uninstallFor.id]: true },
              }));
              setUninstallFor(null);
              notify("حُذف في المعاينة فقط. في التطبيق الحقيقي لن يُحذف بلا هذه الخطوة.");
            }}
            className="mt-3 h-12 w-full rounded-xl bg-accent font-semibold text-accent-fg disabled:opacity-40"
          >
            {restore[tv.id] ? "تأكيد الحذف" : "يلزم نقطة استعادة أولاً"}
          </button>
        </Sheet>
      ) : null}
    </div>
  );
}

function HomeScreen(props: {
  android: boolean;
  platform: "android" | "tizen" | "webos";
  dnsPort: "blocked" | "open";
  online: boolean;
  score: number | null;
  ramLabel: string;
  bloatLabel: string;
  storage: string;
  cleaned: boolean;
  dnsSafe: boolean;
  evening: boolean;
  restore: boolean;
  onClean: () => void;
  onEvening: () => void;
  onDns: () => void;
  onPair: () => void;
  onOpen: (screen: ScreenId) => void;
}) {
  if (!props.android) {
    const webos = props.platform === "webos";
    return (
      <section className="space-y-3">
        <h1 className="text-2xl font-semibold">{webos ? "هذا التلفاز webOS" : "هذا التلفاز تايزن"}</h1>
        <p className="text-sm text-muted">
          {webos ? "LG webOS" : "سامسونج تايزن"} لا يقبل تنظيف ADB. الدليل فقط، بلا تعطيل ولا حذف ولا وحدات.
        </p>
        <ol className="space-y-2 text-sm">
          <li className="rounded-xl border border-border bg-surface px-3 py-3">
            1. {webos ? "استخدم ريموت LG أو تطبيق ThinQ." : "استخدم ريموت سامسونج أو SmartThings."}
          </li>
          <li className="rounded-xl border border-border bg-surface px-3 py-3">
            2. إن كان جهاز أندرويد على HDMI، بدّل إليه من قائمة الأجهزة. خطته لا تُمس.
          </li>
          <li className="rounded-xl border border-border bg-surface px-3 py-3">3. لا أزرار إيقاف أو حذف لتطبيقات المصنع.</li>
        </ol>
      </section>
    );
  }

  const ring = props.score ?? 0;
  const dash = 2 * Math.PI * 52;
  const offset = dash - (ring / 100) * dash;

  return (
    <section className="space-y-3">
      <div className="flex items-center gap-4 rounded-2xl border border-border bg-surface p-4">
        <svg viewBox="0 0 120 120" className="size-28 shrink-0" aria-hidden>
          <circle cx="60" cy="60" r="52" fill="none" className="stroke-border" strokeWidth="8" />
          <circle
            cx="60"
            cy="60"
            r="52"
            fill="none"
            className="stroke-primary"
            strokeWidth="8"
            strokeLinecap="round"
            strokeDasharray={dash}
            strokeDashoffset={offset}
            transform="rotate(-90 60 60)"
          />
          <text
            x="60"
            y="66"
            textAnchor="middle"
            className="fill-fg text-2xl font-semibold"
            style={{ fontSize: 28 }}
          >
            {ring}
          </text>
        </svg>
        <div className="min-w-0">
          <h1 className="text-xl font-semibold">صحة التلفاز</h1>
          <p className="text-sm text-muted">
            {props.cleaned ? "نُظّف مع نقطة استعادة." : "لم يُنظَّف بعد. التطبيقات الأساسية تبقى."}
          </p>
          <p className="mt-1 text-sm text-muted">
            {props.restore ? "نقطة الاستعادة محفوظة لهذا الجهاز." : "لا نقطة استعادة بعد."}
          </p>
        </div>
      </div>

      <div className="grid grid-cols-3 gap-2">
        <Stat label="الذاكرة" value={props.ramLabel} />
        <Stat label="التخزين" value={props.storage} />
        <Stat label="الإيقاف" value={props.bloatLabel} />
      </div>

      {props.online ? (
        <button
          type="button"
          onClick={props.onClean}
          className="h-12 w-full rounded-xl bg-primary font-semibold text-primary-fg"
        >
          {props.cleaned ? "مراجعة الخطة" : "ابدأ التنظيف الآمن"}
        </button>
      ) : (
        <button
          type="button"
          onClick={props.onPair}
          className="h-12 w-full rounded-xl bg-primary font-semibold text-primary-fg"
        >
          إدخال رمز الإقران
        </button>
      )}

      {props.dnsPort === "blocked" && !props.dnsSafe ? (
        <div className="rounded-xl border border-accent bg-surface px-3 py-3">
          <p className="font-semibold text-accent">منفذ 853 محجوب على هذا الراوتر</p>
          <p className="mt-1 text-sm text-muted">
            تثبيت DNS المشفر قد يقطع النت على شاومي. الوضع الآمن يكتب 94.140.14.14 بلا منفذ 853.
          </p>
          <button
            type="button"
            onClick={props.onDns}
            className="mt-3 h-11 rounded-xl bg-accent px-4 font-semibold text-accent-fg"
          >
            تطبيق الوضع الآمن
          </button>
        </div>
      ) : null}
      {props.dnsPort === "open" && !props.dnsSafe ? (
        <div className="rounded-xl border border-border bg-surface px-3 py-3">
          <p className="font-semibold">منفذ 853 متاح</p>
          <p className="mt-1 text-sm text-muted">التثبيت المشفر لن يُحوَّل إلى الوضع الآمن على هذا الراوتر.</p>
          <button
            type="button"
            onClick={props.onDns}
            className="mt-3 h-11 rounded-xl bg-primary px-4 font-semibold text-primary-fg"
          >
            تثبيت DNS المشفر
          </button>
        </div>
      ) : null}
      {props.dnsSafe ? (
        <p className="flex items-center gap-2 rounded-xl border border-border bg-surface px-3 py-3 text-sm">
          <Shield className="size-4 text-primary" />
          {props.dnsPort === "blocked" ? "DNS في الوضع الآمن. لن يُستخدم منفذ 853." : "DNS مشفر عبر منفذ 853."}
        </p>
      ) : null}

      <button
        type="button"
        onClick={props.onEvening}
        className="flex w-full items-center justify-between gap-3 rounded-xl border border-border bg-surface px-3 py-3 text-start"
      >
        <span className="flex items-center gap-2">
          <Moon className="size-4 text-accent" />
          <span>
            <span className="block font-semibold">وضع المساء</span>
            <span className="block text-sm text-muted">قوائم 0.5× و DNS عائلي. بلا أذان إجباري.</span>
          </span>
        </span>
        <span
          dir="ltr"
          className={`flex h-7 w-12 shrink-0 items-center rounded-full px-1 ${props.evening ? "justify-end bg-primary" : "justify-start bg-surface-2"}`}
        >
          <span className={`size-5 rounded-full ${props.evening ? "bg-primary-fg" : "bg-muted"}`} />
        </span>
      </button>

      <div className="grid grid-cols-3 gap-2">
        <Mini label="ريموت" onClick={() => props.onOpen("remote")} />
        <Mini label="تطبيقات" onClick={() => props.onOpen("apps")} />
        <Mini label="وحدات" onClick={() => props.onOpen("modules")} />
      </div>

      <GlassDock />
    </section>
  );
}

function CleanScreen(props: {
  android: boolean;
  online: boolean;
  planOn: Record<string, boolean>;
  setPlanOn: (value: Record<string, boolean>) => void;
  selectedCount: number;
  job: Job | null;
  cleaned: boolean;
  onStart: () => void;
}) {
  if (!props.android) {
    return <p className="text-sm text-muted">لا خطة تنظيف على أنظمة غير أندرويد.</p>;
  }

  return (
    <section className="space-y-3">
      <h1 className="text-2xl font-semibold">خطة التنظيف</h1>
      <p className="text-sm text-muted">
        يُحفَظ رجوع كامل قبل أي إيقاف. تطبيقاتك لا تُحذف.
        {props.online ? "" : " التلفاز غير متصل."}
      </p>
      <ul className="space-y-2">
        {PLAN.map((group) => {
          const on = props.planOn[group.id];
          return (
            <li key={group.id}>
              <button
                type="button"
                onClick={() => props.setPlanOn({ ...props.planOn, [group.id]: !on })}
                className="flex w-full items-start gap-3 rounded-xl border border-border bg-surface px-3 py-3 text-start"
              >
                <span
                  className={`mt-0.5 grid size-6 shrink-0 place-items-center rounded-md ${on ? "bg-primary text-primary-fg" : "border border-border"}`}
                >
                  {on ? <Check className="size-4" /> : null}
                </span>
                <span>
                  <span className="block font-semibold">
                    {group.title}
                    <span className="tabular-nums text-muted"> · {group.count}</span>
                  </span>
                  <span className="block text-sm text-muted">{group.detail}</span>
                </span>
              </button>
            </li>
          );
        })}
      </ul>
      <p className="text-sm text-muted">يبقى: {KEPT.join("، ")}</p>
      {props.job && props.job !== "done" ? (
        <p className="rounded-xl border border-primary bg-surface px-3 py-3 text-sm">{JOB_LABEL[props.job]}…</p>
      ) : null}
      {props.cleaned ? (
        <p className="rounded-xl border border-border bg-surface px-3 py-3 text-sm">
          اكتمل التنظيف في المعاينة، ونقطة الاستعادة محفوظة لهذا التلفاز فقط.
        </p>
      ) : null}
      <button
        type="button"
        disabled={!props.online || Boolean(props.job && props.job !== "done") || props.selectedCount === 0}
        onClick={props.onStart}
        className="h-12 w-full rounded-xl bg-primary font-semibold text-primary-fg disabled:opacity-40"
      >
        {props.selectedCount === 0
          ? "لا يوجد ما يُغيَّر"
          : `تنظيف ${props.selectedCount} عنصراً`}
      </button>
    </section>
  );
}

function RemoteScreen(props: {
  android: boolean;
  online: boolean;
  standby: boolean;
  muted: boolean;
  volume: number;
  glass: string;
  anim: Anim;
  castText: string;
  onCastText: (value: string) => void;
  onAnim: (value: Anim) => void;
  onKey: (label: string) => void;
  onStandby: () => void;
  onMute: () => void;
  onVolume: (delta: number) => void;
  onSend: () => void;
  onShot: () => void;
  onRam: () => void;
}) {
  if (!props.android || !props.online) {
    return <p className="text-sm text-muted">الريموت يظهر بعد اتصال أندرويد. اختر جهازاً متصلاً من الأعلى.</p>;
  }

  return (
    <section className="space-y-3">
      <h1 className="text-2xl font-semibold">الريموت</h1>
      <div className="relative overflow-hidden rounded-2xl border border-border bg-surface px-4 py-8 text-center">
        <p className="text-sm text-muted">ما يصله التلفاز</p>
        <p className="mt-1 text-lg font-semibold">{props.standby ? "الشاشة مطفأة" : props.glass}</p>
        <p className="mt-2 tabular-nums text-sm text-muted">
          الصوت {props.muted ? "مكتوم" : props.volume}
        </p>
      </div>
      <div className="mx-auto grid w-48 grid-cols-3 gap-2" dir="ltr">
        <span />
        <Pad label="فوق" onClick={() => props.onKey("أعلى")} />
        <span />
        <Pad label="يسار" onClick={() => props.onKey("يسار")} />
        <Pad label="موافق" onClick={() => props.onKey("موافق")} primary />
        <Pad label="يمين" onClick={() => props.onKey("يمين")} />
        <span />
        <Pad label="تحت" onClick={() => props.onKey("أسفل")} />
        <span />
      </div>
      <div className="grid grid-cols-4 gap-2">
        <IconButton label="رجوع" onClick={() => props.onKey("رجوع")} icon={<ChevronLeft className="size-4" />} />
        <IconButton label="الرئيسية" onClick={() => props.onKey("الرئيسية")} icon={<House className="size-4" />} />
        <IconButton
          label={props.muted ? "صوت" : "كتم"}
          onClick={props.onMute}
          icon={props.muted ? <Volume2 className="size-4" /> : <VolumeX className="size-4" />}
        />
        <IconButton label="طاقة" onClick={props.onStandby} icon={<Power className="size-4" />} />
      </div>
      <div className="grid grid-cols-2 gap-2">
        <button type="button" onClick={() => props.onVolume(1)} className="h-11 rounded-xl border border-border bg-surface">
          رفع الصوت
        </button>
        <button type="button" onClick={() => props.onVolume(-1)} className="h-11 rounded-xl border border-border bg-surface">
          خفض الصوت
        </button>
      </div>
      <div className="grid grid-cols-3 gap-2">
        {(["1", "0.5", "0"] as Anim[]).map((value) => (
          <button
            key={value}
            type="button"
            onClick={() => props.onAnim(value)}
            className={`h-11 rounded-xl border ${props.anim === value ? "border-primary bg-primary text-primary-fg" : "border-border bg-surface"}`}
          >
            {value === "1" ? "1×" : value === "0.5" ? "0.5×" : "فوري"}
          </button>
        ))}
      </div>
      <label className="block text-sm" htmlFor="cast-text">
        نص إلى التلفاز
      </label>
      <div className="flex gap-2">
        <input
          id="cast-text"
          value={props.castText}
          onChange={(event) => props.onCastText(event.target.value)}
          className="h-12 min-w-0 flex-1 rounded-xl border border-border bg-surface px-3 text-fg"
        />
        <button type="button" onClick={props.onSend} className="h-12 rounded-xl bg-primary px-4 text-primary-fg">
          <Keyboard className="size-4" />
          <span className="sr-only">إرسال</span>
        </button>
      </div>
      <div className="grid grid-cols-2 gap-2">
        <button type="button" onClick={props.onShot} className="flex h-11 items-center justify-center gap-2 rounded-xl border border-border bg-surface">
          <Camera className="size-4" />
          لقطة
        </button>
        <button type="button" onClick={props.onRam} className="h-11 rounded-xl border border-border bg-surface">
          تفريغ الذاكرة
        </button>
      </div>
    </section>
  );
}

function AppsScreen(props: {
  android: boolean;
  filter: AppFilter;
  onFilter: (filter: AppFilter) => void;
  frozen: Record<string, boolean>;
  blocked: Record<string, boolean>;
  removed: Record<string, boolean>;
  onOpen: (app: TvApp) => void;
}) {
  if (!props.android) {
    return <p className="text-sm text-muted">مدير التطبيقات لأجهزة أندرويد فقط.</p>;
  }
  const list = APPS.filter((app) => app.kind === props.filter && !props.removed[app.id]);
  return (
    <section className="space-y-3">
      <h1 className="text-2xl font-semibold">تطبيقات التلفاز</h1>
      <div className="grid grid-cols-2 gap-2">
        <FilterButton active={props.filter === "user"} label="تطبيقاتي" onClick={() => props.onFilter("user")} />
        <FilterButton active={props.filter === "system"} label="المصنع" onClick={() => props.onFilter("system")} />
      </div>
      <p className="text-sm text-muted">
        {props.filter === "user"
          ? "فتح، إيقاف، خلفية، كاش. المحمي بلا حذف."
          : "التجمييد أولاً. الحذف النهائي خلف تأكيد مكتوب ونقطة استعادة."}
      </p>
      <ul className="space-y-2">
        {list.map((app) => (
          <li key={app.id}>
            <button
              type="button"
              onClick={() => props.onOpen(app)}
              className="flex w-full items-center justify-between gap-3 rounded-xl border border-border bg-surface px-3 py-3 text-start"
            >
              <span>
                <span className="block font-semibold">{app.name}</span>
                <span className="block text-sm text-muted">
                  {props.frozen[app.id] ? "مجمّد" : props.blocked[app.id] ? "الخلفية ممنوعة" : app.detail}
                </span>
              </span>
              <ChevronLeft className="size-4 text-muted" />
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}

function ModulesScreen(props: {
  android: boolean;
  online: boolean;
  modulesOn: Record<string, boolean>;
  dnsSafe: boolean;
  dnsPort: "blocked" | "open";
  adhan: boolean;
  advanced: boolean;
  rawCode: string;
  onAdvanced: (value: boolean) => void;
  onRaw: (value: string) => void;
  onAdhan: () => void;
  onToggle: (id: string) => void;
  onInstallCode: (name: string) => void;
}) {
  if (!props.android) {
    return <p className="text-sm text-muted">الوحدات المدمجة تُثبَّت على أندرويد فقط.</p>;
  }
  return (
    <section className="space-y-3">
      <h1 className="text-2xl font-semibold">الوحدات</h1>
      <ul className="space-y-2">
        {MODULES.map((mod) => {
          const on = Boolean(props.modulesOn[mod.id]);
          return (
            <li key={mod.id} className="rounded-xl border border-border bg-surface px-3 py-3">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="font-semibold">{mod.title}</p>
                  <p className="text-sm text-muted">{mod.detail}</p>
                  {mod.id === "dns" ? (
                    <p className="mt-1 text-sm text-accent">
                      {props.dnsPort === "blocked"
                        ? props.dnsSafe
                          ? "الوضع الآمن مفعّل: 94.140.14.14"
                          : "المنفذ 853 محجوب. التثبيت يختار الوضع الآمن أولاً."
                        : props.dnsSafe
                          ? "المنفذ 853 متاح واستُخدم."
                          : "المنفذ 853 متاح قبل التثبيت."}
                    </p>
                  ) : null}
                  {mod.id === "prayer" && on ? (
                    <button type="button" onClick={props.onAdhan} className="mt-2 h-11 text-sm text-primary">
                      الأذان {props.adhan ? "يعمل" : "متوقف"} · مستقل عن وضع المساء
                    </button>
                  ) : null}
                </div>
                <button
                  type="button"
                  disabled={!props.online}
                  onClick={() => props.onToggle(mod.id)}
                  className={`h-11 shrink-0 rounded-xl px-3 text-sm font-semibold disabled:opacity-40 ${on ? "bg-surface-2 text-fg" : "bg-primary text-primary-fg"}`}
                >
                  {on ? "إزالة" : "تثبيت"}
                </button>
              </div>
            </li>
          );
        })}
      </ul>
      <h2 className="pt-2 text-lg font-semibold">كتالوج Downloader</h2>
      <p className="text-sm text-muted">خارج المتجر الرسمي. راجع المصدر قبل التثبيت. الكود الحر تحت «متقدم».</p>
      <ul className="space-y-2">
        {CODES.map((code) => (
          <li key={code.id} className="flex items-center justify-between gap-3 rounded-xl border border-border bg-surface px-3 py-3">
            <span>
              <span className="block font-semibold">{code.name}</span>
              <span className="block text-sm tabular-nums text-muted">
                {code.id} · {code.note}
              </span>
            </span>
            <button
              type="button"
              disabled={!props.online}
              onClick={() => props.onInstallCode(code.name)}
              className="h-11 rounded-xl bg-primary px-3 text-sm font-semibold text-primary-fg disabled:opacity-40"
            >
              تثبيت
            </button>
          </li>
        ))}
      </ul>
      <button type="button" onClick={() => props.onAdvanced(!props.advanced)} className="text-sm text-primary">
        {props.advanced ? "إخفاء الكود الحر" : "متقدم: كود حر"}
      </button>
      {props.advanced ? (
        <div className="flex gap-2">
          <input
            value={props.rawCode}
            onChange={(event) => props.onRaw(event.target.value.replace(/\D/g, "").slice(0, 8))}
            inputMode="numeric"
            aria-label="كود Downloader"
            className="h-12 min-w-0 flex-1 rounded-xl border border-border bg-surface px-3 tabular-nums text-fg"
          />
          <button
            type="button"
            disabled={props.rawCode.length < 4 || !props.online}
            onClick={() => props.onInstallCode(`الكود ${props.rawCode}`)}
            className="h-12 rounded-xl border border-border px-3 disabled:opacity-40"
          >
            فتح
          </button>
        </div>
      ) : null}
    </section>
  );
}

function IdeasScreen(props: { onOpen: (screen: ScreenId) => void }) {
  return (
    <section className="space-y-3">
      <h1 className="text-2xl font-semibold">ما طُبّق</h1>
      <p className="text-sm text-muted">
        الثمانية تعمل الآن في اللوحة والتنظيف والريموت والتطبيقات والوحدات، ولكل تلفاز حالته.
      </p>
      <ul className="space-y-3">
        {IDEAS.map((idea, index) => (
          <li key={idea.id} className="rounded-xl border border-border bg-surface px-3 py-3">
            <p className="flex items-center gap-2 text-sm text-primary">
              <Check className="size-4" />
              <span className="tabular-nums">{index + 1} مطبّق</span>
            </p>
            <h2 className="text-lg font-semibold">{idea.title}</h2>
            <p className="mt-1 text-sm text-muted">{idea.body}</p>
            <button
              type="button"
              onClick={() => props.onOpen(idea.tryScreen)}
              className="mt-3 h-11 rounded-xl bg-primary px-3 text-sm font-semibold text-primary-fg"
            >
              افتح
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}

function Stat(props: { label: string; value: string }) {
  return (
    <div className="rounded-xl border border-border bg-surface px-2 py-3 text-center">
      <p className="text-xs text-muted">{props.label}</p>
      <p className="mt-1 text-sm font-semibold leading-5">{props.value}</p>
    </div>
  );
}

function Mini(props: { label: string; onClick: () => void }) {
  return (
    <button type="button" onClick={props.onClick} className="h-12 rounded-xl border border-border bg-surface text-sm font-semibold">
      {props.label}
    </button>
  );
}

function GlassDock() {
  return (
    <div className="rounded-2xl border border-line bg-bg px-3 py-4">
      <p className="text-center text-xs text-muted">شريط الشاشة الهادئة</p>
      <div className="mt-3 grid grid-cols-4 gap-2 text-center text-xs">
        {["شاهد", "TOD", "يوتيوب", "stc"].map((name) => (
          <div key={name} className="rounded-lg bg-surface-2 py-3">
            {name}
          </div>
        ))}
      </div>
    </div>
  );
}

function Pad(props: { label: string; onClick: () => void; primary?: boolean }) {
  return (
    <button
      type="button"
      onClick={props.onClick}
      className={`h-12 rounded-full text-sm font-semibold ${props.primary ? "bg-primary text-primary-fg" : "border border-border bg-surface"}`}
    >
      {props.label}
    </button>
  );
}

function IconButton(props: { label: string; onClick: () => void; icon: ReactNode }) {
  return (
    <button
      type="button"
      onClick={props.onClick}
      className="flex h-11 flex-col items-center justify-center gap-0.5 rounded-xl border border-border bg-surface text-xs"
    >
      {props.icon}
      {props.label}
    </button>
  );
}

function Action(props: { label: string; onClick: () => void }) {
  return (
    <button type="button" onClick={props.onClick} className="h-12 rounded-xl border border-border bg-surface-2 text-sm">
      {props.label}
    </button>
  );
}

function FilterButton(props: { active: boolean; label: string; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={props.onClick}
      className={`h-11 rounded-xl text-sm font-semibold ${props.active ? "bg-primary text-primary-fg" : "border border-border bg-surface"}`}
    >
      {props.label}
    </button>
  );
}

function Sheet(props: { title: string; onClose: () => void; children: ReactNode }) {
  return (
    <div className="fixed inset-0 z-30 flex items-end justify-center bg-bg/80">
      <div className="max-h-[80vh] w-full max-w-md overflow-y-auto rounded-t-2xl border border-border bg-surface p-4">
        <div className="mb-3 flex items-center justify-between gap-3">
          <h2 className="text-lg font-semibold">{props.title}</h2>
          <button type="button" onClick={props.onClose} className="h-11 rounded-xl px-3 text-sm text-muted">
            إغلاق
          </button>
        </div>
        {props.children}
      </div>
    </div>
  );
}
