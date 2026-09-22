import requests
import urllib3
import re

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
        for p in ["enRouteIP2PSTNList.htm", "enRoutePSTN2IPList.htm"]:
            r = session.get(f"{URL_BASE}/{p}")
            matches = re.findall(r'url\s*=\s*[\'"]([^\'"]+)[\'"]', r.text)
            print(f"URLs in {p}: {matches}")
            for u in set(matches):
                if not u.endswith('.htm'):
                    res = session.get(f"{URL_BASE}/{u}")
                    print(f"  Response for {u}: {res.status_code} -> {res.text[:300]}")
