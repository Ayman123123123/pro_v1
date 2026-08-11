#!/usr/bin/env python3
"""
فاحص تكامل تطبيق يونس السيادي — يمنع ارتداد الأعطال المؤكّدة.
-------------------------------------------------------------------
يفحص:
1) عقد API: مطابقة طلبات التطبيق بمسارات الخادم (الفعل + المسار)
2) عطل التصريف: الدوال المُستدعاة غير المعرّفة
3) arity: ApiResult.Success يجب أن يأخذ وسيطين
4) سباق التوكن: يجب وجود Mutex حول تحديث التوكن
5) التحويلات غير الآمنة: 'as String' / 'as List<' بلا '?' في الـ controllers
6) ProGuard: يجب أن يحوي قواعد للـ Serializable وWebRTC وRoom

يلتقط الأعطال التي ظهرت في التدقيق العميق ويمنع عودتها.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RED_APP = ROOT / "red-app/src/main/java/com/red/sovereign"
BACKEND = ROOT / "backend-server/src/main/kotlin/com/red/server"
PROGUARD = ROOT / "red-app/proguard-rules.pro"

failures = []
passes = 0


def check(name: str, ok: bool, detail: str = ""):
    global passes
    if ok:
        passes += 1
    else:
        failures.append(f"❌ {name}: {detail}")


# ─── 1. عقد API: unblock يجب أن يكون DELETE /block ───────────────────
dir_vm = (RED_APP / "contacts/DirectoryViewModel.kt").read_text(encoding="utf-8")
check(
    "unblock: التطبيق يستخدم DELETE /api/contacts/{redId}/block",
    re.search(r'unblock.*?client\.request\("DELETE",\s*"/api/contacts/\$\{profile\.redId\}/block"\)', dir_vm, re.DOTALL) is not None,
    "الطلب يجب أن يكون DELETE /block لا POST /unblock (404 مؤكّد)",
)

# ─── 2. عطل التصريف: الدوال الثلاث يجب أن تكون معرّفة ──────────────────
call_service = (RED_APP / "calls/YounesCallService.kt").read_text(encoding="utf-8")
phone_recv = (RED_APP / "calls/PhoneStateReceiver.kt").read_text(encoding="utf-8")
for fn in ["silenceRinger", "holdActiveCall", "resumeRinger"]:
    defined = re.search(rf"fun {fn}\(", call_service) is not None
    check(f"عطل تصريف: YounesCallService.{fn} معرّفة", defined, f"{fn} مستدعاة من PhoneStateReceiver لكن غير معرّفة")
    # يجب أن تمرّر context (لا نداء بلا وسيط)
    check(f"{fn}: PhoneStateReceiver يمرّر context", f"{fn}(context)" in phone_recv, "يجب تمرير context لتجنّب مرجع ساكن")

# ─── 3. ApiResult.Success arity: وسيطان دائمًا ─────────────────────────
auth_api = (RED_APP / "auth/AuthApi.kt").read_text(encoding="utf-8")
m = re.search(r"data class Success<.*?>\(val (\w+): \w+, val (\w+): \w+\)", auth_api)
check("ApiResult.Success: توقيعه وسيطان (code, value)", m is not None, "Success يجب أن يأخذ (code, value)")
if m:
    # كل نداء في AuthorizedApiClient يجب أن يمرّر وسيطين
    api_client = (RED_APP / "auth/AuthorizedApiClient.kt").read_text(encoding="utf-8")
    one_arg = re.findall(r"ApiResult\.Success\([^,\n)]+\)\s*$", api_client, re.MULTILINE)
    one_arg = [s for s in one_arg if "ApiResult.Success(" in s and s.count(",") == 0]
    # عدّ الـ Success( ذات الوسيط الواحد فقط
    bad = re.findall(r"ApiResult\.Success\([^\s,)]+\)", api_client)
    check("ApiResult.Success: كل النداءات تمرّر وسيطين في AuthorizedApiClient", len(bad) == 0, f"وجدت {len(bad)} نداء بوسيط واحد: {bad[:3]}")

# ─── 4. سباق التوكن: Mutex موجود ───────────────────────────────────────
check(
    "سباق التوكن: Mutex معرّف في AuthorizedApiClient",
    "Mutex" in api_client and "withLock" in api_client,
    "يجب وجود Mutex مع withLock لمنع الطرد الجماعي عند 401 متوازي",
)
api_client = (RED_APP / "auth/AuthorizedApiClient.kt").read_text(encoding="utf-8")
# تجاهل التعليقات عند فحص runBlocking
api_client_code = "\n".join(l for l in api_client.splitlines() if not l.strip().startswith("//") and "لا runBlocking" not in l)
check(
    "سباق التوكن: لا runBlocking في AuthorizedApiClient",
    "runBlocking" not in api_client_code,
    "runBlocking داخل Dispatchers.IO خطر جمود — استخدم استدعاء suspend مباشر",
)

# ─── 5. التحويلات غير الآمنة في controllers ───────────────────────────
for ctrl in [BACKEND / "admin/controller/ContentController.kt", BACKEND / "admin/controller/AdminV2Controller.kt"]:
    text = ctrl.read_text(encoding="utf-8")
    unsafe = re.findall(r'\bas String\b(?!\?)', text)  # 'as String' بلا '?'
    # استثنِ التعليقات
    code_lines = [l for l in text.splitlines() if not l.strip().startswith("//")]
    code = "\n".join(code_lines)
    unsafe_count = len(re.findall(r'\bas String\b(?!\?)', code))
    check(f"{ctrl.name}: لا تحويلات 'as String' غير آمنة", unsafe_count == 0, f"وجدت {unsafe_count} تحويل غير آمن ⇒ 500 بدل 400")

# ─── 6. ProGuard: قواعد كافية ─────────────────────────────────────────
if PROGUARD.exists():
    pg = PROGUARD.read_text(encoding="utf-8")
    check("ProGuard: قواعد @Serializable", "Serializable" in pg and "kotlinx.serialization" in pg, "انهيار JSON في الإصدار بدونها")
    check("ProGuard: قواعد WebRTC", "org.webrtc" in pg, "تعطّل المكالمات في الإصدار بدونها")
    check("ProGuard: قواعد Room", "room" in pg.lower(), "فشل الاستعلامات في الإصدار بدونها")
    check("ProGuard: قواعد OkHttp", "okhttp3" in pg, "تحذيرات/كسر الشبكة بدونها")
else:
    check("ProGuard: الملف موجود", False, "proguard-rules.pro غير موجود")

# ─── 7. votePoll: لا 'success' to true أعمى + تحقق optionId ────────────
content_service = (BACKEND / "admin/service/ContentService.kt").read_text(encoding="utf-8")
check(
    "votePoll: تحقق optionId ينتمي للاستطلاع",
    "validOptionIds" in content_service and "INVALID_OPTION" in content_service,
    "يجب التحقق أن optionId ينتمي للاستطلاع لمنع إفساد النتائج",
)
# vote لا يجب أن يحوي عودة صامتة (return بلا throw)
vote_section = re.search(r"fun vote\(.*?\n\}(?:\s*\n)", content_service, re.DOTALL)
if vote_section:
    check("vote: أخطاء صريحة (throw) بدل عودة صامتة", "throw" in vote_section.group(0), "العيدة الصامتة تكذب على المستخدم")

# ─── 8. events GET endpoint ────────────────────────────────────────────
content_ctrl = (BACKEND / "admin/controller/ContentController.kt").read_text(encoding="utf-8")
check(
    "events: @GetMapping('/events/{eventId}') معرّف",
    re.search(r'@GetMapping\("/events/\{eventId\}"\)', content_ctrl) is not None,
    "التطبيق يطلب GET /events/{id} — يجب endpoint مطابق",
)

# ─── 9. N+1 poll deletion ─────────────────────────────────────────────
check(
    "deletePoll: حذف مجمّع (deleteAllByPollId) بدل N+1",
    "deleteAllByPollId" in content_service,
    "findByPollId().forEach{delete} = N+1 استعلامات",
)

# ─── 10. قناة red_calls: لا تعارض أهمية ────────────────────────────────
router = (RED_APP / "core/network/SovereignNotificationRouter.kt").read_text(encoding="utf-8")
# red_calls يجب أن تكون IMPORTANCE_HIGH (لا MAX) في الراوتر
max_for_red_calls = re.search(r'NotificationChannel\(CHANNEL_CALLS,.*?IMPORTANCE_MAX\)', router, re.DOTALL)
check("قناة red_calls: لا IMPORTANCE_MAX في الراوتر (تعارض)", max_for_red_calls is None, "الراوتر يجب أن يستخدم IMPORTANCE_HIGH كالبقية")

# ─── 11. CallTelemetry: لا تسريب نطاق ─────────────────────────────────
telemetry = (RED_APP / "calls/CallTelemetry.kt").read_text(encoding="utf-8")
check(
    "CallTelemetry.flush: نطاق مشترك بـ SupervisorJob (لا تسريب)",
    "SupervisorJob" in telemetry and "val scope = CoroutineScope(Dispatchers.IO)" not in telemetry,
    "CoroutineScope محلي لكل نداء = نطاق يتيم لا يموت",
)

# ─── 12. AppLock: LocalFragmentActivity محذوف من Compose الحديث ─────────
applock = (RED_APP / "security/AppLockScreen.kt").read_text(encoding="utf-8")
applock_code = "\n".join(l for l in applock.splitlines() if not l.strip().startswith("//"))
check(
    "AppLock: لا LocalFragmentActivity (محذوف من Compose الحديث)",
    "LocalFragmentActivity" not in applock_code,
    "استخدم (LocalContext.current) as? FragmentActivity بدلاً منه",
)

# ─── 13. AppLock: الفشل لا يفتح التطبيق (أمن) ───────────────────────────
# onUnlocked يجب أن يُستدعى فقط من onAuthenticationSucceeded، لا من onFailure
check(
    "AppLock: الفشل لا يفتح التطبيق (القفل إجباري)",
    "onFailure { onUnlocked() }" not in applock and ".onFailure { onUnlocked() }" not in applock,
    "onFailure { onUnlocked() } يجعل القفل تجميلياً — أي فشل بصمة يفتح التطبيق",
)
check(
    "AppLock: onAuthenticationSucceeded يفتح التطبيق",
    "onAuthenticationSucceeded" in applock and "onUnlocked()" in applock,
    "النجاح وحده يجب أن يفتح التطبيق",
)

# ─── 14. MediaGallery: لا مشاركة حالة AttachmentViewModel بين الخلايا ────
gallery = (RED_APP / "features/media/MediaGallery.kt").read_text(encoding="utf-8")
# قراءة attachments.state في خلية الشبكة = كل الخلايا تعرض نفس الصورة (عيب)
check(
    "MediaGallery: لا قراءة attachments.state في خلية الشبكة (حالة مشتركة)",
    "attachments.state as? AttachmentState.Downloaded" not in gallery,
    "AttachmentViewModel مصمّم لرسالة واحدة — مشاركته بين الخلايا تعرض نفس الصورة للكل",
)


# ─── الخلاصة ──────────────────────────────────────────────────────────
print("════════════════════════════════════════════════")
print("  🛡️ فاحص تكامل تطبيق يونس السيادي")
print("════════════════════════════════════════════════")
print(f"  ✅ {passes} فحص ناجح")
if failures:
    print(f"  ❌ {len(failures)} فشل:")
    for f in failures:
        print(f"     {f}")
    sys.exit(1)
else:
    print("  النتيجة: سليم ✅ — كل العيوب المؤكّدة مُتحكّمٌ بها")
    sys.exit(0)
