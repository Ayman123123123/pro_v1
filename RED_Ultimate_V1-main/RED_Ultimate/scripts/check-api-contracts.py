#!/usr/bin/env python3
"""
حارس عقود JSON بين تطبيق الأندرويد وخادم Spring.

لماذا يلزم
──────────
العميل يُسلسل بـkotlinx.serialization، والخادم يفكّ بـJackson. حين
يختلف **اسم** حقل بين الطرفين لا يشتكي أيُّ مترجم: كلٌّ يترجم وحده
بنجاح، والعطب لا يظهر إلا وقت التشغيل. وقد تكرّر هذا ثلاث مرات فعلًا:

  FeedViewModel.createPoll  أرسل `scope`      والخادم يقرأ `visibility`
  CreateStoryRequest        أرسل `visibleTo`  والخادم يقرأ `visibility`
  CreatePostRequest         أرسل `mediaKeys`  والخادم يقرأ `media`

والأثر ليس عرضيًّا. `FAIL_ON_UNKNOWN_PROPERTIES` مفعَّل افتراضيًّا في
Jackson ولم يُعطَّل في `JacksonConfig`، فالحقل المجهول يُسقط الطلب
كلَّه بـ400. ولو عُطّل يومًا لصار الأسوأ: يُهمَل الحقل صامتًا فتأخذ
القصّة خصوصيةً أوسع ممّا اختار صاحبها.

ما يفحصه
────────
لكل `data class` بالاسم نفسه على الطرفين: هل ثمّة حقل موجود **في
العميل فقط**؟ ذلك هو الاتجاه الخطر، لأنه ما يُرسَل فيُرفض.

حقلٌ في الخادم فقط ليس خطأً: العميل يتجاهل ما لا يعرف
(`ignoreUnknownKeys = true`)، وقد يكون الحقل جديدًا أو لا يهمّ العميل.

الاستثناءات مُعلَّلة أدناه واحدًا واحدًا؛ وإضافة استثناء جديد تتطلّب
سببًا مكتوبًا لا مجرّد إسكات.

الاستعمال: python3 scripts/check-api-contracts.py   (من RED_Ultimate/)
"""
import os
import re
import sys

CLIENT_ROOT = "red-app/src/main"
SERVER_ROOT = "backend-server/src/main"

# ── استثناءات مُعلَّلة ────────────────────────────────────────────────
# المفتاح: اسم الصنف. القيمة: (الحقول المسموح انفرادُ العميل بها، السبب)
ALLOWED = {
    "PostMedia": (
        {"durationMs", "voiceWaveform"},
        "زائف: في الخادم تعريفان بالاسم نفسه، والماسح يلتقط الأقدم في "
        "SovereignMongoDocuments.kt. المستعمل فعلًا هو "
        "social/PostModels.kt وفيه الحقلان — و FeedService في الحزمة "
        "نفسها فيحلّهما إليه.",
    ),
    "StoryView": (
        {"reaction", "viewerRedId"},
        "نموذجان مختلفان لا عقد واحد: صنف العميل غير مستعمل في أي طلب "
        "(الاطّلاع يُسجَّل بـPOST /{id}/view بلا جسم)، وصنف الخادم "
        "وثيقة Mongo داخلية.",
    ),
    "CallEnded": (
        {"mode", "peer"},
        "إشارة WebSocket لا جسم REST؛ تُبنى وتُقرأ في CallSignal على "
        "الطرفين بمخطَّط مستقلّ عن Jackson.",
    ),
}


def parse_data_classes(root):
    """اسم الصنف ⇒ (أسماء حقول JSON، المسار). يحترم @SerialName."""
    found = {}
    for dirpath, _, files in os.walk(root):
        if "/build/" in dirpath or "_archive" in dirpath:
            continue
        for name in files:
            if not name.endswith(".kt"):
                continue
            path = os.path.join(dirpath, name)
            with open(path, encoding="utf-8", errors="ignore") as handle:
                src = handle.read()
            for match in re.finditer(r"data class (\w+)\s*\(", src):
                cls = match.group(1)
                start = match.end() - 1
                depth = 0
                end = start
                for i in range(start, len(src)):
                    if src[i] == "(":
                        depth += 1
                    elif src[i] == ")":
                        depth -= 1
                        if depth == 0:
                            end = i
                            break
                body = src[start + 1:end]
                body = re.sub(r"//[^\n]*", "", body)
                body = re.sub(r"/\*.*?\*/", "", body, flags=re.S)
                fields = set(re.findall(r"\bva[lr]\s+(\w+)\s*:", body))
                for serial, field in re.findall(
                    r'@SerialName\("([^"]+)"\)\s*va[lr]\s+(\w+)', body
                ):
                    fields.discard(field)
                    fields.add(serial)
                if fields:
                    found.setdefault(cls, (fields, path))
    return found


def main():
    if not os.path.isdir(CLIENT_ROOT) or not os.path.isdir(SERVER_ROOT):
        print("❌ شغّل الأداة من جذر RED_Ultimate/", file=sys.stderr)
        return 2

    client = parse_data_classes(CLIENT_ROOT)
    server = parse_data_classes(SERVER_ROOT)
    shared = sorted(set(client) & set(server))

    problems = []
    for cls in shared:
        client_fields, client_path = client[cls]
        server_fields, _ = server[cls]
        only_client = client_fields - server_fields
        if not only_client:
            continue
        allowed, _reason = ALLOWED.get(cls, (set(), ""))
        unexplained = only_client - allowed
        if unexplained:
            problems.append((cls, sorted(unexplained), client_path))

    print(f"فُحص {len(shared)} صنفًا مشتركًا بين العميل والخادم.")
    if not problems:
        print("\n  ✅ لا حقل يرسله العميل ويجهله الخادم.")
        print("\n⚠️  الفحص بالاسم فقط: لا يقارن الأنواع ولا يرصد اختلاف")
        print("    القيم الافتراضية. راجع العقد يدويًّا عند تغييره.")
        return 0

    print(f"\n❌ {len(problems)} صنفًا يرسل حقلًا يجهله الخادم:\n")
    for cls, fields, path in problems:
        print(f"  ── {cls}")
        print(f"     الحقول : {fields}")
        print(f"     الملف  : {path}")
        print("     الأثر  : الخادم يردّ 400 (FAIL_ON_UNKNOWN_PROPERTIES)")
        print()
    print("صحّح الاسم ليطابق الخادم، أو أضف استثناءً مُعلَّلًا في ALLOWED.")
    return 1


if __name__ == "__main__":
    sys.exit(main())
