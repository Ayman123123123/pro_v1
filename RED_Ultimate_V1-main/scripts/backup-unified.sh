#!/usr/bin/env bash
# Backup unified without long-path cache issues — uses git archive
set -e
STAMP=$(date +"%Y%m%d-%H%M%S")
OUT="/tmp/BACKUP-unified-$STAMP.zip"
git archive --format=zip --output="$OUT" HEAD
echo "✅ Backup created: $OUT (git archive, no cache, no long paths)"
ls -lh "$OUT"
