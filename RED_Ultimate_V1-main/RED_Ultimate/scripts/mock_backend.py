#!/usr/bin/env python3
"""
🚀 RED Ultimate — Sovereign Local Mock Backend API Server
Provides live endpoints for DINSTAR UC2000-VE-8G, PSTN, VoIP, Diagnostics, and Admin Dashboard.
Listens on 127.0.0.1:8080.
"""

import json
import time
from http.server import HTTPServer, BaseHTTPRequestHandler
import re
from urllib.parse import urlparse, parse_qs

# Correct Yemen mobile operators per ITU E.164 & Wikipedia
YEMEN_OPERATORS = [
    {"index": 0, "radioType": "GSM", "status": "REGISTERED", "callState": "IDLE", "signal": 84, "signalRaw": 26, "gprs": "ATTACH", "numberMasked": "••••7101", "operator": "Sabafon", "imsiMasked": "••••4210", "iccidMasked": "••••8901"},
    {"index": 1, "radioType": "GSM", "status": "REGISTERED", "callState": "IDLE", "signal": 90, "signalRaw": 28, "gprs": "ATTACH", "numberMasked": "••••7102", "operator": "Sabafon", "imsiMasked": "••••4211", "iccidMasked": "••••8902"},
    {"index": 2, "radioType": "GSM", "status": "REGISTERED", "callState": "IDLE", "signal": 77, "signalRaw": 24, "gprs": "ATTACH", "numberMasked": "••••7301", "operator": "YOU", "imsiMasked": "••••4220", "iccidMasked": "••••8903"},
    {"index": 3, "radioType": "GSM", "status": "REGISTERED", "callState": "IDLE", "signal": 71, "signalRaw": 22, "gprs": "ATTACH", "numberMasked": "••••7302", "operator": "YOU", "imsiMasked": "••••4221", "iccidMasked": "••••8904"},
    {"index": 4, "radioType": "GSM", "status": "REGISTERED", "callState": "ACTIVE", "signal": 97, "signalRaw": 30, "gprs": "ATTACH", "numberMasked": "••••7701", "operator": "Yemen Mobile", "imsiMasked": "••••4230", "iccidMasked": "••••8905"},
    {"index": 5, "radioType": "GSM", "status": "REGISTERED", "callState": "IDLE", "signal": 94, "signalRaw": 29, "gprs": "ATTACH", "numberMasked": "••••7801", "operator": "Yemen Mobile", "imsiMasked": "••••4231", "iccidMasked": "••••8906"},
    {"index": 6, "radioType": "GSM", "status": "REGISTERED", "callState": "IDLE", "signal": 65, "signalRaw": 20, "gprs": "ATTACH", "numberMasked": "••••7001", "operator": "Y Telecom", "imsiMasked": "••••4240", "iccidMasked": "••••8907"},
    {"index": 7, "radioType": "GSM", "status": "REGISTERED", "callState": "IDLE", "signal": 81, "signalRaw": 25, "gprs": "ATTACH", "numberMasked": "••••1001", "operator": "Yemen 4G", "imsiMasked": "••••4250", "iccidMasked": "••••8908"},
]

CDR_RECORDS = [
    {"port": 4, "direction": "outgoing", "source_number": "admin", "destination_number": "777123456", "start_date": "2026-08-08 14:20:10", "duration": "03:42", "billsec": 222, "operator": "Yemen Mobile", "disposition": "COMPLETED", "signal": 97},
    {"port": 0, "direction": "outgoing", "source_number": "user_2", "destination_number": "711987654", "start_date": "2026-08-08 13:45:00", "duration": "01:15", "billsec": 75, "operator": "Sabafon", "disposition": "COMPLETED", "signal": 84},
    {"port": 2, "direction": "outgoing", "source_number": "user_5", "destination_number": "733456789", "start_date": "2026-08-08 12:10:33", "duration": "05:12", "billsec": 312, "operator": "YOU", "disposition": "COMPLETED", "signal": 77},
    {"port": 6, "direction": "outgoing", "source_number": "user_1", "destination_number": "700112233", "start_date": "2026-08-08 11:30:20", "duration": "02:04", "billsec": 124, "operator": "Y Telecom", "disposition": "COMPLETED", "signal": 65},
    {"port": 7, "direction": "outgoing", "source_number": "user_8", "destination_number": "100223344", "start_date": "2026-08-08 10:05:15", "duration": "04:50", "billsec": 290, "operator": "Yemen 4G", "disposition": "COMPLETED", "signal": 81},
]

class SovereignApiHandler(BaseHTTPRequestHandler):
    def _set_headers(self, status=200, content_type="application/json"):
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Authorization, Content-Type, X-Requested-With, X-Device-Id")
        self.end_headers()

    def do_OPTIONS(self):
        self._set_headers(204)

    def do_GET(self):
        parsed = urlparse(self.path)
        path = parsed.path

        if path in ["/health", "/actuator/health"]:
            self._set_headers(200)
            self.wfile.write(json.dumps({
                "status": "UP",
                "components": {
                    "dinstarGateway": {"status": "UP", "ip": "192.168.11.1", "model": "UC2000-VE-8G", "registeredSlots": 8},
                    "asteriskPstn": {"status": "UP", "route": "PJSIP_DINSTAR_TRUNK"},
                    "db": {"status": "UP"},
                    "redis": {"status": "UP"},
                    "sovereignVoip": {"status": "UP", "iceServers": 3}
                }
            }).encode())
            return

        if path == "/api/admin/dinstar/discover":
            self._set_headers(200)
            self.wfile.write(json.dumps({
                "success": True,
                "gatewayIp": "192.168.11.1",
                "model": "UC2000-VE-8G",
                "status": "ONLINE",
                "portsDetected": 8,
                "message": "Connected via HTTP Digest Auth"
            }).encode())
            return


        if path == "/api/admin/dinstar/inventory":
            self._set_headers(200)
            gateways = [{
                "gatewayId": "gateway_uuid_001",
                "name": "UC2000-VE-8G Main",
                "model": "UC2000-VE-8G",
                "ip": "192.168.11.1",
                "ports": [
                    {"portIndex": p["index"], "operator": p["operator"], "status": p["status"], "signal": p["signal"], "numberMasked": p["numberMasked"], "note": "mock inventory"}
                    for p in YEMEN_OPERATORS
                ]
            }]
            self.wfile.write(json.dumps(gateways).encode())
            return

        if path == "/api/admin/dinstar/status":
            self._set_headers(200)
            self.wfile.write(json.dumps(YEMEN_OPERATORS).encode())
            return

        if path == "/api/admin/dinstar/capabilities":
            self._set_headers(200)
            self.wfile.write(json.dumps({
                "voiceViaAsterisk": True,
                "portInfo": True,
                "moduleReset": True,
                "sms": True,
                "ussd": True,
                "cdr": True,
                "callForward": True,
                "portPower": True,
                "firmwareUpgradeViaUi": True,
                "configBackupViaUi": True
            }).encode())
            return

        if path == "/api/admin/dinstar/cdr":
            self._set_headers(200)
            self.wfile.write(json.dumps({"cdr": CDR_RECORDS, "count": len(CDR_RECORDS)}).encode())
            return

        if path.startswith("/api/admin/dinstar/ports/"):
            port_match = re.match(r"^/api/admin/dinstar/ports/(\d+)(?:/ussd)?$", path)
            if port_match:
                port_idx = int(port_match.group(1))
                if 0 <= port_idx < len(YEMEN_OPERATORS):
                    self._set_headers(200)
                    self.wfile.write(json.dumps({"port": port_idx, "status": YEMEN_OPERATORS[port_idx]}).encode())
                    return

        if path == "/api/admin/dinstar/sms/incoming":
            self._set_headers(200)
            self.wfile.write(json.dumps({
                "sms": [
                    {"incoming_sms_id": 101, "port": 0, "number": "711223344", "smsc": "+96771100000", "timestamp": "2026-08-08 14:15:00", "text": "تم شحن الرصيد بنجاح بمبلغ 2000 ريال يمني."},
                    {"incoming_sms_id": 102, "port": 4, "number": "777889900", "smsc": "+96777700000", "timestamp": "2026-08-08 13:20:00", "text": "رصيدك الحالي هو 4,500 ريال وصالح حتى 2026/12/31"}
                ]
            }).encode())
            return

        if path == "/api/admin/dinstar/sms/queue":
            self._set_headers(200)
            self.wfile.write(json.dumps({"count": 0}).encode())
            return

        if path == "/api/admin/dinstar/device-status":
            self._set_headers(200)
            self.wfile.write(json.dumps({
                "model": "UC2000-VE-8G",
                "ip": "192.168.11.1",
                "firmware": "04240302 (B4.11.19.14L2)",
                "hardwareId": "7036-cf4b-3125",
                "serial": "dd45-1014-8440-0030",
                "uptime": "14 days, 6 hours",
                "cpuUsage": "12%",
                "ramUsage": "34%",
                "gsmPorts": 8,
                "registeredPorts": 8
            }).encode())
            return


        # Admin dashboard unified mock data (development only)
        if path == "/api/admin/dashboard/summary":
            self._set_headers(200)
            self.wfile.write(json.dumps({
                "analytics": {"totalUsers": 128, "approvedUsers": 116, "pendingUsers": 3, "bannedUsers": 2, "newUsers24h": 9, "approvalRate": 91.4},
                "pendingReports": 4,
                "recentCriticalAlerts": 0,
                "degradedComponents": 0,
                "activeBackups": 0,
                "generatedAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
            }).encode())
            return

        if path == "/api/admin/health":
            self._set_headers(200)
            self.wfile.write(json.dumps([
                {"id": "backend", "component": "backend", "status": "HEALTHY", "cpuUsage": 18.0, "memoryUsage": 42.0, "activeConnections": 24, "averageResponseMs": 35, "errorRate": 0.01, "lastCheckAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())},
                {"id": "postgres", "component": "postgres", "status": "HEALTHY", "cpuUsage": 8.0, "memoryUsage": 31.0, "activeConnections": 12, "averageResponseMs": 9, "errorRate": 0.0, "lastCheckAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())},
                {"id": "sfu", "component": "media-sfu", "status": "HEALTHY", "cpuUsage": 11.0, "memoryUsage": 28.0, "activeConnections": 5, "averageResponseMs": 22, "errorRate": 0.0, "lastCheckAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())}
            ]).encode())
            return

        if path == "/api/admin/metrics/realtime":
            self._set_headers(200)
            self.wfile.write(json.dumps({
                "users": {"online": 42, "approved": 116, "pending": 3},
                "health": {},
                "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
            }).encode())
            return

        if path == "/api/admin/analytics":
            self._set_headers(200)
            rows = []
            for i in range(7):
                rows.append({
                    "statDate": f"2026-08-{4+i:02d}", "totalUsers": 100 + i * 4, "newUsers": 3 + i,
                    "activeUsersDau": 40 + i * 2, "messagesSent": 900 + i * 120,
                    "voiceMessages": 80 + i * 9, "callsTotal": 30 + i * 4, "callsPstn": 8 + i,
                    "dinstarBalanceRemaining": 120000 - i * 2500, "storageUsedBytes": 2400000000 + i * 110000000
                })
            self.wfile.write(json.dumps(rows).encode())
            return

        if path == "/api/admin/users":
            self._set_headers(200)
            users = [
                {"id":"user_uuid_001","redId":"YNS-0001","username":"admin","displayName":"مسؤول يونس","status":"APPROVED","role":"ADMIN","pstnEnabled":True,"pstnDailyLimit":100,"createdAt":"2026-08-01T10:00:00Z"},
                {"id":"user_uuid_002","redId":"YNS-0002","username":"ahmed","displayName":"أحمد","status":"PENDING","role":"USER","pstnEnabled":False,"pstnDailyLimit":0,"createdAt":"2026-08-09T10:00:00Z"},
                {"id":"user_uuid_003","redId":"YNS-0003","username":"ali","displayName":"علي","status":"APPROVED","role":"USER","pstnEnabled":True,"pstnDailyLimit":10,"createdAt":"2026-08-08T10:00:00Z"}
            ]
            self.wfile.write(json.dumps({"content": users, "page":0, "size":20, "totalElements":len(users), "totalPages":1}).encode())
            return

        if path == "/api/admin/users/pending":
            self._set_headers(200)
            self.wfile.write(json.dumps([{"id":"user_uuid_002","redId":"YNS-0002","username":"ahmed","displayName":"أحمد","createdAt":"2026-08-09T10:00:00Z","devices":[]}]).encode())
            return

        m_user_overview = re.match(r"^/api/admin/users/([^/]+)/overview$", path)
        if m_user_overview:
            self._set_headers(200)
            uid = m_user_overview.group(1)
            self.wfile.write(json.dumps({"user":{"id":uid,"redId":"YNS-0001","username":"admin","displayName":"مسؤول يونس"},"online":True,"messagesSent":1200,"messagesReceived":1180,"messages24h":87,"callsMade":14,"callsReceived":9,"redCalls":17,"pstnCalls":6,"passwordResetRequired":False,"remoteWipeStatus":"NONE","managedDeviceWipeAllowed":True,"securityEvents":[]}).encode())
            return

        if path.startswith("/api/admin/audit"):
            self._set_headers(200)
            self.wfile.write(json.dumps({"content":[{"id":"audit_1","action":"ADMIN_LOGIN","actorId":"admin_uuid_001","targetId":"dashboard","createdAt":time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())}],"page":0,"size":20,"totalElements":1,"totalPages":1}).encode())
            return

        if path.startswith("/api/admin/reports") or path.startswith("/api/admin/security/alerts"):
            self._set_headers(200)
            self.wfile.write(json.dumps({"content":[],"page":0,"size":20,"totalElements":0,"totalPages":0}).encode())
            return

        if path.startswith("/api/admin/announcements") or path.startswith("/api/admin/backups") or path.startswith("/api/admin/feature-flags"):
            self._set_headers(200)
            self.wfile.write(json.dumps([]).encode())
            return

        if path.startswith("/api/admin/content/"):
            self._set_headers(200)
            self.wfile.write(json.dumps({"content":[],"page":0,"size":20,"totalElements":0,"totalPages":0}).encode())
            return

        if path.startswith("/api/notifications"):
            self._set_headers(200)
            if path.endswith("/unread-count"):
                self.wfile.write(json.dumps({"count": 0}).encode())
            else:
                self.wfile.write(json.dumps({"content":[],"page":0,"size":50,"totalElements":0,"totalPages":0}).encode())
            return

        if path.startswith("/api/social/"):
            self._set_headers(200)
            self.wfile.write(json.dumps({}).encode())
            return

        if path == "/api/master/v1/stats/realtime":
            self._set_headers(200)
            self.wfile.write(json.dumps({"active_users":42,"total_messages":12480,"messages_24h":1248,"pending_approvals":3,"system_load":"12%","db_health":"UP","gsm_signal":"STABLE","db_storage":"4.2 GB"}).encode())
            return

        if path == "/api/master/v1/media/active-calls":
            self._set_headers(200)
            self.wfile.write(json.dumps({"activeCalls":1,"rooms":2,"peers":5}).encode())
            return

        if path == "/api/master/v1/hardware/dinstar/slots":
            self._set_headers(200)
            self.wfile.write(json.dumps(YEMEN_OPERATORS).encode())
            return

        # Overview & Master Diagnostics
        if path in ["/api/master/overview", "/api/admin/overview"]:
            self._set_headers(200)
            self.wfile.write(json.dumps({
                "activeCalls": 1,
                "totalCallsToday": 48,
                "registeredUsers": 128,
                "activeDevices": 4,
                "dinstarGateway": {"status": "ONLINE", "ip": "192.168.11.1", "avgSignal": 82, "ports": 8},
                "serverUptime": "99.98%",
                "networkThroughput": "14.2 Mbps"
            }).encode())
            return

        # Fallback 404
        self._set_headers(404)
        self.wfile.write(json.dumps({"error": "NOT_FOUND", "path": path}).encode())

    def do_POST(self):
        parsed = urlparse(self.path)
        path = parsed.path
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length) if length > 0 else b"{}"
        data = json.loads(body.decode("utf-8")) if body else {}

        if path == "/api/auth/login":
            username = data.get("username", "admin")
            self._set_headers(200)
            self.wfile.write(json.dumps({
                "accessToken": "younes_mock_jwt_access_token_2026",
                "refreshToken": "younes_mock_jwt_refresh_token_2026",
                "user": {
                    "id": "admin_uuid_001",
                    "username": username,
                    "displayName": "مسؤول يونس السيادي",
                    "role": "ADMIN"
                }
            }).encode())
            return

        if path == "/api/auth/refresh":
            self._set_headers(200)
            self.wfile.write(json.dumps({
                "accessToken": "younes_mock_jwt_access_token_2026_refreshed",
                "refreshToken": "younes_mock_jwt_refresh_token_2026"
            }).encode())
            return

        if path == "/api/auth/logout":
            self._set_headers(200)
            self.wfile.write(json.dumps({"status": "LOGGED_OUT"}).encode())
            return

        if path.startswith("/api/admin/dinstar/ports/"):
            # Reset port
            reset_match = re.match(r"^/api/admin/dinstar/ports/(\d+)/reset$", path)
            if reset_match:
                p = int(reset_match.group(1))
                self._set_headers(200)
                self.wfile.write(json.dumps({"status": "SUCCEEDED", "port": p, "message": f"Module {p+1} reset triggered"}).encode())
                return

            # USSD
            ussd_match = re.match(r"^/api/admin/dinstar/ports/(\d+)/ussd$", path)
            if ussd_match:
                p = int(ussd_match.group(1))
                code = data.get("code", "*101#")
                self._set_headers(200)
                self.wfile.write(json.dumps({
                    "status": "SUCCEEDED",
                    "port": p,
                    "code": code,
                    "reply": f"رصيدك الحالي: 5,420 ريال يمني. باقة 4G صالحة حتى 2026/09/15"
                }).encode())
                return

            # CallForward
            cf_match = re.match(r"^/api/admin/dinstar/ports/(\d+)/callforward$", path)
            if cf_match:
                p = int(cf_match.group(1))
                self._set_headers(200)
                self.wfile.write(json.dumps({"status": "SUCCEEDED", "port": p, "param": data.get("param"), "number": data.get("number")}).encode())
                return

            # Power
            power_match = re.match(r"^/api/admin/dinstar/ports/(\d+)/power$", path)
            if power_match:
                p = int(power_match.group(1))
                self._set_headers(200)
                self.wfile.write(json.dumps({"status": "SUCCEEDED", "port": p, "power": data.get("power", "on")}).encode())
                return

        if path == "/api/admin/dinstar/sms/send":
            self._set_headers(200)
            self.wfile.write(json.dumps({
                "status": "SUCCEEDED",
                "task_id": 401,
                "sms_in_queue": 0,
                "message": f"SMS sent successfully to {len(data.get('param', []))} recipient(s)"
            }).encode())
            return

        if path == "/api/admin/dinstar/sms/result":
            self._set_headers(200)
            self.wfile.write(json.dumps({
                "result": [
                    {"port": 0, "user_id": 1, "number": "777123456", "time": "2026-08-08 14:30:00", "status": "DELIVERED", "count": 1, "succ_count": 1, "ref_id": 8801}
                ]
            }).encode())
            return


        if re.match(r"^/api/admin/users/[^/]+/temporary-password$", path):
            self._set_headers(204)
            return

        if re.match(r"^/api/admin/users/[^/]+/remote-app-wipe$", path):
            self._set_headers(202)
            self.wfile.write(json.dumps({"commandId": f"wipe_{int(time.time())}", "status": "QUEUED"}).encode())
            return

        if path in ["/api/admin/security/wipe", "/api/admin/security/kill-switch"]:
            self._set_headers(200)
            self.wfile.write(json.dumps({"status": "SENT", "commandId": f"security_{int(time.time())}"}).encode())
            return

        if path == "/api/pstn/calls":
            num = data.get("number", "777123456")
            self._set_headers(200)
            self.wfile.write(json.dumps({
                "callId": f"call_{int(time.time())}",
                "status": "DIALING",
                "number": num,
                "usedToday": 1,
                "dailyLimit": 100,
                "slot": 4
            }).encode())
            return

        self._set_headers(200)
        self.wfile.write(json.dumps({"status": "OK", "path": path}).encode())


    def do_PUT(self):
        path = urlparse(self.path).path
        m = re.match(r"^/api/admin/dinstar/inventory/([^/]+)/ports/(\d+)$", path)
        if m:
            idx = int(m.group(2))
            port = dict(YEMEN_OPERATORS[idx]) if 0 <= idx < len(YEMEN_OPERATORS) else {"index": idx}
            port["portIndex"] = idx
            port["updated"] = True
            self._set_headers(200)
            self.wfile.write(json.dumps(port).encode())
            return
        self._set_headers(200)
        self.wfile.write(json.dumps({"status": "OK", "path": path}).encode())

    def do_PATCH(self):
        self._set_headers(200)
        self.wfile.write(json.dumps({"status": "OK", "path": urlparse(self.path).path}).encode())

    def do_DELETE(self):
        self._set_headers(200)
        self.wfile.write(json.dumps({"status": "OK", "path": urlparse(self.path).path}).encode())

    def log_message(self, format, *args):
        pass  # Quiet logging

def run_server(port=8080):
    server_address = ("0.0.0.0", port)
    httpd = HTTPServer(server_address, SovereignApiHandler)
    print(f"🚀 Sovereign Mock API server running on http://0.0.0.0:{port}")
    httpd.serve_forever()

if __name__ == "__main__":
    run_server()
