# Push Instructions (current repo)

## Repository

- Root: `C:\Users\hpc01\Pictures\pro_new`
- Remote: `origin` -> `https://github.com/Ayman123123123/pro_v1.git`
- Branch: `main` (previously `master`)

## Routine push

```powershell
cd C:\Users\hpc01\Pictures\pro_new
git status
git add -A
git commit -m "descriptive message"
git push origin main
```

## Notes

- `.gradle_home/`, `.android_home/`, `app.jar`, `upload-clean/`, `backend-server/`
  (root husk), `cookies.txt` and `dinstar_cookies.txt` are intentionally
  untracked (see root `.gitignore`) — do not `git add -f` them.
- `scripts/merge-local-copies.ps1` and `scripts/BACKUP_EVERYTHING.ps1` will NOT
  push unless you pass `-Push` explicitly (safety gate added 2026-08-18).
- The canonical CI workflow is `.github/workflows/quality-gate.yml`.
