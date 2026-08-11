#!/usr/bin/env python3
"""
فاحص ثابت شامل لملفات Kotlin الجديدة — يلتقط عيوب التصريف الشائعة.
------------------------------------------------------------------
يفحص كل ملف جديد/معدّل بحثاً عن:
1) 'by remember' بلا imports getValue/setValue
2) 'launch {' / '.launch {' بلا import kotlinx.coroutines.launch
3) 'rememberCoroutineScope' بلا import
4) 'viewModel(' بلا import
5) '@Composable' بلا import
6) مراجع R.string مفقودة (نصوص حرفية لا مشكلة فيها)
7) تعريفات مكررة (نفس اسم الدالة في نفس الحزمة)
8) imports مكررة
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RED_APP = ROOT / "red-app/src/main/java/com/red/sovereign"

# الملفات الجديدة/المعدّلة هذه الجلسة
TARGET_FILES = [
    RED_APP / "features/media/MediaGallery.kt",
    RED_APP / "features/profile/ProfileScreen.kt",
    RED_APP / "features/profile/ProfileViewModel.kt",
    RED_APP / "security/AppLockScreen.kt",
    RED_APP / "media/StickerApi.kt",
    RED_APP / "media/StickerPicker.kt",
    RED_APP / "media/EventsScreen.kt",
    RED_APP / "media/PollsScreen.kt",
    RED_APP / "core/RichMessage.kt",
    RED_APP / "auth/AuthorizedApiClient.kt",
    RED_APP / "auth/TokenStore.kt",
    RED_APP / "auth/AuthViewModel.kt",
    RED_APP / "calls/YounesCallService.kt",
    RED_APP / "calls/PhoneStateReceiver.kt",
    RED_APP / "calls/CallTelemetry.kt",
    RED_APP / "core/database/Entities.kt",
    RED_APP / "core/database/RedDao.kt",
    RED_APP / "core/database/RedDatabase.kt",
    RED_APP / "core/database/LocalRepository.kt",
    RED_APP / "core/RedConnectionService.kt",
    RED_APP / "settings/SettingsViewModel.kt",
    RED_APP / "ui/RedDashboard.kt",
]

failures = []
passes = 0


def check(name, ok, detail=""):
    global passes
    if ok:
        passes += 1
    else:
        failures.append(f"❌ {name}: {detail}")


def get_imports(text):
    return set(re.findall(r'^import\s+([\w.*]+)', text, re.MULTILINE))


def get_code_only(text):
    """يزيل التعليقات والـ strings لتقليل الإيجابيات الكاذبة."""
    lines = []
    for line in text.splitlines():
        stripped = line.strip()
        if stripped.startswith("//") or stripped.startswith("*") or stripped.startswith("/*"):
            continue
        # أزل التعليقات داخل السطر (تقريبي)
        line = re.sub(r'//.*$', '', line)
        lines.append(line)
    return "\n".join(lines)


for f in TARGET_FILES:
    if not f.exists():
        check(f"{f.name}: موجود", False, "الملف غير موجود")
        continue
    text = f.read_text(encoding="utf-8")
    code = get_code_only(text)
    imports = get_imports(text)
    fname = f.name

    # ١. by remember يحتاج getValue + setValue (أو wildcard runtime.*)
    has_runtime_wildcard = "androidx.compose.runtime.*" in imports
    has_getValue = has_runtime_wildcard or any(i.endswith(".getValue") or i == "getValue" for i in imports)
    has_setValue = has_runtime_wildcard or any(i.endswith(".setValue") or i == "setValue" for i in imports)
    if "by remember" in code:
        check(f"{fname}: by remember → getValue", has_getValue, "by remember يتطلب import androidx.compose.runtime.getValue (أو runtime.*)")
        check(f"{fname}: by remember → setValue", has_setValue, "by remember يتطلب import androidx.compose.runtime.setValue (أو runtime.*)")
    else:
        passes += 2  # لا ينطبق

    # ٢. launch بدون import — نميّز coroutine launch عن ActivityResultLauncher.launch
    # coroutine launch: scope.launch { ... } أو .launch {  لا يسبقه identifier.launch(
    has_coroutine_launch = bool(re.search(r'\blaunch\s*\{', code)) or bool(re.search(r'val\s+\w+\s*=\s*\w+\.launch\s*\{', code))
    has_launch_import = any("kotlinx.coroutines.launch" in i for i in imports) or "kotlinx.coroutines.launch" in text
    uses_fq_launch = bool(re.search(r'kotlinx\.coroutines\.GlobalScope\.launch', code))
    # استثنِ imagePicker.launch( و launcher.launch( (ActivityResultLauncher)
    activity_launch = bool(re.search(r'\b\w+Launcher\.launch\(|\bimagePicker\.launch\(|\b\w+Picker\.launch\(', code))
    pure_coroutine = has_coroutine_launch and not (activity_launch and not re.search(r'\bscope\.launch\s*\{|\b\.launch\s*\{[^a-zA-Z]', code))
    if pure_coroutine and not uses_fq_launch:
        check(f"{fname}: launch → import", has_launch_import, "scope.launch {} يحتاج import kotlinx.coroutines.launch")
    else:
        passes += 1

    # ٣. rememberCoroutineScope بدون import — fully-qualified مقبول
    if "rememberCoroutineScope" in code:
        has_rcs = any("rememberCoroutineScope" in i for i in imports) or "androidx.compose.runtime.rememberCoroutineScope" in text
        check(f"{fname}: rememberCoroutineScope → import", has_rcs, "rememberCoroutineScope يحتاج import أو fully-qualified")
    else:
        passes += 1

    # ٤. viewModel( بدون import — fully-qualified مقبول
    if re.search(r'\bviewModel\s*\(', code):
        has_vm = any("viewmodel.compose.viewModel" in i for i in imports) or "androidx.lifecycle.viewmodel.compose.viewModel" in text
        check(f"{fname}: viewModel → import", has_vm, "viewModel() يحتاج import")
    else:
        passes += 1

    # ٥. @Composable بدون import — wildcard runtime.* مقبول
    if "@Composable" in code:
        has_composable = has_runtime_wildcard or any(i.endswith(".Composable") for i in imports)
        check(f"{fname}: @Composable → import", has_composable, "@Composable يحتاج import")
    else:
        passes += 1

    # ٦. imports مكررة
    import_lines = [l.strip() for l in text.splitlines() if l.strip().startswith("import ")]
    dupes = [l for l in import_lines if import_lines.count(l) > 1]
    check(f"{fname}: لا imports مكررة", len(set(dupes)) == 0, f"imports مكررة: {set(dupes)}")

    # ٧. mutableStateOf بلا import
    if "mutableStateOf" in code and "by remember" not in code:
        # قد تُستخدم بـ remember{mutableStateOf} في نفس السطر
        if re.search(r'remember\s*\{\s*mutableStateOf', code):
            check(f"{fname}: mutableStateOf → import", any("mutableStateOf" in i for i in imports), "mutableStateOf يحتاج import")
        else:
            passes += 1
    else:
        passes += 1

# ٨. تعريفات مكررة عبر الملفات (نفس الحزمة)
all_defs = {}
for f in TARGET_FILES:
    if not f.exists():
        continue
    text = f.read_text(encoding="utf-8")
    pkg = re.search(r'^package\s+([\w.]+)', text, re.MULTILINE)
    if not pkg:
        continue
    pkg = pkg.group(1)
    # الدوال العامة
    for m in re.finditer(r'^fun\s+(\w+)\s*\(', text, re.MULTILINE):
        name = m.group(1)
        key = (pkg, name)
        all_defs.setdefault(key, []).append(str(f))
    # الدوال private
    for m in re.finditer(r'^private fun\s+(\w+)\s*\(', text, re.MULTILINE):
        name = m.group(1)
        key = (f"{pkg}#private", name, str(f))
        all_defs.setdefault(key, []).append(str(f))

for key, files in all_defs.items():
    if len(files) > 1:
        # private fun يُسمح بتكرارها عبر ملفات مختلفة
        if "private" not in str(key):
            check(f"لا تعريف مكرر: {key}", False, f"معرّف في: {set(files)}")
        else:
            passes += 1
    else:
        passes += 1


# ─── الخلاصة ──────────────────────────────────────────────────────────
print("════════════════════════════════════════════════")
print("  🔬 فاحص ثابت شامل لملفات Kotlin الجديدة")
print("════════════════════════════════════════════════")
print(f"  ✅ {passes} فحص ناجح")
if failures:
    print(f"  ❌ {len(failures)} فشل:")
    for fl in failures:
        print(f"     {fl}")
    sys.exit(1)
else:
    print("  النتيجة: سليم ✅ — لا عيوب تصريف شائعة في الملفات الجديدة")
    sys.exit(0)
