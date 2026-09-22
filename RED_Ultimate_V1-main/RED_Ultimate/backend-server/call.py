import urllib.request
import json

data = json.dumps({'username': 'red_admin', 'password': 'admin12345678', 'clientType': 'ANDROID'}).encode('utf-8')
req = urllib.request.Request('http://localhost:8080/api/auth/login', data=data, headers={'Content-Type': 'application/json'})
token = json.loads(urllib.request.urlopen(req).read().decode('utf-8'))['accessToken']

call_data = json.dumps({'number': '780488700', 'gatewayId': '022ce956-8675-3645-95ea-e2077eb24e05', 'portIndex': 7}).encode('utf-8')
req2 = urllib.request.Request('http://localhost:8080/api/pstn/calls', data=call_data, headers={'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token})
try:
    urllib.request.urlopen(req2)
except Exception as e:
    pass
