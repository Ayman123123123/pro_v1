#!/usr/bin/env bash
# Push this project to a GitLab repository.
# Usage: bash scripts/push-to-gitlab.sh https://gitlab.com/USERNAME/REPO.git [local-branch]
set -euo pipefail
GITLAB_URL="${1:?Usage: $0 <gitlab-repo-url> [branch]}"
BRANCH="${2:-$(git branch --show-current)}"
if git remote get-url gitlab >/dev/null 2>&1; then
  git remote set-url gitlab "$GITLAB_URL"
  echo "updated existing 'gitlab' remote -> $GITLAB_URL"
else
  git remote add gitlab "$GITLAB_URL"
  echo "added 'gitlab' remote -> $GITLAB_URL"
fi
echo "pushing '$BRANCH' to gitlab:main ..."
git push gitlab "$BRANCH:main"
echo "done. open your GitLab project > Build > Pipelines to watch CI."
