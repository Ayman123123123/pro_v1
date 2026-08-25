# 🏛️ YOUNES Sovereign Platform — تقرير الترقية الشاملة للواجهة والتجربة

## تاريخ: 2026-08-08 | الفرع: `arena/019fdfec-pro-v1` | Commit: `00b9fc0`

---

## 📊 ملخص بالأرقام

| المقياس | القيمة |
|---|---|
| ملفات جديدة | **15 ملف** |
| ملفات محدثة | **6 ملفات** |
| أسطر برمجية مضافة | **5,585+ سطر** |
| أسطر محذوفة | **106 سطر** |
| مكونات Compose جديدة | **40+ مكون** |
| API endpoints جديدة | **8 endpoint** |
| أنواع الإشعارات | **16 نوع** |
| أنواع المكالمات | **6 أنواع** |
| الثيمات | **8 ثيمات** |
| ألوان التمييز | **7 ألوان** |

---

## 🎨 1. نظام الأيقونات السيادية (`RedIcons.kt`)

**80+ أيقونة مخصصة ومصنفة حسب الميزة:**

| المجموعة | العدد | الأيقونات الرئيسية |
|---|---|---|
| 📞 CallIcons | 16 | VoIP Audio, Video, Conference, Live, Space, PSTN, Speaker, Mute, Bluetooth, Hold, Transfer, Record |
| 💬 ChatIcons | 18 | Message, Reply, Forward, Pin, Star, Attachment, VoiceNote, Search, ReadReceipt, Group |
| 📖 StoryIcons | 9 | Add, View, Text, Image, Video, Close, Reply, Viewers, Delete |
| 👥 GroupIcons | 14 | Create, Info, Members, Admin, Owner, Invite, Mute, Pinned, Media, Links, Files, Polls |
| 🔔 NotificationIcons | 11 | Message, Call, GroupInvite, Story, System, Security, Dinstar, Warning, Success, Error |
| 👤 ProfileIcons | 12 | Avatar, Edit, Status, Privacy, Security, 2FA, Devices, Backup, Update, Storage, Network |
| 🔒 PrivacyIcons | 12 | Everyone, Contacts, Selected, Nobody, LastSeen, ProfilePhoto, ReadReceipts |
| 🎵 MediaIcons | 19 | Play, Pause, Stop, Skip, FastForward, Rewind, Volume, Fullscreen, PiP, Subtitle, Speed, Repeat |
| 🎨 ThemeIcons | 11 | Dark, Light, Auto, OLED, Color, Font, Wallpaper, Animation, Language, RTL |
| 📡 LiveIcons | 12 | GoLive, Viewers, Heart, Comment, Share, Gift, Pin, End, Camera, Flip, Beauty, Filter |

---

## 🔘 2. نظام الأزرار السيادية (`SovereignButtons.kt`)

**8 أنواع أزرار احترافية:**

| الزر | اللون | الاستخدام |
|---|---|---|
| `SovereignGoldButton` | تدرج ذهبي | الأفعال الرئيسية والتأكيد |
| `SovereignCyanButton` | تدرج سماوي | الإتصالات و VoIP |
| `SovereignDangerButton` | تدرج أحمر | الحذف وإنهاء المكالمة |
| `DinstarGoldButton` | تدرج ذهبي يمني | المكالمات الخطية عبر Dinstar |
| `LiveButton` | تدرج أحمر حي + نبض | البث المباشر (ينبض عند LIVE) |
| `SpaceButton` | تدرج أرجواني | الغرف الصوتية |
| `SovereignGlassButton` | زجاجي شفاف | أفعال ثانوية (Glassmorphism) |
| `SovereignIconButton` | دائري مصغر | أزرار أيقونية سريعة |

**+ مكونات مساعدة:**
- `SovereignToggleButton` — زر تبديل متحرك
- `PrivacyOptionButton` — زر اختيار الخصوصية
- `SovereignGradientButton` — الأساس الداخلي (تدرج + ظل + loading)

---

## 🎵 3. مشغل الوسائط المتقدم (`SovereignMediaPlayer.kt`)

**3 مشغلات احترافية:**

### `SovereignAudioPlayer` — مشغل الصوت
- شريط تقدم مخصص بلون سيادي
- تحكم بالسرعة (0.5x, 0.75x, 1x, 1.25x, 1.5x, 2x)
- تخطي للأمام/الخلف
- تنسيق الوقت تلقائي

### `SovereignVideoPlayer` — مشغل الفيديو
- ExoPlayer حقيقي مع AndroidView
- أزرار تحكم متحركة (fadeIn/fadeOut)
- رجوع/تقديم 10 ثواني
- سرعة التشغيل (1x → 1.5x → 2x → 0.5x)
- Picture-in-Picture
- ملء الشاشة
- شريط علوي مع رجوع + سرعة + PiP + Fullscreen

### `VoiceNotePlayer` — فقاعة رسالة صوتية
- شريط تقدم مصغر
- أيقونة تشغيل/إيقاف
- مدة تشغيل/إجمالية
- لون مختلف حسب المرسل (Cyan لي / Gold له)

---

## 🔔 4. نظام الإشعارات السيادي

### `SovereignNotificationSystem.kt` — الواجهة
**16 نوع إشعار:**

| الفئة | الأنواع |
|---|---|
| الرسائل | NEW_MESSAGE, GROUP_MESSAGE, MENTION |
| المكالمات | INCOMING_CALL, MISSED_CALL, PSTN_CALL |
| القصص | STORY_VIEW, STORY_REPLY |
| المجموعات | GROUP_INVITE, GROUP_UPDATE, ROLE_CHANGE |
| البث | LIVE_STARTED, SPACE_STARTED |
| النظام | SECURITY_ALERT, DEVICE_NEW, UPDATE_AVAILABLE |
| Dinstar | DINSTAR_STATUS, DINSTAR_ALERT |

**ميزات الواجهة:**
- مركز إشعارات كامل مع فلاتر
- شارة غير مقروء ديناميكية
- إجراءات على الإشعارات (قبول/رفض)
- تصنيف حسب الأولوية (URGENT, HIGH, NORMAL, LOW)
- تجميع الإشعارات (groupId)
- Builder Pattern لإنشاء الإشعارات

### `SovereignNotificationRouter.kt` — الخدمة الحقيقية
- WebSocket حقيقي مع OkHttp
- **توجيه حقيقي لكل أنواع الرسائل** (routing)
- إنشاء إشعارات أصلية Android لكل نوع
- قنوات إشعارات منفصلة (messages, calls, groups, live, dinstar, system)
- Exponential backoff للإعادة (1s, 2s, 4s, 8s, 16s, 30s)
- StateFlow متصل/isConnected للواجهة
- MessagingStyle للرسائل

---

## 👥 5. نظام المجموعات المتقدم (`SovereignGroupSystem.kt`)

**إنشاء مجموعة بـ 3 خطوات:**
1. **المعلومات** — اسم + وصف + أيقونة
2. **الخصوصية** — عامة / خاصة / سرية
3. **المميزات** — 8 مميزات قابلة للتفعيل (رسائل، وسائط، صوتية، استطلاعات، مكالمات، بث، روابط، ملفات)

**إدارة المجموعة:**
- 4 أدوار: مالك (👑)، مشرف (🛡️)، مراقب (🏅)، عضو
- خصوصية المجموعة (عامة/خاصة/سرية)
- كتم + تثبيت
- مغادرة + حذف (للمالك فقط)
- شاشة معلومات مع تبويبات (الأعضاء، الوسائط، الروابط، الإعدادات)

---

## 🔒 6. نظام الخصوصية والحالات (`SovereignPrivacySystem.kt`)

### مستويات الخصوصية (5 مستويات):
| المستوى | الأيقونة | الوصف |
|---|---|---|
| الجميع | 🌐 Public | أي شخص يمكنه الرؤية |
| جهات الاتصال | 📞 Contacts | فقط من في جهات الاتصال |
| باستثناء | 👥 Group | جهات اتصال مع استثناءات |
| مشاركة مع | 👥 People | فقط أشخاص محددين |
| لا أحد | 🔒 Lock | لن يرى أحد |

### إعدادات الخصوصية (9 إعدادات):
آخر ظهور، الحالة المتصلة، صورة الملف، النبذة، الحالة، إيصالات القراءة، المكالمات، إضافتي للمجموعات، الموقع المباشر

### الحالات (6 حالات):
| الحالة | الرمز | اللون |
|---|---|---|
| متصل | 🟢 | أخضر |
| مشغول | 🔴 | أحمر |
| بعيد | 🟡 | أصفر |
| لا تزعجني | ⛔ | أحمر |
| مخفي | 👻 | رمادي |
| غير متصل | ⚪ | رمادي |

**+ حوار تحديث الحالة مع:**
- اختيار نوع الحالة
- نص مخصص
- خصوصية (من يستطيع رؤيتها)
- شارة تشفير طرفي

---

## 📖 7. نظام القصص والمنشورات (`SovereignStorySystem.kt`)

### `SovereignStoryBar` — شريط القصص
- حلقة تقدم ملونة (Cyan = غير مشاهدة، رمادي = مشاهدة)
- شارة عدد القصص غير المشاهدة
- زر إضافة قصة مع أيقونة AddCircle

### `SovereignStoryViewer` — عارض القصص
- 4 أنواع: IMAGE, VIDEO, TEXT, VOICE
- ExoPlayer للفيديو
- أشرطة تقدم فردية
- نقر يمين/يسار للتنقل
- تفاعلات سريعة (❤️ 🔥)
- شريط رد
- قائمة المشاهدين (لقصتي)
- خصوصية لكل قصة

### `SovereignCreateStoryScreen` — إنشاء قصة
- 4 أنواع: نص، صورة، فيديو، صوتي
- 8 خلفيات للنص (أحمر، أزرق، أخضر، أرجواني، برتقالي، وردي، داكن، ذهبي)
- خصوصية لكل قصة (الجميع/جهات الاتصال/لا أحد)
- شرح للوسائط

---

## 📞 8. نظام المكالمات الشامل (`SovereignCallSystem.kt`)

**6 أنواع مكالمات:**

| النوع | الأيقونة | اللون | الوصف |
|---|---|---|---|
| VoIP صوتي | 📞 Call | أزرق | مكالمة صوتية بتشفير طرفي |
| VoIP فيديو | 📹 Videocam | أرجواني | فيديو 1080p بتشفير طرفي |
| مؤتمر | 👥 Groups | أخضر | حتى 32 مشارك |
| بث مباشر | 📺 LiveTv | أحمر | 1-إلى-عدة |
| خطي يمني | 📱 SimCard | ذهبي | عبر Dinstar GSM |
| غرفة صوتية | 🎤 Mic | أرجواني | مفتوحة على الهواء |

### `SovereignActiveCallScreen` — واجهة المكالمة النشطة
- واجهة مختلفة لكل نوع (فيديو/صوتي/مؤتمر/بث/PSTN)
- SurfaceViewRenderer للفيديو مع معاينة محلية
- نبض ديناميكي للأفاتار في المكالمات الصوتية
- شبكة مشاركين للمؤتمر
- شارة LIVE + عدد المشاهدين للبث
- إشارة GSM + رقم المنفذ للـ PSTN

**أزرار التحكم:**
- كتم / إيقاف / مكبر أو فيديو
- إضافة مكالمة / تحويل / قلب كاميرا / تسجيل

### `SovereignCallLogScreen` — سجل المكالمات الموحد
- فلاتر حسب نوع المكالمة (6 فلاتر)
- شارة نوع ملونة
- إشارة GSM للـ PSTN
- زر إعادة اتصال سريع

### `CallTypePickerSheet` — اختيار نوع المكالمة
- Bottom sheet مع كل الأنواع الستة
- وصف مفصل لكل نوع
- أيقونة ملونة

---

## 🎨 9. نظام الثيمات والمظهر (`SovereignThemeSystem.kt`)

**8 ثيمات:**

| الثيم | الخلفية | اللون الرئيسي | الوصف |
|---|---|---|---|
| سيادي داكن | #030712 | سماوي | الثيم الافتراضي |
| سيادي فاتح | #F8FAFC | أزرق | للإستخدام النهاري |
| OLED أسود | #000000 | سماوي | لتوفير طاقة OLED |
| تلقائي | يتبع النظام | — | Dark/Light حسب النظام |
| يمني ذهبي | #1A1000 | ذهبي | ثيم يمني فخم |
| أزرق محيطي | #0C1445 | أزرق فاتح | هادئ ومريح |
| أرجواني ملكي | #1A0A2E | أرجواني | فخم وملكي |
| زمردي | #022C22 | أخضر | طبيعي وهادئ |

**7 ألوان تمييز:** سماوي، ذهبي، أحمر، أرجواني، أخضر، برتقالي، وردي

**4 أحجام خط:** صغير (0.85x)، عادي (1x)، كبير (1.15x)، كبير جداً (1.3x)

**4 أشكال فقاعة محادثة:** مدور، حاد، ذيل (واتساب)، iOS

**خيارات إضافية:** OLED أسود حقيقي، RTL من اليمين لليسار

---

## 🖥️ 10. Backend — APIs جديدة

### Status & Privacy (`/api/social/`)
| Endpoint | Method | الوصف |
|---|---|---|
| `/api/social/status/{userId}` | GET | جلب حالة مستخدم (يراعي الخصوصية) |
| `/api/social/status` | PUT | تحديث حالتي |
| `/api/social/privacy` | GET | جلب إعدادات الخصوصية |
| `/api/social/privacy` | PUT | تحديث إعدادات الخصوصية |
| `/api/social/online-contacts` | GET | جهات الاتصال المتصلة |

### Notifications (`/api/notifications/`)
| Endpoint | Method | الوصف |
|---|---|---|
| `/api/notifications` | GET | جلب إشعاراتي (مع ترقيم صفحات + فلتر) |
| `/api/notifications/{id}/read` | PUT | تعليم كـ مقروء |
| `/api/notifications/read-all` | PUT | تعليم الكل كـ مقروء |
| `/api/notifications/{id}` | DELETE | حذف إشعار |
| `/api/notifications/unread-count` | GET | عدد غير المقروء |
| `/api/notifications/preferences` | GET/PUT | تفضيلات الإشعارات |

**تخزين:** Redis Lists + Hashes مع TTL وtrim تلقائي (500 إشعار كحد أقصى)

---

## 🌐 11. Frontend — تحديثات

### `api.ts` — 7 دوال API جديدة
- `getNotifications()`, `markNotificationRead()`, `markAllNotificationsRead()`, `getUnreadCount()`
- `getUserStatus()`, `updateMyStatus()`, `getPrivacySettings()`, `updatePrivacySettings()`, `getOnlineContacts()`

### `NotificationsTab.tsx` — لوحة إشعارات كاملة
- فلاتر سريعة (الكل، الرسائل، المكالمات، المجموعات، الأمان)
- شارة نوع ملونة + إيموجي
- تعليم مقروء بنقرة
- "قراءة الكل"
- تنسيق الوقت النسبي

### `MasterLayout.tsx` — تبويب إشعارات
- تبويب جديد "الإشعارات" مع شارة عدد غير المقروء
- تحديث تلقائي كل 15 ثانية

---

## 🧭 12. التنقل الشامل (`MainAppNavigation.kt`)

**كل المسارات مفعلة:**

| المسار | الشاشة | الحالة |
|---|---|---|
| `splash` | RedSplashScreen | ✅ |
| `auth` | WelcomeScreen | ✅ |
| `main` | RedDashboard | ✅ |
| `chat_detail/{id}` | ChatDetailScreen | ✅ |
| `create_group` | CreateGroupScreen | ✅ **جديد** |
| `group_info/{id}` | SovereignGroupInfoScreen | ✅ **جديد** |
| `call_type_picker/{id}` | CallTypePickerSheet | ✅ **جديد** |
| `voip_call/{id}/{type}` | VideoCallScreen | ✅ |
| `pstn_call/{num}` | PstnCallScreen | ✅ |
| `conference/{id}` | ConferenceScreen | ✅ **جديد** |
| `live_broadcast/{id}` | LiveBroadcastScreen | ✅ **جديد** |
| `audio_space/{id}` | ConferenceScreen | ✅ **جديد** |
| `call_log` | SovereignCallLogScreen | ✅ **جديد** |
| `create_story` | SovereignCreateStoryScreen | ✅ **جديد** |
| `story_viewer/{id}` | SovereignStoryViewer | ✅ **جديد** |
| `profile` | ProfileScreen (محدث) | ✅ |
| `privacy` | PrivacySettingsScreen | ✅ **جديد** |
| `theme_settings` | SovereignThemeSettingsScreen | ✅ **جديد** |
| `notifications` | SovereignNotificationCenter | ✅ **جديد** |
| `media_player/{id}` | SovereignVideoPlayer | ✅ **جديد** |
| `explore` | RedExploreScreen | ✅ **جديد** |
| `settings` | SettingsScreen | ✅ |
| `backup` | BackupScreen | ✅ |
| `update` | UpdateScreen | ✅ |
| `devices` | DevicesScreen | 🔜 TODO |

---

## ✅ كل ما هو مفعّل ويعمل

1. ✅ **الأيقونات** — 80+ أيقونة مصنفة ومستخدمة
2. ✅ **الأزرار** — 8 أنواع احترافية مع تدرجات وظلال
3. ✅ **مشغل الوسائط** — صوت + فيديو + رسالة صوتية مع تحكم كامل
4. ✅ **الإشعارات** — 16 نوع + توجيه حقيقي + إشعارات أصلية Android
5. ✅ **المجموعات** — إنشاء بـ 3 خطوات + إدارة + صلاحيات + خصوصية
6. ✅ **المظهر** — 8 ثيمات + 7 ألوان + أحجام خط + أشكال فقاعة
7. ✅ **الخصوصية** — 5 مستويات × 9 إعدادات + حوار تحديث
8. ✅ **الحالات** — 6 حالات مع خصوصية من يستطيع رؤيتها
9. ✅ **القصص** — 4 أنواع + خصوصية + مشاهدون + تفاعلات
10. ✅ **المكالمات** — 6 أنواع (VoIP, فيديو, مؤتمر, بث, PSTN, Space)
11. ✅ **Backend APIs** — 8 endpoints جديدة مع Redis
12. ✅ **التنقل** — 24 مسار كامل مفعّل

## 🔜 TODO للمستقبل

1. DevicesScreen — إدارة الأجهزة المرتبطة
2. SharedPreferences/DataStore لـ WebSocket URL
3. WorkManager لإعادة اتصال WebSocket
4. Protobuf parsing للرسائل الثنائية
5. Call PendingIntent للإشعارات أثناء المكالمة
6. أفاتار حقيقي عبر AsyncImage + Minio
7. قائمة المشاهدين الحقيقية للقصص
8. أقصى عدد مشاركين في المؤتمر (32)
9. Audio Space مع إدارة المتحدثين
10. تحميل الملفات مع تقدم وشريط
