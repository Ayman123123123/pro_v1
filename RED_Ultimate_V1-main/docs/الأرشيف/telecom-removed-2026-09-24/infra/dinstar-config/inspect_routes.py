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

def inspect_routes():
    for p in ["enRouteIP2PSTNList.htm", "enRoutePSTN2IPList.htm", "enSIPCfg.htm"]:
        r = session.get(f"{URL_BASE}/{p}")
        print(f"\n==================== Page: {p} (status: {r.status_code}) ====================")
        soup = BeautifulSoup(r.text, 'html.parser')
        for tr in soup.find_all('tr'):
            cells = [td.get_text(strip=True) for td in tr.find_all(['td', 'th'])]
            if cells and any(cells):
                print("  | " + " | ".join(cells))
        scripts = re.findall(r'<script[^>]*>(.*?)</script>', r.text, re.DOTALL)
        for s in scripts:
            lines = [l.strip() for l in s.splitlines() if 'document.write' in l or 'totalRule' in l or 'Route' in l]
            for l in lines:
                print("    [JS]:", l)

if __name__ == '__main__':
    if login():
        inspect_routes()
