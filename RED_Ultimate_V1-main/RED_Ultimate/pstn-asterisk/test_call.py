import urllib.request
import json
import time

data = json.dumps({'username': 'red_admin', 'password': 'Mn5qWmjYZyNxgXOe', 'clientType': 'ANDROID'}).encode('utf-8')
req = urllib.request.Request('http://red-backend:8080/api/auth/login', data=data, headers={'Content-Type': 'application/json'})
resp = urllib.request.urlopen(req)
token = json.loads(resp.read().decode('utf-8'))['accessToken']

call_data = json.dumps({'number': '780488700', 'gatewayId': '022ce956-8675-3645-95ea-e2077eb24e05', 'portIndex': 7}).encode('utf-8')
req2 = urllib.request.Request('http://red-backend:8080/api/pstn/calls', data=call_data, headers={'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token})
try:
    resp2 = urllib.request.urlopen(req2)
    print('Call initiated:', resp2.read().decode('utf-8'))
except Exception as e:
    print('Call failed:', e.read().decode('utf-8') if hasattr(e, 'read') else e)
