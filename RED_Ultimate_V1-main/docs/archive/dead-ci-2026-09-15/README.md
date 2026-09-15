# Dead / inactive CI files (archived 2026-09-15)

These files are NOT executed:

- `RED_Ultimate_V1-main/.github/workflows/*` — GitHub Actions only reads the
  repository-root `.github/workflows/`. Files inside a sub-directory never run.
  The canonical workflow is `/.github/workflows/red-ultimate-ci.yml`.
- `nested.gitlab-ci.yml` — GitLab CI only reads the repository-root `.gitlab-ci.yml`.
  The canonical pipeline is `/.gitlab-ci.yml`.

Kept here for reference only. Do not edit these expecting them to run.