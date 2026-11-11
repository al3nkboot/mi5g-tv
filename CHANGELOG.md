# Changelog

All notable changes to the app. Versions follow [semantic versioning](https://semver.org/).

## 2.3.0 (2026-11-11)

### الجديد منذ v2.2.1
- حماية `com.tcl.usercenter` في قوائم TCL G03 وG05 و65C735 (بعض أجهزة TCL تعيد التشغيل بعد الاستعداد بدونه)، وتحويل استثناء tcl-g05 إلى «اسأل».
- لا يُعاد تشغيل معالج الإعداد (setupwraith) ما دامت الشاشة الرئيسية الأصلية (launcherx) متوقفة؛ التراجع يعيد الشاشة الأصلية أولاً.
- وصف أوضح لتكلفة إيقاف setupwraith على البث (G10) ولخدمة تحديث شاومي في MiTV-MOOR2.
- قراءة قائمة التطبيقات بطريقة بديلة عند فشل `pm list packages` (cmd package ثم المستخدم الرئيسي).
- الشاشة الرئيسية 2.9-mi5g: يعيد مساعد زر الرئيسية بعد الاستعداد حتى لو بقي مفعّلاً بالاسم والمفتاح على 0، ولا يتوقف المساعد إذا تعذّر فتح تطبيق مربوط بزر، مع «قفل ترتيب الرئيسية» و«التحويل لجهاز يُشغَّل عبر HDMI-CEC» (متوقفان افتراضياً).
- رسالة واضحة عند امتلاء تخزين التلفاز أثناء تثبيت الشاشة الرئيسية، وتسمية تطبيقات الشاشة الرئيسية الأخرى عندما يسأل أندرويد أيها يستخدم، وزر ثالث «الإبقاء على الشاشة الرئيسية الأصلية للتلفاز» بعد الرفض.
- شاشة Google TV المثبتة يدوياً على Android TV لا تجعل التلفاز Google TV.
- «ما وجدناه» يوضح وجود تطبيقات تُركت لاختيارك، وإيقاف صف «محفوظ» بلمسة واحدة.
- زر «اتصال» في الريموت يقترن ثم يرجعك للرئيسية مباشرة بدل شاشة التنظيف.

### What's new since v2.2.1
- TCL usercenter protected on G03, G05 and 65C735 lists; tcl-g05 override now asks.
- The setup wizard is never switched back on while the TV's own home (launcherx) stays off; undo puts the stock home back first.
- Clearer notes for the G10 casting cost of setupwraith and the MiTV-MOOR2 Xiaomi updater.
- App list fallback when `pm list packages` fails (cmd package, then the main user).
- Bundled launcher 2.9-mi5g: the Home helper comes back after standby also when the switch reads 0, a mapped app that can't start no longer stops the helper, plus optional Home lock and HDMI-CEC One Touch Play switch (both off by default).
- Clear card when the TV is out of space for the home screen install, the chooser card names the other Home apps, and a third button keeps the TV's own home screen after saying no.
- A Google TV home screen installed by hand on an Android TV set no longer makes it a Google TV.
- What we found says when apps are left on for you to choose; a Kept row goes off in one tap.
- The remote's Connect pairs and returns to the home screen, not the cleanup screen.

## 2.2.1 (2026-11-10)

### الجديد منذ v2.2.0
- زر «تخطّي التنظيف» في شاشة «ما وجدناه» بعد الاتصال بالتلفاز (وأثناء القراءة، وعند فشل القراءة أو الخطة): ينقلك للرئيسية وأنت متصل لتستخدم الريموت ومواقيت الصلاة والمباريات وبقية الأدوات.
- التطبيق يتذكر التخطي لكل تلفاز، فلا تُفرض شاشة التنظيف عند كل فتح أو اتصال؛ والتنظيف متاح دائماً من زر «ابدأ التنظيف الآمن» في الرئيسية.

### What's new since v2.2.0
- “Skip cleanup” on the “What we found” screen after connecting (and while reading the TV, and when the read or plan fails): opens the home screen, still connected, so the remote, prayer times, matches and other tools work without cleaning.
- The skip is remembered per TV, so the cleanup screen is not forced on every start or connect; cleaning stays one tap away on the home screen.

## 2.2.0 (2026-11-09)

### الجديد منذ v2.1.1
- خيار «تجاهل أزرار القنوات في الرئيسية» (متوقف افتراضياً) في إعدادات Mi5G على التلفاز.
- خيار «مسح مهمة الإعدادات عند زر الترس» لريموتات MediaTek/Skyworth حتى يرجع زر الرجوع للرئيسية (متوقف افتراضياً).
- سطر حالة المداخل (مثل متصل) أوضح عند التركيز.
- تحديث انتقائي لملفات إزالة البرامج الزائدة من CleanYourTV (MIT) للموديلات الشائعة في الخليج: شاومي/Mi TV، TCL، هايسنس، سوني، Google TV/Chromecast وأجهزة شائعة؛ مع الإبقاء على حماية Mi5G (مثل `com.tcl.exhibit`).
- إصلاح تثبيت Google TV للمستخدم الرئيسي (`cmd package install --user 0`) مع رسائل أوضح بالعربية والإنجليزية عند فشل «لا يوجد APK قابل للقراءة» — قد يعالج بلاغ Chromecast «مقترن لكن لا يثبّت شيئاً».

دعم التطوير: https://paypal.me/Midroid5G · X: @midroid5G

### What's new since v2.1.1
- Optional “Ignore channel keys on Home” (off by default) in Mi5G Settings on the TV.
- Optional “Clear Settings task on gear” for MediaTek/Skyworth remotes so Back returns Home (off by default).
- Inputs list status line is readable when focused.
- Selective Gulf TV debloat profile refresh from CleanYourTV (MIT) for Xiaomi/Mi TV, TCL, Hisense, Sony, Google TV/Chromecast and popular sticks/boxes; Mi5G denylist protections kept (e.g. `com.tcl.exhibit`).
- Google TV main-user install fix (`cmd package install --user 0`) with clearer Arabic/English failure messages for the “no readable apk” / wrong-user case — may address Chromecast “paired but nothing installs” reports.

Support: https://paypal.me/Midroid5G · X: @midroid5G

## 2.1.1 (2026-11-06)

Patch release for two crashes found on OnePlus 13 (Android 15) after v2.1.0.

### الجديد منذ v2.1.0
- إصلاح الشاشة السوداء عند رفض إذن الإشعارات أثناء بدء التنظيف. التطبيق يطلب الإذن أولاً، ثم يكمل سواء وافقت أو رفضت.
- إصلاح انهيار التطبيق عند فتح «تخصيص» بسبب تمرير متداخل على أندرويد 15.

دعم التطوير: https://paypal.me/Midroid5G · X: @midroid5G

### What's new since v2.1.0
- Fixed a black-screen crash when denying the notification permission as cleanup starts. The app now asks first, then continues whether you allow or deny.
- Fixed a crash when opening Customise, caused by nested scrolling on Android 15.

Support: https://paypal.me/Midroid5G · X: @midroid5G

## 2.1.0 (2026-11-06)

First public release since v1.6.0. Includes the unpublished 2.0.0 and 2.0.1 work plus 2.1.

### الجديد في الإصدار 2.1.0 (منذ v1.6.0)

#### من 2.0.0

#### الهاتف والريموت
- ريموت جديد بالكامل: حلقة اتجاهات ذهبية، أزرار الصوت والقنوات، لوحة لمس، لوحة مفاتيح، وتطبيقات مفضلة.
- إقران لاسلكي مدمج لأجهزة Android 11 إلى 14 (رمز الإقران والمنفذ) بدون كمبيوتر، مع إدخال يدوي للعنوان والمنفذ.
- رسائل أوضح: الخطوة التي فشلت عند التثبيت، وبطاقة واضحة عندما لا يمكن الوصول للتلفاز.
- حجم البطاقات والأيقونات (صغير، متوسط، كبير)، ودليل بدء سريع داخل التطبيق.
- شرح أن القفل المطلوب هو قفل الهاتف وليس كلمة مرور للتلفاز.
- حماية أقوى: لا يمكن إيقاف حزم النظام الأساسية (مثل SDK Sandbox في Android 14)، وسحب صلاحية WRITE_SECURE_SETTINGS عند إلغاء ميزة DNS.

#### الشاشة الرئيسية للتلفاز (المشغّل)
- قائمة الضغط المطوّل على البطاقة: نقل، إزالة من الرئيسية، معلومات التطبيق.
- مركز التحكم فوق أي تطبيق، شريط الوسائط العلوي، ساعة عند الرجوع، فتح تطبيق أو مدخل عند تشغيل التلفاز.
- تخصيص أزرار الريموت بضغطة واحدة أو اثنتين أو ثلاث أو ضغط مطوّل.
- قفل الشاشة الرئيسية على Mi5G لأجهزة شاومي مع رجوع آمن للمشغّل الأصلي بدون حذف.

#### مواقيتي و Mi5G Score على التلفاز
- مواقيت الصلاة على التلفاز بمحرك مواقيتي، الأذان الإجباري، تنبيه قبل الأذان، ونافذة عائمة للصلاة القادمة، مع ضبط الإعدادات من الهاتف.
- تذكير يوم الجمعة (العنوان فقط)، وإذاعات قرآن مباشرة من بث عام موجود.
- Mi5G Score: نتائج ومواعيد المباريات وجداول الدوريات وأحداث المباراة.

#### الأجهزة والصيانة والتحديثات
- مساعد شاشات سامسونج Tizen و LG webOS: ريموت عبر Wi-Fi، خطوات AdGuard DNS، وخطوات إيقاف إعلانات Home Promotion و Live Plus و Smart Hub.
- تثبيت كل تطبيقات Mi5G TV على التلفاز من الهاتف بضغطة واحدة.
- ملفات تعريف جديدة: Xiaomi TV Stick 4K (MiTV-AXSR0) و Mi Box S الجيل الثالث (MDZ-32-AA)، وفلتر «المقترح» في مدير التطبيقات.
- قسم التحديثات: الإصدار الحالي والمتاح وطريقة التحديث، وزر «حدّث الآن» يفتح ملف التحديث في المتصفح.
- إعادة الإعداد بضغطة واحدة بعد تحديث النظام، واستعادة كاملة بضغطة واحدة.
- صفحة الشكر والتراخيص: tvOS-Launcher و OmarchyTV و talkies (TechPP) و tv-vibehack (Gathin) والخطوط.

#### من 2.0.1
- ترتيب الشاشة الرئيسية من الجوال عبر رمز QR: نقل البطاقات، تغيير الاسم والأيقونة، إخفاء التطبيقات، ومجلدات.
- تثبيت مداخل HDMI في الرئيسية، وخلفيات Bing أو صور وفيديو من الجوال، وخيارات تخطيط الشبكة.
- شاشة توقف بالمدينة والساعة والتاريخ الهجري والميلادي والصلاة القادمة والطقس (أفكار Aerial TV بترخيص MIT من pengchujin، بدون فيديوهات Apple).
- إصلاح تلفزيونات GAORUI (MediaTek / SDK 28): لا تُرفض الخطة إذا كان المشغّل موقّعاً بمفتاح آخر، مع مهلات أوضح وحماية `com.ctv.android.tvboot`.
- زر واحد «التحقق من التحديثات»، وصفحة GitHub كخيار بديل فقط عند الفشل.
- إشعارات Aerial TV (MIT) و ZXing (Apache-2.0) في التراخيص.

#### من 2.1
- بطاقة «تشغّل جهاز» عند تشغيل جهاز على HDMI، مع إخفاء المنافذ غير المستخدمة وخيار عرض حالة HDMI عبر CEC فقط.
- رسالة من الجوال تظهر في سطرين على شاشة التوقف، وساعات هادئة تخفف الإضاءة ليلاً (افتراضي 23:00 إلى 07:00).
- طقس بدون إعداد من عنوان الإنترنت عند الحاجة، ومصدر احتياطي MET Norway إذا تعطلت Open-Meteo.
- وضع خفيف تلقائي للتلفزيونات البطيئة، وإخفاء تطبيقات الشركة المصنّعة المزعجة، وحماية حزم TCL الأساسية.
- رسالة واضحة إذا فشل تثبيت التحديث أو إذا رفض GitHub الطلب بسبب حد الاستعلامات.
- إعادة رسم الساعة كل دقيقة وعند إضاءة الشاشة (إصلاح توقف الساعة بعد الاستعداد على بعض أجهزة TCL).
- التحقق من اسم الحزمة قبل أوامر الصدفة، وفتح مدخل HDMI عبر Live TV عند ربط زر ريموت به.
- خيار حساب العصر حنفي أو شافعي في مواقيتي على التلفاز (الوحدة v6).
- صف «تابع المشاهدة» اختياري (يحتاج موافقة READ_TV_LISTINGS).
- مساعد اختياري يحاول إرجاع الرئيسية إلى مشغّل Mi5G، وأعمدة الشبكة من 4 إلى 6 مع حجم الأيقونات.
- نقل ملفات محدود بين الجوال والتلفاز عبر رمز QR على نفس الشبكة (بدون تثبيت تلقائي).
- حساب الذاكرة على Android 8 عبر MemAvailable.

### What's new in 2.1.0 (since v1.6.0)
#### Phone and remote
- A new remote: gold D-pad ring, volume and channel rockers, trackpad, keyboard and favorite apps.
- Built-in Wireless Debugging pairing for Android 11 to 14 (pairing code and port), no computer needed, plus manual IP/port entry.
- Clearer errors: the install step that failed, and a clear card when the TV can't be reached.
- Card and icon size (small, medium, large) and an in-app quick start.
- Explains that the lock prompt is the phone's own lock, not a TV password.
- Stronger protection: core system packages (such as the Android 14 SDK Sandbox) can't be switched off, and WRITE_SECURE_SETTINGS is revoked when DNS is turned off.

#### TV home screen (launcher)
- Hold-OK tile menu: move, remove from Home, app info.
- Control Center over any app, a media pill at the top, a clock on Back, open an app or input when the TV wakes.
- Remote button mapper with 1x, 2x, 3x and hold presses.
- Lock Home to Mi5G on Xiaomi TVs, with a safe return to the stock launcher (never uninstalls).

#### Mawaqiti and Mi5G Score on the TV
- Prayer times on the TV with the Mawaqiti engine, forced adhan, pre-adhan banner and a floating next-prayer widget, set from the phone.
- Friday reminder (title only) and live Quran radio from existing public streams.
- Mi5G Score: results, fixtures, league tables and match events.

#### Devices, maintenance and updates
- Samsung Tizen and LG webOS helper: Wi-Fi remote, AdGuard DNS steps, and steps to turn off Home Promotion, Live Plus and Smart Hub ads.
- Install every Mi5G TV app on the TV from the phone in one tap.
- New profiles: Xiaomi TV Stick 4K (MiTV-AXSR0) and Mi Box S 3rd Gen (MDZ-32-AA), and a "Suggested" filter in the App Manager.
- Updates section: current and available version, how updating works, and "Update now" opens the update APK in the browser.
- One-tap re-setup after a system update, and one-tap full restore.
- Credits and licences: tvOS-Launcher, OmarchyTV, talkies (TechPP), tv-vibehack (Gathin) and the fonts.

#### From 2.0.1
- Arrange Home from the phone via QR: move tiles, rename, change icons, hide apps, and folders.
- Pin HDMI inputs on Home, Bing or phone photo/video wallpapers, and grid layout options.
- City screensaver with the clock, Hijri and Gregorian dates, next prayer and weather (Aerial TV ideas, MIT, pengchujin; no Apple aerial videos).
- GAORUI (MediaTek / SDK 28) fix: a foreign-signed launcher no longer blocks the cleanup plan; clearer timeouts; `com.ctv.android.tvboot` is protected.
- One "Check for updates" item, with the GitHub releases page only as a fallback when the check fails.
- Aerial TV (MIT) and ZXing (Apache-2.0) notices in the licences.

#### From 2.1
- A "device is on" card when a device wakes on HDMI, plus hiding unused inputs and an optional CEC-only HDMI status.
- A two-line screensaver message from the phone, and quiet hours that dim the screensaver at night (default 23:00 to 07:00).
- Weather without setup via an IP location fallback when needed, and MET Norway if Open-Meteo fails.
- Automatic lite effects on slow TVs, hiding noisy maker extras, and protecting core TCL packages.
- Clear messages when an update cannot install, or when GitHub rate-limits the check.
- Clock redraw every minute and when the screen turns on (TCL standby clock fix).
- Strict shell package-name checks, and Live TV passthrough when a remote button opens an HDMI input.
- Standard Shafi / Hanafi Asr calculation in Mawaqiti on the TV (module v6).
- Optional Continue Watching row (asks for READ_TV_LISTINGS).
- Optional helper to return Home to the Mi5G launcher, and 4 to 6 Home columns with icon sizes.
- Bounded QR file transfer between phone and TV on the same network (no auto-install).
- Android 8 memory reading via MemAvailable.

## 1.5.0 (2026-10-26)

### الجديد في الإصدار 1.5.0
- **تنبيه الأذان الصوتي والمرئي (`مواقيت الصلاة والطقس v1.2.0`):** إضافة خيار تفعيل تنبيه الأذان والتكبير الصوتي والمرئي عند دخول وقت الصلاة مع زر تجربة الصوت مباشرة على التلفاز بدون الحاجة للإنترنت.
- **إصلاح مشكلة انقطاع الإنترنت ورسالة DNS الخاص على شاشات شاومي (`AdGuard DNS v1.2.0`):** فحص تلقائي لمنفذ 853 قبل التثبيت وإرجاع تلقائي فوري إلى الوضع الآمن عند حظر الراوتر للمنفذ، مع زر مباشر لفتح إعدادات Wi-Fi وشرح إدخال أرقام `94.140.14.14` يدويًا.
- **قسم متجر Downloader وأكواد التحميل السريع (مثل `401740`) + `SmartTube` (يوتيوب بدون إعلانات وبدون Shorts):** فتح أي كود Downloader أو تحميل وتثبيت ملفات APK مباشرة من الهاتف إلى التلفاز بضغطة زر.
- **تخصيص أزرار الريموت (Button Mapper) وإصلاح زر الإعدادات على شاومي + إجبار العرض الكامل (Force Landscape):** تفعيل زر الإعدادات فوق أي تطبيق أو مشغل فيديو، وتحويل أزرار يوتيوب ونتفلكس في الريموت لأي تطبيق، وإجبار كاميرات المراقبة `DMSS` و`واتساب` على العرض الأفقي الكامل، وتفعيل شاشة التوقف بالساعة ووضع الهدوء الليلي والتبديل السريع لمنافذ `HDMI`.
- **ترقية شاملة لمدير تطبيقات التلفاز (7 أوامر كاملة + إظهار تطبيقات النظام والمصنع):** فتح مباشر لبطاقات `Mi5G TV` على `Xiaomi TV Stick`، إيقاف إجباري، منع العمل بالخلفية، تجميد/تعطيل، فك التجميد واستعادة التطبيقات، حذف نهائي لتطبيقات المصنع المسبقة (مثل Netflix و Prime)، وسحب نسخ `APK` للجوال.
- **إدخال رمز الإقران اللاسلكي + دليل شاشات سامسونج `Tizen` و LG `webOS` والشاشات الصينية:** دعم إدخال منفذ ورمز الإقران اللاسلكي، وإضافة ملفات تعريف رسمية لشاشات **Ikon (`IK-E500FF`)** و **STC TV Box** و **Xiaomi TV Stick** و **Haier** و **Nikai** والبروجكتر **HY320**.

## 1.4.0 (2026-10-25)

### الجديد في الإصدار 1.4.0
- إضافة الوحدة الخامسة المدمجة للتلفاز **بطاقة المباريات (فرقك المفضلة)** (`com.midroid.mi5gtv.matches` / `tv-matches`) لعرض نتائج ومواعيد مباريات اليوم والغد بالتوقيت المحلي مع اختيار الفرق والدوريات العربية والأوروبية المفضلة بالريموت.

## 1.3.0 (2026-10-24)

### الجديد في الإصدار 1.3.0
- إضافة قسم تسريع التلفزيون وتنظيف الرام والكاش مع عرض مباشر لاستهلاك الذاكرة العشوائية (RAM) ومساحة التخزين وتسريع حركة القوائم (0.5x).
- إضافة ميزة الإرسال السريع للروابط والفيديوهات والصور من الهاتف إلى شاشة التلفزيون مباشرة عبر ADB.
- إضافة مدير تطبيقات التلفاز الكامل لفتح التطبيقات أو إيقافها إجباريا أو مسح بياناتها أو سحب نسخة احتياطية من ملف APK إلى الهاتف.

## 1.1.0 (2026-10-24)

### الجديد في الإصدار 1.1.0
- حماية تطبيقات البث العربية (شاهد، TOD، stc tv، STARZPLAY، OSN+، WATCH IT، Yango Play، الأولى، أوان، أنغامي، beIN CONNECT) من الإيقاف وإضافتها تلقائيا إلى شريط التطبيقات.
- إضافة شاشة أدوات التحكم بالتلفاز (ريموت التلفاز الكامل، إرسال النص عبر ADB، وتصوير شاشة التلفاز مع المشاركة الفورية).
- إضافة أداة تثبيت ملفات APK عن بعد من الهاتف إلى التلفاز عبر ADB.

## 0.3.3 (2026-10-24)

Fixes from the first public day of issues. Thank you to everyone who reported.

### Setup
- A switch-off the TV answers "yes" to but keeps on is now skipped, and the rest of the clean-up
  runs. Before, Carry on met the same step again every time (issue #10, PatchWall on a Xiaomi TV
  Box S with Android 14). Done names what wouldn't switch off.
- The app and the TV now check the same rule before anything is switched off, so the plan can no
  longer include a step the app itself would then refuse.
- A launcher you installed yourself is never switched off. If the TV's home screen was already
  changed, by another app or an earlier change, Setup leaves it as it is and does the clean-up.
  Before, such a TV got "plan refused" on every try (issue #8).
- On a TV without an exact list, the TV's own home screen now stays switched on during the new
  home screen switch when it is a protected kind of app (one the TV keeps running, its keyboard or
  its screensaver), instead of being switched off.
- When there was nothing to change, Done now says "Nothing was changed" instead of "Your TV is
  ready" (issue #15).
- On a TV the app has no list for, the setup wizard note no longer suggests re-adding the Wi-Fi,
  which would not help there.

### Manage
- Home screen on a TV without the new home screen says why Setup left the TV's own, and what
  would change it, instead of offering a dock and a look that ended in "We stopped" (issue #3).

### Reports
- Help add this TV and Report a problem now fill the GitHub form in for you: the report is part of
  the link when it fits (all the real reports so far do), and is still copied as a fallback. The
  form's report box can no longer be posted empty (issues #12, #14, #16).
- The TV report lists the apps in short groups, so it fits the form.
- Build numbers such as "V816.0.11.0" are no longer mistaken for network addresses and masked.
- Privacy policy: says that when the report is part of the form's link, your browser sends it to
  GitHub as it opens the form. Nothing is posted until you post it.

## 0.3.2 (2026-10-23)

Fixes from the first two issues people reported, and an easy way to report a problem or a new TV.

### Fire TV and undo
- Fire TV sets (and Fire TV Edition TVs from Insignia, Toshiba and others) are now recognised
  from the TV's own details, even when the app has no list for the exact model. They get the
  clean-up only: Fire OS does not let another home screen take over, so the app no longer tries.
  China-market TCL and FFalcon sets without Google services are treated the same way.
- Undo now checks the TV itself. If the TV's own home screen is back, undo finishes, even when
  the TV refuses a step whose result is already in place. Before, a Fire TV could refuse to
  "switch Amazon's home screen back on" although it was never off, and every button (Carry on,
  Undo, Stop here) ran into the same refusal again.
- If you were stuck on "The setup didn't finish" with 0.3.1, it clears the next time the app
  connects to the TV after updating.
- Stop here shows Done only once the run has really stopped, and "Forget this TV" ends an
  unfinished setup for that TV straight away. While setup is still working on a TV, Forget this
  TV and Delete everything ask you to wait or stop it first.
- A TV whose home screen the app does not recognise gets the clean-up only, instead of an error.

### Reporting
- **Help add this TV**: when the app has no list for your TV, one tap makes a short report of
  what the TV is and which apps it came with (apps you installed yourself are only counted, never
  named). You see the whole text first, then copy it into a pre-filled GitHub form or share it
  another way. The app sends nothing itself.
- **Report a problem**, in Settings and on the screens where something went wrong, does the same
  with the redacted log.
- The log is readable: plain error names, how the TV was matched, the home screen decision, and
  the step that failed with the TV's answer.
- "Share diagnostics" said it made a zip; it shares plain text, and the screens now say so.
- The TV report and the problem report never name a home screen app you installed; only the
  TV's own and the app's are named.
- Delete everything now deletes the app's diagnostics log too.
- Privacy policy: the diagnostics section is now "Reports you choose to share", and "What stays
  on your phone" lists the diagnostics log. Nothing new is collected and nothing is sent by the
  app.
- Friendlier issue forms that the app can fill in, and a "Reporting a problem or a new TV"
  section in CONTRIBUTING.

## 0.3.1 (2026-10-23)

A small update for F-Droid's inclusion rules. Nothing changes in how the app cleans a TV.

- Where add-ons are offered (Cards and modules, and Done's "Add a card"), each add-on's page now
  says, before you add it, that add-ons are downloaded from their authors' GitHub releases,
  outside any app store, and are not built or checked by F-Droid. Back leaves without
  downloading anything, as before.
- The store description names the app's internet connections: the version from GitHub and
  F-Droid contacts GitHub only when you tap "Check for updates" or choose to browse and add
  add-ons; the Google Play version makes none of its own. The short summary follows F-Droid's
  style.
- The README says how the app is made: with AI coding agents, under the maintainer's review.
- The app's page and privacy policy moved to https://cleanyour.tv/app/android and
  https://cleanyour.tv/app/android/privacy.

## 0.3.0 (2026-10-24)

A Google Play version of the app, F-Droid readiness, and a website with a privacy policy. Every
channel gets the same app, built from the same code and signed with the same key. On the GitHub
build nothing changes in use.

### Google Play
- A Google Play build of the app, for people who would rather install from the Play Store than
  download an APK (Google Play lists it once its review is done). It has the same app ID, version
  and signing key as the GitHub download.
- The Google Play build is updated by Google Play, so it has no "Check for updates". It downloads
  no add-ons (Cards and modules and Done's "Add a card" are not shown), makes no internet request
  of its own, and always keeps the TV's software updaters on: they are listed under "We keep
  these" instead of being a choice in Customise, and Switched off has no Off choice for them. One
  that an earlier GitHub build switched off is listed in Switched off, where it can be put back.
  Everything else is the same.

### F-Droid
- F-Droid can build the app from source and get exactly the APK published on GitHub, so it can
  offer the same signed APK. The home screen launcher and the TV progress screen that the app
  bundles are rebuilt from source too, and still match the versions the app checks before it
  installs them on a TV.

### Website and privacy policy
- The app has a website, https://udhaykumarbala.github.io/cleanyourtv-app/: the download, the three
  steps, the supported TVs and how to install an APK.
- The privacy policy is at https://udhaykumarbala.github.io/cleanyourtv-app/privacy/ (and in
  PRIVACY.md): the app collects no data and has no accounts, analytics, advertising or crash
  reporting; everything it learns about a TV stays on the phone.

### One app, one key
- The GitHub, F-Droid and Google Play builds share the app ID `tv.cleanyour.app`, the version and
  the signing certificate, so an install from one channel can be updated from another and keeps
  what the app saved.
- One release now builds both reproducibly: the APK for GitHub and F-Droid and the bundle for
  Google Play, each built twice and compared byte for byte, and checked for the same permissions
  and, in the Play bundle, no GitHub host and no update check.

## 0.2.0 (2026-10-21)

Launcher 2.5 and 42 more TV lists (131 in all), with stricter safety: the home screen switch is
skipped up front on TVs where it could break casting, Wi-Fi or start-up, nothing that the TV
inputs, accessibility or the remote need is switched off by default, and the app says why when a TV
keeps its own home screen or an app stays on.

### Home screen launcher
- Bundles the Clean Your TV launcher 2.5 (upstream v2.5.0), built from source and signed with this
  project's launcher key. New on the TV: edit the dock from the remote, choose the rows under the
  dock and a wallpaper in Launcher settings, and more; every new launcher option is off by default.
- A TV set up with 0.1.0 is offered "Update the home screen" in Manage. The dock, the look and the
  card answers stay.
- The Home screen page says what happens to a dock changed on the TV: the phone cannot see it, a
  new look keeps it, and saving a different dock from the phone replaces it. A new look now sends
  the saved dock as it is, even when an app in it has gone from the TV, so the TV's own dock edit
  stays; a dock of more than 12 apps saved under Six columns keeps its first 12 in a four-tile look.
- The phone reads the settings keys launcher 2.5 added exactly as the launcher does.
- Never switches off an app that asks for the TV input hardware permission (the layer under the
  HDMI, AV and tuner inputs), even when a profile lists it and also during the home screen switch,
  where a TV's own home screen that asks for it stays on. The run goes on without it, and Done and
  Manage say it was kept because it runs the TV's HDMI and tuner inputs.
- After a look change, the launcher's remote button services come back on TVs that kept them
  unbound before (accessibility is switched off and on again around the list, as upstream does).
  Stopping a run in the middle of that step finishes it first, so the TV never stays without its
  accessibility services.
- When the home screen switch fails, Setup no longer names a TV's own fallback placeholder as
  another home screen app.

### TV lists
- Matches the TV against 131 community tested profiles from 46 brands, up from 89, from
  the upstream lists of launcher v2.5.0: 42 new ones, among them the first Fire OS 8 list (Fire
  TV Stick 4K, 2023), three more Fire TV devices, six more TCL sets, the Google TV Streamer,
  XGIMI MoGo 2 and MoGo 3 Pro, Xiaomi's China sets on PatchWall and HyperOS 3, Hisense and Vidda
  VIDAA sets, the Freebox Player POP, and first lists from AIWA, BYINTEK, Cecotec, Challenger,
  Compac, EKO, FFalcon, JVC, Kogan, Motorola, MTC, Polytron, SHARP, Tele System and Verizon.
- Sets where the home screen cannot be switched safely get the clean-up and keep their own home,
  and the "What we found" page says why: TVs with a vendor home the app does not know (Xiaomi
  PatchWall, VIDAA, OQEE, Verizon); the Kalley 43G200, the Kalley R10G and the PRISM+ Q65 Ultra,
  one family whose firmware brings the setup wizard back at every boot and has left a set stuck at
  start-up, also when no list matches the TV exactly; and Google's own Chromecast with Google TV and
  Google TV Streamer, where the switch would cost casting after every reboot.
- On TVs whose home screen switch needs the setup wizard off, the switch is skipped up front when
  the wizard saved the TV's Wi-Fi or the TV cannot say who did: switching the wizard off would
  forget the Wi-Fi, and left on, it takes over the Home button and can crash the TV (upstream
  #506). Setup does the clean-up and says how to get the new home screen later. A TV without a list
  gets the clean-up only whenever its setup wizard is on, since the switch would leave the wizard on
  with the TV's own home screen off.
- TCL Live, a TV input, is a choice on every TCL list instead of off by default, and the broadcast
  tuner inputs on the Sony BRAVIA AE2 and PRISM+ Q65 Ultra lists are always kept.
- TalkBack, the phone remote service and the remote's usage logger are a choice on every list that
  names them, never off by default, and a test keeps any accessibility, remote or pairing package
  from being off by default again.
- The app has its real icon, a glass TV, in place of the placeholder outline.
- The app takes its version from the installed APK, so Settings, diagnostics and "Check for
  updates" always name the version that runs.
- Corrections from upstream: the onn 4K box keeps Google's ambient mode so the Google Photos
  screensaver works, the Philips 65PUS8102 also switches off its reboot logger, and the TCL G07 and
  Xiaomi MSSP3 lists gained notes from more units. Home screen customizers that a TV service or
  the stock home's Inputs button relies on are now a choice rather than off by default on four
  more TVs.
- When two profiles match a TV equally, the one whose firmware fingerprint is named more exactly
  wins, so lists for neighbouring firmware builds each reach their own TVs.
- Sixteen upstream lists are not shipped, each with its reason in
  `core/spec/upstream-feedback/unconverted.txt`: duplicates of a shipped list, lists with nothing
  the app can match on, and a TCL China set whose network debugging needs a computer after every
  reboot.

## 0.1.0 (2026-10-20)

The first public release.

### Setup
- Finds Android TV, Google TV and Fire TV devices on the Wi-Fi and guides you through switching on
  network debugging with the remote, with steps per TV family.
- Reads the TV and matches it against 89 community tested profiles (27 brands), with a careful
  brand fallback for models nobody has profiled yet.
- Shows what it would switch off, grouped and counted, and what it keeps, before anything runs;
  Customise changes the list, the dock and the look, and shows every command in order.
- "Make it clean": saves a restore point, disables the clutter (never uninstalls), installs the
  Clean Your TV launcher 2.4 with a dock of the apps you use, and shows a progress animation on the
  TV while it works. A watchdog on the TV puts its own launcher back if the phone does not confirm
  the new home screen within 150 seconds.
- Asks for the phone's screen lock once per session before the first change; switches network
  debugging off again at the end unless you keep it on.

### Afterwards
- Manage: put back any switched off app, check for leftovers after a TV software update, change
  the home screen, cards and modules, undo everything from the restore point.
- Save a copy of the restore point as a zip with plain restore scripts that work from any computer
  with adb.
- Optional add-on apps for the TV from a signed list, checked by size, SHA-256 and signer before
  install.
- Settings: check for updates (one request to GitHub, on demand), share redacted diagnostics after
  a preview, licences.

### Under the hood
- No accounts, no analytics; internet only to GitHub and only on your action.
- Reproducible release build: two clean container builds byte identical; CI builds the same
  unsigned APK for every tag so the signed release can be checked against it.
- Signed with the release key, certificate SHA-256
  `eaf7af3867bb29ff4bb038854842c93f42ffe4198a087e611cb18f925201cd65`.
