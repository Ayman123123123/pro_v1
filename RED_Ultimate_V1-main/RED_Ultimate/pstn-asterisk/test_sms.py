import urllib.request
import json
import ssl

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

# 1. Login
data = json.dumps({'username': 'red_admin', 'password': 'Mn5qWmjYZyNxgXOe', 'clientType': 'ANDROID'}).encode('utf-8')
req = urllib.request.Request('http://red-backend:8080/api/auth/login', data=data, headers={'Content-Type': 'application/json'})
token = json.loads(urllib.request.urlopen(req, context=ctx).read().decode('utf-8'))['accessToken']

# 2. Get incoming SMS
req2 = urllib.request.Request('http://red-backend:8080/api/admin/dinstar/sms/incoming', headers={'Authorization': 'Bearer ' + token})
try:
    resp2 = urllib.request.urlopen(req2, context=ctx)
    print('Incoming SMS:', resp2.read().decode('utf-8'))
except Exception as e:
    print('Failed:', e.read().decode('utf-8') if hasattr(e, 'read') else e)
