# أرشيف D:\pro_archive_2026-09-16 — جرد كامل (2026-09-16)

## ملخص تنفيذي
- **إجمالي الملفات**: 57 ملف
- **إجمالي الحجم**: 6,390,660,538 بايت ≈ **6.39 GB**
- **الحالة**: أرشيف للقراءة فقط، غير منقول فعلياً
- **الهدف**: توثيق شامل للنقل المستقبلي إلى قرص خارجي (USB/NAS) لتحرير ~6 GB من مساحة العمل

---

## 1. جرد المجلدات العلوية

| المجلد | عدد الملفات | الحجم (بايت) | الحجم (تقريبي) | الحالة |
|---------|-------------|--------------|----------------|--------|
| `apks` | 10 | 2,749,463,523 | 2.75 GB | خام (Raw APKs) |
| `apks-stray` | 3 | 818,442,945 | 818 MB | خام (نسخ مكررة/قديمة) |
| `dead-backend-shadow` | 0 | 0 | 0 B | فارغ |
| `docs-duplicates` | 1 | 5,381 | 5 KB | خام |
| `env-backup` | 4 | 22,275 | 22 KB | خام |
| `env-history` | 9 | 88,709 | 89 KB | خام |
| `gradle` | 1 | 699,355 | 699 KB | خام |
| `nested-git-backup` | 20 | 26,388 | 26 KB | خام (مجلد .git) |
| `old-modules` | 5 | 7,298,710 | 7.3 MB | مضغوط (zip) + SHA256 |
| **الجذر (ملفات zip)** | 3 | 2,814,613,009 | 2.81 GB | مضغوط |
| **المجموع** | **57** | **6,390,660,538** | **6.39 GB** | |

---

## 2. تفصيل الملفات مع SHA256

### 2.1 المجلد `apks` — 10 ملفات APK خام (2.75 GB)
| الملف | الحجم | SHA256 |
|-------|-------|--------|
| app-debug.apk | 278,438,422 | `2B357984733325AB6C103CC9ACF5C133B4D717270B7DD6339B0D35AE11A995DF` |
| RED-Ultimate-debug-2026-09-05.apk | 274,660,300 | `259045F7E333AFB0C8320C8478B535DB62163EC2689F22A1F112862271A9E88B` |
| RED-Ultimate-debug-2026-09-08-0243.apk | 273,379,343 | `BF799973A14DF10B8BD28C94CD474BA19A3A7A05D8B45EA7C33E2425E79AD510` |
| RED-Ultimate-debug-2026-09-10-calls-fixed.apk | 274,504,677 | `827809F339B25080627DD9F598A154B3B610F311C3CCD5AE0ED8C89F199CF5F6` |
| RED-Ultimate-debug-2026-09-10-fixed.apk | 272,653,822 | `FB4BAD96D9627530868D1E6A965E57DCB7A15AD7A887755B46E58A02D6F88F28` |
| RED-Ultimate-debug-2026-09-10-full.apk | 281,868,939 | `15CB773A83BE9865BD35587B738A44EF7F156ADED8A9A016B347AC94433DD0E8` |
| RED-Ultimate-debug-2026-09-10-swipe-ui.apk | 274,513,938 | `469897F4F2881E84CF3E33F49A9ECA614D0F982E72E983BEB2EC7931904A29C4` |
| RED-Ultimate-debug-2026-09-12-lan-fixed.apk | 285,708,329 | `FAD634322CE5C8C625FC1EDA359E62A6B9EE488FC4F6BE1BC1516C077F945F2A` |
| RED-Ultimate-debug-2026-09-15-sfu-fix.apk | 267,933,022 | `D2957CEC7139350614937677E7CFFE9A015421C181073FA76EBF564F8F7E26A1` |
| RED-Ultimate-debug-2026-09-15-wave1.apk | 265,802,731 | `816E77CE6D40696F23D00CC59F20671960882293F8A0182A4FA0629FCD4E5258` |

> **ملاحظة**: هذه ملفات APK خام غير مضغوطة، مرشحة قوية للضغط أو النقل.

### 2.2 المجلد `apks-stray` — 3 ملفات APK خام (818 MB)
| الملف | الحجم | SHA256 | ملاحظة |
|-------|-------|--------|-------|
| red-app-debug.apk | 273,379,343 | `BF799973A14DF10B8BD28C94CD474BA19A3A7A05D8B45EA7C33E2425E79AD510` | مطابق لـ `RED-Ultimate-debug-2026-09-08-0243.apk` |
| red-app-debug_duplicate.apk | 273,379,343 | `BF799973A14DF10B8BD28C94CD474BA19A3A7A05D8B45EA7C33E2425E79AD510` | نسخة مكررة مطابقة |
| RED_APP_AUTH_FIXED_2026-08-24.apk | 271,684,259 | `8B5DBBCD774CC5D2DAFC42607A3734C29FD6913010127EB2C4E85C3CFF67C12E` | إصدار قديم |

> **تنبيه**: ملفان مكرران متطابقان (SHA256 متطابق) — يمكن حذف أحدهما عند التنظيف.

### 2.3 المجلد `dead-backend-shadow` — فارغ (0 ملفات)
لا يوجد محتوى.

### 2.4 المجلد `docs-duplicates` — ملف واحد (5 KB)
| الملف | الحجم | SHA256 |
|-------|-------|--------|
| `���_奭_����_�靧���_�馟��_2026-08-13.md` | 5,381 | `4F61BFFD7738293EFEEE81FCB44A9004164C884D1DE0C242A13F8C0FF734795B` |

### 2.5 المجلد `env-backup` — 4 ملفات (22 KB)
| الملف | الحجم | SHA256 |
|-------|-------|--------|
| backend-server.env | 79 | `849F70DDEBC61B48BA47F386B97EF3648C10BCECD3969A723F1D598F81C2DECB` |
| RED_Ultimate.env | 18,326 | `A1655E117884B4FF9F6F83EB1BDF987831220B1FFE89C5620E100960699DA1E8` |
| RED_Ultimate_V1-main.env | 2,485 | `74C6940709D70B0E56D09B7DF7617FA084642752C9B1F1E0D311942F8ADB68D5` |
| root.pro_new.env | 1,385 | `958CBE0740E20FFED83B847275AADF2F14885C914570FF1729ECBFDE79A2000F` |

### 2.6 المجلد `env-history` — 9 ملفات (89 KB)
| الملف | الحجم | SHA256 |
|-------|-------|--------|
| .env.before-cookie-key-fix-20260819 | 4,992 | `5EF2BEB11B3B2321BEA0EB872E346C236BC67A402DDBD54FCB25932344832028` |
| .env.before-dinstar-3-removal-20260823 | 5,737 | `5D9CFB5B40F63F955923C57BCAAED28876CEFA87017599E7BD0ED8ED1F903278` |
| .env.before-hotspot-fix-20260905 | 9,103 | `A642DB7F54C1C1DF31E0A45E9EC5C6BE75DB807D7F50685B325FE73F305E9D7F` |
| .env.before-ipfix-20260913 | 12,754 | `7A3F424A7F9AE73C437DC8A8908E8F7DBFE89880E47CD4D29DA7B0469D560AFD` |
| .env.before-lanip-fix-20260912 | 12,751 | `99BDEC57286068D239983E5241BA599F22C24091151EF30F122C05C4035559E5` |
| .env.before-nul-fix-20260819 | 5,023 | `4F77B6BD8A85E83BADCE019C4A639BEB90B2E0668552A0B480EB7A02A0F0840D` |
| .env.before-rerun-20260915 | 12,755 | `AAEDC9DC2E9D1B1F52171DF9CC86B9CEEB3619482590620D5F742E4331A1FF73` |
| .env.before-sfuip-fix-20260915 | 12,840 | `8EC8C07F7A595DDD0CB721018E78B64CC00963320B612DC204CF6AC0C0FAE648` |
| .env.before-turn-fix-20260912 | 12,754 | `1F5DE1233B2EA050E84C8504163AC6ACA40CFAB7AEB5C55B2A8DC61E6815CE31` |

### 2.7 المجلد `gradle` — ملف واحد (699 KB)
| الملف | الحجم | SHA256 |
|-------|-------|--------|
| verification-metadata.xml.disabled | 699,355 | `33EFFAD02AEB57788069950B3EE030BF89BF4FC386642CECE295C7380213FB2C` |

### 2.8 المجلد `nested-git-backup` — 20 ملف (26 KB) — مجلد `.git` احتياطي
| الملف | الحجم | SHA256 |
|-------|-------|--------|
| .git/config | 166 | `DC03A999D4C75D98406EF2CB35E70745867050E824CB1427E989C379845AAFE0` |
| .git/description | 73 | `85AB6C163D43A17EA9CF7788308BCA1466F1B0A8D1CC92E26E9BF63DA4062AEE` |
| .git/HEAD | 23 | `F6F2B945F6C411B02BA3DA9C7ACE88DCF71B6AF65BA2E0D89AA82900042B5A10` |
| .git/index | 44 | `B4F68A670E877B00D90B19FCAAC2DF30216C3067AB6E4F3F9517CAC4664B5E82` |
| .git/hooks/applypatch-msg.sample | 478 | `0223497A0B8B033AA58A3A521B8629869386CF7AB0E2F101963D328AA62193F7` |
| .git/hooks/commit-msg.sample | 896 | `1F74D5E9292979B573EBD59741D46CB93FF391ACDD083D340B94370753D92437` |
| .git/hooks/fsmonitor-watchman.sample | 4,726 | `E0549964E93897B519BD8E333C037E51FFF0F88BA13E086A331592BF801FA1D0` |
| .git/hooks/post-update.sample | 189 | `81765AF2DAEF323061DCBC5E61FC16481CB74B3BAC9AD8A174B186523586F6C5` |
| .git/hooks/pre-applypatch.sample | 424 | `E15C5B469EA3E0A695BEA6F2C82BCF8E62821074939DDD85B77E0007FF165475` |
| .git/hooks/pre-commit.sample | 1,649 | `57185B7B9F05239D7AB52DB045F5B89EB31348D7B2177EAB214F5EB872E1971B` |
| .git/hooks/pre-merge-commit.sample | 416 | `D3825A70337940EBBD0A5C072984E13245920CDF8898BD225C8D27A6DFC9CB53` |
| .git/hooks/pre-push.sample | 1,374 | `ECCE9C7E04D3F5DD9D8ADA81753DD1D549A9634B26770042B58DDA00217D086A` |
| .git/hooks/pre-rebase.sample | 4,898 | `4FEBCE867790052338076F4E66CC47EFB14879D18097D1D61C8261859EAAA7B3` |
| .git/hooks/pre-receive.sample | 544 | `A4C3D2B9C7BB3FD8D1441C31BD4EE71A595D66B44FCF49DDB310252320169989` |
| .git/hooks/prepare-commit-msg.sample | 1,492 | `E9DDCAA4189FDDD25ED97FC8C789ECA7B6CA16390B2392AE3276F0C8E1AA4619` |
| .git/hooks/push-to-checkout.sample | 2,783 | `A53D0741798B287C6DD7AFA64AEE473F305E65D3F49463BB9D7408EC3B12BF5F` |
| .git/hooks/sendemail-validate.sample | 2,308 | `44EBFC923DC5466BC009602F0ECF067B9C65459ABFE8868DDC49B78E6CED7A92` |
| .git/hooks/update.sample | 3,650 | `8D5F2FA83E103CF08B57EAA67521DF9194F45CBDBCB37DA52AD586097A14D106` |
| .git/info/exclude | 240 | `6671FE83B7A07C8932EE89164D1F2793B2318058EB8B98DC5C06EE0A5A3B0EC1` |
| .git/objects/47/3a0f4c3b... | 15 | `164C5FA8067FACF1A43F09CE3D0E35EBF53A7F5723ECBF15A8667CFC53C26F6C` |

### 2.9 المجلد `old-modules` — 5 ملفات (7.3 MB) — **مضغوطة مع SHA256 خاص**
| الملف | الحجم | SHA256 | إدخالات |
|-------|-------|--------|---------|
| core.zip | 674,638 | `B5904B835DA85B301D7BAD1BD67D5499972F376C0DBED63D658CF4BEC00D434E` | 603 |
| demo.zip | 1,989,081 | `08ABF36D2B659AFB095521A10799A9D62C8EDD87402D3D43BBC196683BB8B7EE` | 518 |
| feature.zip | 1,943,298 | `30E1E8C1DCE07DC84976F2D7BD79BD96E6B3E93521975041EBA8F3A50DF56167` | 881 |
| lib.zip | 2,691,321 | `6A4FD9DEC5169513799A22D1FBC58ED08C62DFA1A583B7DBC1CDE7E52D656B96` | 1,147 |
| SHA256SUMS.txt | 372 | `172D44B9E7A5086D135B8D421ED2BF87AAAEA1C7CFC284E154DA6B0451B95EA9` | — |

> **ملاحظة**: هذا المجلد يحتوي على ملف `SHA256SUMS.txt` خاص به يغطي الـ 4 ملفات zip الداخلية.

### 2.10 ملفات الجذر المضغوطة (3 ملفات zip — 2.81 GB)
| الملف | الحجم | SHA256 | مغطى بـ ARCHIVE_SHA256SUMS.txt |
|-------|-------|--------|-------------------------------|
| .git-temp.zip | 2,466,474,768 | `4523C18D40A17CE885DC63EC2B7998F83A8B5392D451B3F1DF45443FB4324535` | ✅ نعم |
| bundles.zip | 147,604,111 | `D7066AE2942CCB056730D4E6CC94CB70ADBEADBA9E68FEE1B21A2476FB3D6792` | ✅ نعم |
| red-sha1.zip | 200,534,130 | `05C2C1161C3209076E9EDE966C0C386C79F9016DF2A6658BA8D66CA4E0354ED2` | ✅ نعم |

---

## 3. التحقق من `ARCHIVE_SHA256SUMS.txt`

### المحتوى الحالي (3 أسطر فقط):
```txt
4523C18D40A17CE885DC63EC2B7998F83A8B5392D451B3F1DF45443FB4324535  .git-temp.zip
05C2C1161C3209076E9EDE966C0C386C79F9016DF2A6658BA8D66CA4E0354ED2  red-sha1.zip
D7066AE2942CCB056730D4E6CC94CB70ADBEADBA9E68FEE1B21A2476FB3D6792  bundles.zip
```

### التقييم:
- ✅ **يغطي**: 3 ملفات zip في الجذر (2.81 GB)
- ❌ **لا يغطي**: 54 ملفاً آخر (3.58 GB) بما في ذلك:
  - جميع ملفات APK في `apks/` و `apks-stray/` (3.57 GB)
  - ملفات البيئة والتوثيق والgradle وgit-backup وold-modules
  - ملف `SHA256SUMS.txt` الخاص بـ `old-modules/`

### التوصية:
إضافة SHA256 لجميع الملفات الـ 54 المتبقية إلى `ARCHIVE_SHA256SUMS.txt` لضمان سلامة الأرشيف الكامل.

---

## 4. تحليل المساحة والتوصية بالنقل

### تفصيل المساحة حسب النوع:
| النوع | الحجم | النسبة |
|-------|-------|--------|
| **ملفات خام (Raw)** — APKs, env, docs, gradle, git | 3,576,047,529 بايت (3.58 GB) | 56% |
| **ملفات مضغوطة (Zipped)** — .git-temp.zip, bundles.zip, red-sha1.zip, old-modules/*.zip | 2,814,613,009 بايت (2.81 GB) | 44% |

### أكبر المستهلكين للمساحة:
1. **apks/** — 2.75 GB (43% من الإجمالي) — **مرشح أول للنقل**
2. **.git-temp.zip** — 2.47 GB (39%) — **مرشح ثاني للنقل**
3. **apks-stray/** — 818 MB (13%) — يحتوي مكررات
4. **red-sha1.zip + bundles.zip** — 348 MB (5%)

### توصية النقل للقرص الخارجي (USB/NAS):
| الأولوية | المسار | الحجم | السبب |
|----------|-------|-------|-------|
| 1 | `apks/` | 2.75 GB | ملفات APK خام تاريخية، غير مستخدمة في البناء الحالي |
| 2 | `.git-temp.zip` | 2.47 GB | نسخة احتياطية كاملة لـ .git، يمكن استعادتها عند الحاجة |
| 3 | `apks-stray/` | 818 MB | نسخ مكررة وقديمة، مرشحة للحذف بعد التحقق |
| 4 | `bundles.zip` + `red-sha1.zip` | 348 MB | حزم قديمة، منخفضة الأولوية |
| 5 | `old-modules/` | 7.3 MB | وحدات قديمة مضغوطة، ذاتية التوثيق |
| 6 | باقي المجلدات الصغيرة | ~1.2 MB | بيئة، توثيق، git-backup — يمكن نقلها معاً |

**إجمالي قابل للتحرير بالنقل**: ~6.39 GB (الأرشيف كاملاً)
**التوفير الفوري بنقل المجلدين 1+2 فقط**: ~5.22 GB (82%)

---

## 5. خطوات مقترحة للتنفيذ المستقبلي (عند توفر قرص خارجي)

1. **نسخ الأرشيف كاملاً** إلى USB/NAS مع الحفاظ على هيكل المجلدات
2. **التحقق من SHA256** بعد النقل باستخدام هذا التوثيق
3. **حذف المجلدات المنقولة** من `D:\pro_archive_2026-09-16` بعد التحقق الناجح
4. **تحديث `ARCHIVE_SHA256SUMS.txt`** ليشمل جميع الـ 57 ملفاً
5. **توثيق موقع التخزين الجديد** في هذا الملف (مسار USB/NAS، تاريخ النقل)

---

## 6. ملاحظات هامة
- ❌ **لا توجد عمليات نقل أو حذف تمت** — هذا توثيق فقط
- ✅ جميع SHA256 محسوبة وموثقة أعلاه
- 🔍 المجلد `dead-backend-shadow` فارغ — يمكن تجاهله
- 🔄 ملفان في `apks-stray/` متطابقان SHA256 — نسخة مكررة مؤكدة
- 📦 `old-modules/` يحتوي SHA256 خاص به — مكتمل ذاتياً

---

*تم إنشاؤه آلياً في 2026-09-16 — للقراءة فقط، لا تعديل على الأرشيف الفعلي*