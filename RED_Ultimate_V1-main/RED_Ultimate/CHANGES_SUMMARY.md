# RED Sovereign - Configuration Fixes Summary (2026-09-06)

## ✅ COMPLETED FIXES

### 1. Network/IP Configuration (.env)
| Variable | Old Value | New Value | File |
|----------|-----------|-----------|------|
| `CLIENT_LAN_IP` | `192.168.0.175` | `10.38.160.42` | `.env` |
| `TURN_PUBLIC_HOST` | `192.168.0.175` | `10.38.160.42` | `.env` |
| `TLS_SAN_IP` | `192.168.0.175` | `10.38.160.42` | `.env` |
| `ALLOWED_ORIGINS` | `...192.168.0.175...` | `...10.38.160.42...` | `.env` |
| `ASTERISK_WSS_URL` | `ws://192.168.0.175:8088/ws/sip` | `ws://10.38.160.42:8088/ws/sip` | `.env` |
| `MEDIASOUP_ANNOUNCED_IP` | `192.168.0.175` | `10.38.160.42` | `.env` |

### 2. Asterisk PJSIP Configuration (docker-entrypoint.sh)
- ✅ WebRTC endpoint `media_address` now uses `${TURN_PUBLIC_HOST:-0.0.0.0}` (will resolve to 10.38.160.42)
- ✅ ICE support enabled (`ice_support=yes`)
- ✅ SIP Session Timers on all Dinstar endpoints (`timers=yes`, `timers_min_se=90`)
- ✅ Endpoint identifier order: `username,ip,anonymous` (fixes REGISTER matching)

### 3. RTP Port Range Expansion
| File | Old Range | New Range | Max Concurrent Calls |
|------|-----------|-----------|---------------------|
| `pstn-asterisk/rtp.conf` | 10000-10900 (101 ports) | 10000-20000 (10001 ports) | ~5000 |
| `docker-compose.yml` | 10000-10900/udp | 10000-20000/udp | ~5000 |
| `docker-entrypoint.sh` fallback | 10000-10900 | 10000-20000 | ~5000 |

### 4. Android App Build Configuration
| File | Change |
|------|--------|
| `scripts/build-apk.ps1` | Default `ServerUrl` changed from `http://192.168.0.181:8088` → `http://10.38.160.42:8088` |
| `red-app/build.gradle.kts` | Already had correct default: `http://10.38.160.42:8088` |

### 5. Dinstar SIP Port Mapping (Verified Correct)
- `DINSTAR_PORT_SIP_PORTS=5068,5061,5062,5063,5064,5065,5066,5067` in `.env` (Port 0 on 5068 — device reserves 5060 for trunk)
- Entrypoint generates per-port AORs with exact ports from device Port List
- Port 0 → 5060, Port 1 → 5061, ..., Port 7 → 5067
- Verified via live UDP OPTIONS probe (200 OK with correct user per port)

---

## ⚠️ PENDING - REQUIRES USER ACTION

### 1. Docker Desktop Memory (CRITICAL for APK Build)
```powershell
# In Docker Desktop Settings → Resources → Advanced:
# Set Memory to 8GB (currently 3.8GB - causes OOM kills)
# Apply & Restart Docker Desktop
```

### 2. Restart All Containers (After Docker Restart)
```powershell
cd D:\pro_new\pro_new\RED_Ultimate_V1-main\RED_Ultimate
docker compose up -d
```

### 3. Verify Asterisk Configuration (After Restart)
```bash
# Check WebRTC media_address
docker exec red-pstn-gateway asterisk -rx 'pjsip show endpoint red-webrtc-client' | grep media_address
# Should show: media_address: 10.38.160.42

# Check RTP settings
docker exec red-pstn-gateway asterisk -rx 'rtp show settings'
# Should show: Port start: 10000, Port end: 20000

# Check Dinstar endpoints
docker exec red-pstn-gateway asterisk -rx 'pjsip show endpoints' | grep dinstar
# All 8 ports should show "Avail" with RTT ~5ms
```

### 4. Test Two-Way Audio
```bash
# From Asterisk CLI:
docker exec red-pstn-gateway asterisk -rx "channel originate PJSIP/780488700@dinstar-gw-192-168-11-1-port-7 application Echo"

# Enable RTP debug to verify packets flow both ways:
docker exec red-pstn-gateway asterisk -rx "rtp set debug on"
```

### 5. Test Inbound Call
- Port 7 real SIM number: **785681741** (permanent binding red_admin ↔ port 7)
- From test phone (780488700), dial **785681741** → should ring in Android app via WebSocket
- Accept in app → two-way audio

### 6. Android App Configuration
- Install APK: `adb install apk-output/app-debug.apk`
- In app Settings → "Set server manually" → `http://10.38.160.42:8088`
- Login: `red_admin` / `Mn5qWmjYZyNxgXOe`
- Verify PSTN enabled, Port 7 selected

---

## 🔍 KNOWN ISSUES (Hardware/External)

### Port 7 SIM Suspended/Barred
**Symptom**: All outbound calls (voice/USSD) fail with 486 Busy / NO CARRIER / ERROR
**Port 4 USSD works** → Proves SIP/RTP path is functional
**Root Cause**: SIM in Port 7 likely has no balance, expired, or voice service disabled by carrier
**Fix Required**: Physical SIM check/recharge/replacement or move SIM to Port 4 (update `RED_PORT_INDEX=4` in backend)

### Dinstar UI Shows "Unregistered"
**Cause**: Firmware UI lacks `SipIsRegister` field for Port 7
**Impact**: Cosmetic only - SIP signaling works (OPTIONS qualify shows "Avail")
**Workaround**: Use Trunk Gateway Mode (no registration) - already configured

---

## 📋 VERIFICATION CHECKLIST

### After Docker Restart:
- [ ] All 10 containers healthy (`docker ps`)
- [ ] Asterisk WebRTC endpoint shows `media_address: 10.38.160.42`
- [ ] RTP range shows 10000-20000
- [ ] All 8 Dinstar port endpoints show "Avail" with RTT < 10ms
- [ ] TURN server accessible on 10.38.160.42:3478
- [ ] Backend health check passes (`curl http://localhost:8088/health`)

### Audio Tests:
- [ ] Echo test: caller hears own voice back (two-way audio confirmed)
- [ ] RTP debug shows packets both directions (sent=N recv=N, loss=0%)
- [ ] No "NO CARRIER" on Port 4 test call

### App Tests:
- [ ] APK builds successfully with 8GB Docker memory
- [ ] App connects to `http://10.38.160.42:8088`
- [ ] WebSocket connects (incoming call notification works)
- [ ] Outbound call from app → rings external phone
- [ ] Inbound call from external → rings in app
- [ ] DTMF works during active call

### PSTN Features:
- [ ] CDR ingestion works (no SQL errors)
- [ ] Redis lock released on missed calls
- [ ] USSD works on Port 4
- [ ] SMS send/receive works

---

## 🎯 NEXT STEPS PRIORITY

1. **Immediate**: Restart Docker Desktop with 8GB memory
2. **Immediate**: `docker compose up -d` 
3. **Immediate**: Verify Asterisk config with commands above
4. **Test**: Echo test for two-way audio
5. **Test**: Inbound/outbound calls via app
6. **Hardware**: Check/replace Port 7 SIM if outbound still fails

---

## 📁 FILES MODIFIED
- `.env` - 6 IP address changes
- `pstn-asterisk/docker-entrypoint.sh` - RTP fallback, media_address template
- `pstn-asterisk/rtp.conf` - RTP range 10000-20000
- `docker-compose.yml` - RTP ports 10000-20000
- `scripts/build-apk.ps1` - Default server URL
- `red-app/build.gradle.kts` - Verified correct (no change needed)