#!/usr/bin/env bash
# Fails fast if the production RED identity path regresses to phone/SMS/OTP/email onboarding.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
auth_sources=(
  "$root/red-app/src/main/java/com/red/sovereign/auth"
  "$root/red-app/src/main/java/com/red/sovereign/ui/AuthScreens.kt"
  "$root/backend-server/src/main/kotlin/com/red/server/auth"
)

if [[ -e "$root/red-app/src/main/java/com/red/sovereign/ui/AuthFlow.kt" ]]; then
  echo "Forbidden legacy phone/OTP onboarding file exists: AuthFlow.kt" >&2
  exit 1
fi

# PSTN is intentionally outside these identity/auth sources. Do not scan the whole product: the
# hardware gateway is allowed to handle a phone number only after explicit PSTN authorization.
for source in "${auth_sources[@]}"; do
  if grep -RInE 'OTP|PHONE_INPUT|OtpVerification|PhoneInput|sms verification|email verification|smtp\.gmail' "$source" --include='*.kt' 2>/dev/null; then
    echo "Phone/SMS/email onboarding is forbidden in the RED identity flow" >&2
    exit 1
  fi
done

echo "Sovereign identity boundary check passed."
