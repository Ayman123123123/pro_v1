# تفكيك RedDashboard.kt (1787 سطر → 5 شاشات)

## الخطة المصدق عليها من 31-ROADMAP

`RedDashboard.kt` الحالي يحوي 29 دالة @Composable في ملف واحد (1787 سطر). هذا يخالف مبدأ UDF + StateFlow + Navigation 3.

### التقسيم المقترح (بدون فقدان ميزة):

| الملف الجديد | الدوال المنقولة | المسؤولية |
|---|---|---|
| `screens/HomeScreen.kt` | `FeedScreen`, `StoryCircle`, `PostCard`, `PostAction`, `Avatar`, `GroupAvatar` | الرئيسية + المنشورات |
| `screens/ChatScreen.kt` | `ChatHubScreen`, `resolveRichMessages`, `RichTextMessage`, `VoiceWaveform`, `VoiceMessage`, `AttachmentMessage`, `ImageMessage` | الدردشات الخاصة E2EE |
| `screens/GroupsScreen.kt` | `GroupList`, `GroupDetail`, `GroupAvatar` (المجموعات) | المجموعات (غير E2EE بعد) |
| `screens/CallsScreen.kt` | `UnifiedCallsScreen`, `CallHistoryRow`, `RoundCallAction` | سجل المكالمات الموحد |
| `screens/MoreScreen.kt` | `MoreScreen`, `MoreOption`, `DinstarPhoneScreen`, `DialPad`, `CreateSheet` | المزيد + DINSTAR |

### الباقي في RedDashboard.kt:
- `RedDashboard` (الـ Scaffold + NavigationBar 5 تبويبات) فقط ~150 سطر
- `RedTopBar` + `EmptyState` + `resolveRichMessages` (مشترك)

### الخطوات:
1. إنشاء 5 ملفات فارغة مع package + imports
2. نسخ كل دالة كما هي (لا منطق جديد)
3. تحديث imports في RedDashboard.kt
4. اختبار `assembleDebug` يجب أن يبقى أخضر

> **الحالة:** هذه الوثيقة هي خطة. الملفات الـ 5 ستُنشأ في الـ Commit التالي وتُنقل تدريجياً — RedDashboard.kt الحالي يبقى يعمل حتى اكتمال النقل (لا كسر).
