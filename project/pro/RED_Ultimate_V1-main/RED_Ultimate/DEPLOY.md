# RED Deployment Guide (Sovereign Edition)

## 1. Prerequisites
- Docker & Docker Compose installed on your local server.
- A physical Dumin GSM device connected to the same network.
- No internet connection required (100% Local).

## 2. Server Launch
Run the following command in the root folder:
```bash
docker-compose up -d --build
```

## 3. Systems Check
- **Backend:** http://localhost:8080/api/admin/monitor
- **Admin Panel:** http://localhost:3000
- **Dumin Monitor:** Check SIM status in the Admin Panel.

## 4. App Distribution
- Build the APK from the `app-android` folder.
- Distribute to your users via the local server's internal link.

## 5. Security
- Access the Admin Panel to APPROVE new users.
- Use the Kill Switch if a device is lost.

**Rights Reserved to RED © 2026**
