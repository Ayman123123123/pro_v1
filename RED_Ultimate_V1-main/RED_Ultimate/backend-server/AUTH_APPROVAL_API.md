# RED registration, device identity and administrator approval

RED accounts never require a phone number, SIM card, OTP, or SMS. Private identity keys are generated and retained by the client; the API accepts public key material only.

## Local identity authority

Before first startup:

```bash
cd RED_Ultimate
./scripts/generate-local-identity-authority.sh
cp .env.example .env
# Replace every placeholder in .env
```

The generated ECDSA P-256/SHA-256 private key is mounted read-only into the backend and is ignored by Git. Back it up securely. In production it should move to an HSM/PKCS#11 provider.

## 1. Register an account and its first device

`POST /api/auth/register`

```json
{
  "username": "ahmed.red",
  "password": "a-long-private-password",
  "displayName": "أحمد",
  "device": {
    "deviceName": "Ahmed Pixel",
    "platform": "ANDROID",
    "registrationId": 1234,
    "protocolDeviceId": 1,
    "signedPreKeyId": 42,
    "kyberPreKeyId": 43,
    "identityKey": "BASE64_PUBLIC_IDENTITY_KEY",
    "signedPreKey": "BASE64_PUBLIC_SIGNED_PRE_KEY",
    "kyberPreKey": "BASE64_PUBLIC_KYBER_PRE_KEY",
    "signedPreKeySignature": "BASE64_SIGNATURE",
    "kyberPreKeySignature": "BASE64_SIGNATURE"
  }
}
```

HTTP `201` returns a generated five-digit YOUNES ID such as `10001`, the device ID, `PENDING`, and ten one-time recovery codes. No access or refresh token is issued. Recovery codes are shown once; the server stores only Argon2id hashes.

## 2. Login while pending

`POST /api/auth/login`

```json
{
  "username": "ahmed.red",
  "password": "a-long-private-password",
  "deviceId": "DEVICE-UUID-FROM-REGISTRATION"
}
```

A pending account receives HTTP `423`. Rejected, suspended and banned accounts receive HTTP `403`.

## 3. Administrator approval

Log in with the bootstrap administrator and use its Bearer token.

- `GET /api/admin/users/pending`
- `POST /api/admin/users/action`

```json
{
  "userId": "ACCOUNT-UUID-FROM-PENDING-LIST",
  "action": "APPROVED",
  "reason": null
}
```

Approval atomically:

1. approves the account;
2. signs every pending device fingerprint with the RED ECDSA P-256/SHA-256 identity authority;
3. stores approval administrator and timestamp;
4. publishes approved public pre-key bundles.

Supported actions: `APPROVED`, `REJECTED`, `SUSPENDED`, `BANNED`.

## 4. Login and token rotation

After approval, login returns a short-lived access JWT and a single-use refresh token. User accounts must provide an approved `deviceId`; the bootstrap browser administrator may log in without one.

Rotate tokens with `POST /api/auth/refresh`:

```json
{ "refreshToken": "..." }
```

Every refresh rotates the token. Reusing an already rotated token revokes all active refresh sessions for the account.

Revoke the current refresh token with `POST /api/auth/logout`:

```json
{ "refreshToken": "..." }
```

## Password recovery without phone or email

`POST /api/auth/recover`

```json
{
  "redId": "10001",
  "recoveryCode": "ABCD-EFGH-JKLM",
  "newPassword": "a-new-long-password"
}
```

A successful use consumes that code, changes the Argon2id password hash and revokes every refresh session. Registration, login and recovery endpoints are rate-limited in Redis.

## Identity directory and device controls

- `GET /api/identity/authority`: public ECDSA P-256/SHA-256 authority key.
- `GET /api/identity/directory/{redId}`: approved public identity/pre-key bundles and device certificates; authentication required.
- `GET /api/devices`: current account devices.
- `DELETE /api/devices/{deviceId}`: revoke a device and all of its refresh sessions.

The authority certificate proves that RED administration approved the binding. Clients must still implement safety numbers, key-change warnings and eventually key transparency.

## WebSocket endpoints

- `/ws/master`: the only binary chat protocol, using `RedProtos.RedRED`.
- `/ws/calls`: JSON WebRTC signaling only.
- `/ws/admin/logs`: administrator-only monitoring stream.

All WebSocket upgrades require an approved account and approved device access token in `Authorization: Bearer <token>`, except the bootstrap administrator, whose dashboard token has no device claim.
