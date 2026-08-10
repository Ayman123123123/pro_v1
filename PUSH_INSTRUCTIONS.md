# 🚀 أوامر الرفع من جهازك المحلي

## الخطوة 1: افتح PowerShell في مجلد المشروع

```powershell
cd C:\Users\hpc01\Pictures\pro
```

## الخطوة 2: تأكد من الفرع

```powershell
git status
git branch --show-current
```

يجب أن ترى: `On branch arena/019fe4dd-pro-v1`

## الخطوة 3: اسحب آخر التحديثات

```powershell
git fetch origin
git pull origin arena/019fe4dd-pro-v1
```

## الخطوة 4: تأكد من نظافة الشجرة

```powershell
git status
```

يجب أن ترى: `nothing to commit, working tree clean`

## الخطوة 5: أرسل التحديثات (إذا عندك commit محلية)

```powershell
git push origin arena/019fe4dd-pro-v1
```

## الخطوة 6: أرسل الفرع الموحد

```powershell
git push origin arena/unified-20260809
```

---

## بعد ما تنتهي، أرسل لي:

```powershell
git log --oneline -10
git branch -a
```

وأنا من جهتي أسحب وأدمج.