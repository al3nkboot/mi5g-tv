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
  where: string;
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
    title: "لوحة صحة بدل جدار الأدوات",
    body: "من 1.3 إلى 1.5 اجتمعت في شاشة الإدارة: التسريع، الإرسال، مدير التطبيقات، أكواد Downloader، ومخطط الأزرار. المستخدم لا يرى «هل تلفازي بخير؟» قبل أن يختار أداة. اللوحة تبدأ بدرجة، والذاكرة، وما سيُوقَف، وحالة DNS، وزر واحد.",
    where: "feature:manage",
    tryScreen: "home",
  },
  {
    id: "platform",
    title: "افصل أندرويد عن تايزن و webOS",
    body: "دليل سامسونج و LG في 1.5 مفيد، لكن التعطيل والحذف يمرّان عبر ADB. المعالج يتفرّع: أندرويد وجوجل وفاير يأخذون الأدوات. تايزن و webOS يأخذون دليل ربط فقط، بلا أزرار تنظيف.",
    where: "feature:connect · feature:setup",
    tryScreen: "home",
  },
  {
    id: "uninstall",
    title: "الحذف النهائي ليس الافتراضي",
    body: "إلغاء تثبيت نتفلكس أو برايم من المصنع لا يُتراجع عنه كالتجمييد. الافتراضي «تجميد». الحذف يطلب نقطة استعادة، وكتابة كلمة «احذف»، ويوضّح أنه حذف للمستخدم لا مسح من صورة النظام.",
    where: "feature:manage · core:planner",
    tryScreen: "apps",
  },
  {
    id: "catalog",
    title: "كتالوج أكواد بدل حقل حر",
    body: "كود 401740 مثال جيد، لكن حقلًا حرًا يفتح أي ملف. اعرض كتالوجاً عربياً مع الحجم والمصدر وتنبيه أنه خارج المتجر، وأبقِ الكود الحر تحت «متقدم».",
    where: "feature:modules",
    tryScreen: "modules",
  },
  {
    id: "evening",
    title: "وضع المساء بضغطة",
    body: "يجمع ما صار موزعاً: سرعة القوائم 0.5×، DNS عائلي، وهدوء ليلي. تنبيه الأذان يبقى اختياراً داخل وحدة الصلاة ولا يُشغَّل مع الوضع تلقائياً.",
    where: "feature:manage · feature:modules",
    tryScreen: "home",
  },
  {
    id: "household",
    title: "أكثر من تلفاز في البيت",
    body: "نقطة الاستعادة والخطة مربوطتان عملياً بجهاز واحد. التبديل بين الصالة وغرفة النوم من أعلى الشاشة، لكل جهاز حالته، و«انسَ هذا التلفاز» لا يمسح الآخر.",
    where: "feature:connect · feature:backup",
    tryScreen: "home",
  },
  {
    id: "dns",
    title: "منفذ 853 قبل انقطاع النت",
    body: "إصلاح AdGuard في 1.5 يرجع للوضع الآمن بعد الفشل. اعرض حالة المنفذ على اللوحة قبل التثبيت. إن حجب الراوتر 853، الزر يكتب 94.140.14.14 ويقترح فتح الواي فاي بدل أن يكتشف المستخدم الانقطاع لاحقاً.",
    where: "feature:modules · adb:session",
    tryScreen: "home",
  },
  {
    id: "split",
    title: "تطبيقاتي غير تطبيقات المصنع",
    body: "سبعة أوامر في كل صف تزدحم على الجوال. تطبيقاتك: فتح، إيقاف، منع الخلفية، مسح الكاش. المصنع: تجميد واستعادة أولاً، والحذف في ورقة منفصلة. المحمي (شاهد، TOD، stc…) بلا تعطيل.",
    where: "feature:manage",
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
