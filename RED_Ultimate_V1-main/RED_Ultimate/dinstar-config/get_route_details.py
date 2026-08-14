import requests
from bs4 import BeautifulSoup
import urllib3
import re
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

def inspect_exact_routes():
    for p in ["enRouteIP2PSTNList.htm", "enRoutePSTN2IPList.htm"]:
        r = session.get(f"{URL_BASE}/{p}")
        print(f"\n==================== {p} ====================")
        scripts = re.findall(r'<script[^>]*>(.*?)</script>', r.text, re.DOTALL)
        for s in scripts:
            for line in s.splitlines():
                if any(k in line for k in ['totalRule', 'totalPort', 'document.write', 'Route', 'add', 'del', 'tr']):
                    print("  ", line.strip())

if __name__ == '__main__':
    if login():
        inspect_exact_routes()
