# 02 — قواعد البيانات والتخزين

## توزيع المسؤوليات

| النظام | البيانات | المبدأ |
|---|---|---|
| PostgreSQL 16 | الحسابات، الأجهزة، refresh sessions، recovery، صلاحيات PSTN، audit، public pre-keys | بيانات علائقية وقيود/معاملات |
| MongoDB 8 | ciphertext messages، sequences، posts، groups، stories، call history | مستندات وتصفح زمني |
| Redis 7 | rate limits، عدادات PSTN اليومية، حالة قصيرة العمر | لا يُعامل كمصدر دائم وحيد |
| MinIO | صور/فيديو/مرفقات | object storage محلي مصادق |
| Android SQLite | sessions/pre-keys/sender keys/ciphertext metadata | السجلات التشفيرية مشفرة عبر Keystore |

## PostgreSQL وFlyway

المهاجرات القانونية داخل `backend-server/src/main/resources/db/migration/`:

| Migration | الغرض |
|---|---|
| V1 | schema الأولي للمستخدمين والبنية الأساسية |
| V2 | إعدادات DINSTAR القديمة/الرئيسية |
| V3 | username وRED ID وحالات الموافقة دون هاتف |
| V4 | الأجهزة وشهادات الهوية وrefresh sessions |
| V5 | `pstn_enabled` والحد اليومي |
| V6 | recovery codes أحادية الاستخدام ومجزأة |
| V7 | audit trail دائم |
| V8 | registration/protocol IDs وpublic signed/Kyber metadata؛ إلغاء الأجهزة القديمة غير الآمنة |
| V9 | مخزون one-time EC/Kyber public pre-keys واستهلاكها |

Hibernate مضبوط على `ddl-auto: validate`؛ Flyway هو مالك schema. يجب اختبار V1→V9 على PostgreSQL حقيقي قبل الدمج النهائي.

## الاستهلاك الذري لـ PreKeys

V9 يخزن **المفاتيح العامة فقط**. الاستهلاك يستخدم معاملة PostgreSQL و`FOR UPDATE SKIP LOCKED` حتى لا يستلم مرسلان الزوج نفسه. EC وKyber يُحدثان معًا؛ lookup العادي لا يحرق مفتاحًا، والاستهلاك يحدث عند غياب session.

Android يجدد المخزون إلى هدف 50 زوجًا عندما ينخفض عن 20. المفتاح المستهلك لا يُعاد تفعيله بـ upsert، والمواد الخاصة تبقى على الهاتف.

## MongoDB

المجموعات المنطقية التي تستخدمها الخدمات تشمل:

- الرسائل والمحادثات ذات sequence ثابت وUUID v7.
- حالات التسليم والحذف الناعم والتسليم دون اتصال.
- المنشورات، الردود/threads، quotes، polls وreactions.
- following والعروض ALL/FOLLOWING/YEMEN.
- groups وgroup members/roles.
- stories ومشاهدات 24 ساعة.
- سجل المكالمات الموحد مع route/type/status.

اسم collection النهائي تحدده annotations/`MongoTemplate` في كل model. لا تعتمد أسماء تخمينية في عمليات النسخ؛ افحص `mongosh show collections` بعد تشغيل النسخة.

## Redis

المفاتيح قصيرة العمر تشمل:

- registration/login/recovery rate limits.
- عداد يومي PSTN وفق توقيت `Asia/Aden`.
- حالات تشغيلية مؤقتة.

Compose يشغل Redis مع password وAOF. تجاوز حد PSTN يعيد الحجز ولا يستدعي Asterisk. يمنع استخدام `KEYS` في مسارات الإنتاج؛ استخدم عمليات محددة أو SCAN عند الحاجة الإدارية.

## MinIO

- bucket محلي للوسائط.
- رفع streaming مصادق وحد أقصى 100MiB وMIME allowlist.
- object keys عشوائية user-scoped.
- تنزيل مصادق؛ لا تجعل bucket عامًا لعرض الصور.
- تنظيف stories المنتهية يزيل metadata وobject.

ما يزال مطلوبًا قبل الإنتاج: thumbnails، فحص malware، orphan cleanup شامل، تشفير cache على Android، ونسخ احتياطي/استعادة مثبت.

## تخزين Android

`PersistentSignalProtocolStore` ينفذ مخازن libsignal للهوية وpre-key/signed/Kyber/session/sender-key. كل record cryptographic مشفر قبل SQLite بواسطة `ProtocolRecordCipher` وAndroid Keystore. plaintext الرسائل يمر عبر bus مؤقت؛ مخزن الرسائل يحفظ ciphertext والحالة.

ما يزال routing metadata ليس SQLCipher كاملًا. تغيير مفتاح هوية remote يُرفض عبر trust comparison، لكن واجهة Safety Number/QR ما تزال مطلوبة.

## النسخ الاحتياطي

النسخة الصحيحة تشمل معًا:

1. `.env` ومفاتيح سلطة الهوية في مخزن منفصل مشفر.
2. `pg_dump` لـ PostgreSQL.
3. `mongodump` لـ MongoDB.
4. Redis AOF عند الحاجة لاستمرارية العدادات.
5. `mc mirror` أو snapshot لـ MinIO.

لا تعتبر backup صالحًا قبل restore drill على بيئة منفصلة والتحقق من الحسابات والوسائط والشهادات. لا تُرفع النسخ أو الأسرار إلى Git.
