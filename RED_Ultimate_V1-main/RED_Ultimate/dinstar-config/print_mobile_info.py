import requests
import urllib3
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

if __name__ == '__main__':
    if login():
        for ep in ["enHBSimInfo.htm", "enSysInfo_Mobile.htm", "enSysInfo_SIP.htm"]:
            r = session.get(f"{URL_BASE}/{ep}")
            print(f"=== {ep} ===")
            print(r.text.strip())
