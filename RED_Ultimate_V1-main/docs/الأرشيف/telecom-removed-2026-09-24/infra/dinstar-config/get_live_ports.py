import requests
import urllib3
import json
import sys

if sys.stdout.encoding.lower() != 'utf-8':
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except AttributeError:
        pass

urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

URL_BASE = 'https://192.168.11.1'
USERNAME = 'admin'
PASSWORD = 'admin'

session = requests.Session()
session.verify = False

def login():
    data = {'username': USERNAME, 'password': PASSWORD}
    r = session.post(f"{URL_BASE}/goform/IADIdentityAuth", data=data, allow_redirects=False)
    return 'devckie' in session.cookies or 'JSESSIONID' in session.cookies or r.status_code == 302

def get_live_ports():
    r = session.get(f"{URL_BASE}/WebGetPortInfoAll")
    print(f"Status Code: {r.status_code}")
    print("Raw Response:")
    print(r.text)
    try:
        # Evaluate js array if needed
        text = r.text.strip()
        data = json.loads(text)
        print("\n--- Parsed SIM Port Details ---")
        for item in data:
            print(json.dumps(item, indent=2, ensure_ascii=False))
    except Exception as e:
        print("Json parse error:", e)

if __name__ == '__main__':
    if login():
        get_live_ports()
