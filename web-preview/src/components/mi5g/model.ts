export type ScreenId = "home" | "clean" | "remote" | "apps" | "modules" | "ideas";

export type Platform = "android" | "tizen" | "webos";

export type TvDevice = {
  id: string;
  room: string;
  model: string;
  platform: Platform;
  host: string;
  online: boolean;
  dnsPort: "blocked" | "open";
};

export type PlanGroup = {
  id: string;
  title: string;
  count: number;
  detail: string;
};

export type TvApp = {
  id: string;
  name: string;
  kind: "user" | "system";
  protected: boolean;
  detail: string;
};

export type ModuleCard = {
  id: string;
  title: string;
  detail: string;
};

export type Idea = {
  id: string;
  title: string;
  body: string;
  tryScreen: ScreenId;
};

export const TVS: TvDevice[] = [
  {
    id: "stick",
    room: "صالة العائلة",
    model: "Xiaomi TV Stick",
    platform: "android",
    host: "192.168.1.42",
    online: true,
    dnsPort: "blocked",
  },
  {
    id: "ikon",
    room: "غرفة النوم",
    model: "Ikon IK-E500FF",
    platform: "android",
    host: "192.168.1.58",
    online: false,
    dnsPort: "open",
  },
  {
    id: "samsung",
    room: "غرفة الضيوف",
    model: "Samsung Q60",
    platform: "tizen",
    host: "192.168.1.21",
    online: true,
    dnsPort: "open",
  },
  {
    id: "lg",
    room: "المجلس",
    model: "LG webOS",
    platform: "webos",
    host: "192.168.1.33",
    online: true,
    dnsPort: "open",
  },
];

export function platformLabel(platform: Platform): string {
  if (platform === "android") return "أندرويد";
  if (platform === "tizen") return "تايزن";
  return "webOS";
}

export const PLAN: PlanGroup[] = [
  {
    id: "ads",
    title: "إعلانات وتتبع",
    count: 18,
    detail: "خدمات العروض وصفوف التوصيات. لا تُحذف بياناتها، تُوقَف فقط.",
  },
  {
    id: "promo",
    title: "اختصارات المتجر",
    count: 5,
    detail: "صفوف لا تفتح تطبيقاً تستخدمه يومياً.",
  },
  {
    id: "home",
    title: "شاشة رئيسية هادئة",
    count: 1,
    detail: "مشغّل خفيف مع الإبقاء على شريط تطبيقاتك كما هو.",
  },
];

export const KEPT = ["شاهد", "TOD", "stc tv", "STARZPLAY", "OSN+", "يوتيوب", "نتفلكس", "beIN"];

export const APPS: TvApp[] = [
  { id: "shahid", name: "شاهد", kind: "user", protected: true, detail: "محمي · بث عربي" },
  { id: "tod", name: "TOD", kind: "user", protected: true, detail: "محمي · بث عربي" },
  { id: "stc", name: "stc tv", kind: "user", protected: true, detail: "محمي · بث عربي" },
  { id: "yt", name: "يوتيوب", kind: "user", protected: true, detail: "محمي" },
  { id: "tube", name: "SmartTube", kind: "user", protected: false, detail: "ثبّته أنت" },
  { id: "netflix", name: "Netflix", kind: "system", protected: false, detail: "مصنع · يمكن تجميده" },
  { id: "prime", name: "Prime Video", kind: "system", protected: false, detail: "مصنع · يمكن تجميده" },
  { id: "patch", name: "PatchWall", kind: "system", protected: false, detail: "شاشة شاومي الترويجية" },
  { id: "adsvc", name: "Mi Ad Service", kind: "system", protected: false, detail: "تتبع وعروض" },
];

export const MODULES: ModuleCard[] = [
  { id: "dns", title: "مانع الإعلانات", detail: "DNS عائلي. يفحص منفذ 853 قبل أي تغيير." },
  { id: "prayer", title: "مواقيت الصلاة", detail: "المواقيت والطقس. الأذان خيار داخل البطاقة لا يُفرض." },
  { id: "speed", title: "قياس السرعة", detail: "تنزيل، زمن الاستجابة، وهل الخط يكفي لبث 4K." },
  { id: "files", title: "مدير الملفات", detail: "التنزيلات والذاكرة و USB بالريموت." },
  { id: "matches", title: "مباريات اليوم", detail: "اليوم والغد بتوقيتك، مع فرقك المفضلة." },
];

export const CODES = [
  { id: "401740", name: "SmartTube", note: "يوتيوب بلا إعلانات وبلا Shorts" },
  { id: "482910", name: "شاهد", note: "خارج المتجر الرسمي" },
  { id: "310442", name: "TOD", note: "خارج المتجر الرسمي" },
];

export const IDEAS: Idea[] = [
  {
    id: "health",
    title: "لوحة صحة التلفاز",
    body: "درجة واحدة، الذاكرة، وما سيُوقَف، ثم زر التنظيف. الأدوات الأخرى تبقى في مكانها.",
    tryScreen: "home",
  },
  {
    id: "platform",
    title: "أندرويد غير تايزن و webOS",
    body: "التنظيف والحذف لأندرويد وجوجل وفاير فقط. سامسونج و LG يعرضان دليل الربط بلا أزرار تعطيل.",
    tryScreen: "home",
  },
  {
    id: "uninstall",
    title: "التجميد قبل الحذف",
    body: "تطبيقات المصنع تُجمَّد أولاً. الحذف النهائي يطلب نقطة استعادة وكتابة «احذف».",
    tryScreen: "apps",
  },
  {
    id: "catalog",
    title: "كتالوج التحميل",
    body: "شاهد و TOD و SmartTube من الكتالوج. الكود الحر يبقى تحت «متقدم» لأنه خارج المتجر.",
    tryScreen: "modules",
  },
  {
    id: "evening",
    title: "وضع المساء",
    body: "قوائم أهدأ و DNS عائلي على هذا التلفاز. الأذان لا يعمل إلا إذا فتحته من بطاقة الصلاة.",
    tryScreen: "home",
  },
  {
    id: "household",
    title: "أكثر من تلفاز",
    body: "لكل جهاز خطته ونقطة استعادته. نسيان تلفاز لا يمسح البقية.",
    tryScreen: "home",
  },
  {
    id: "dns",
    title: "فحص منفذ 853",
    body: "إن حجب الراوتر المنفذ يُكتب 94.140.14.14 قبل أن ينقطع النت. وإن كان مفتوحاً يُثبَّت DNS المشفر.",
    tryScreen: "home",
  },
  {
    id: "split",
    title: "تطبيقاتي والمصنع",
    body: "تطبيقاتك للفتح والإيقاف والكاش. المصنع للتجميد. شاهد و TOD و stc تبقى محمية.",
    tryScreen: "apps",
  },
];

export function healthScore(input: {
  platform: Platform;
  cleaned: boolean;
  ramFreed: boolean;
  dnsSafe: boolean;
  evening: boolean;
  online: boolean;
}): number | null {
  if (input.platform !== "android") return null;
  let score = 58;
  if (input.cleaned) score += 18;
  if (input.ramFreed) score += 8;
  if (input.dnsSafe) score += 8;
  if (input.evening) score += 6;
  if (!input.online) score -= 12;
  return Math.max(18, Math.min(98, score));
}
