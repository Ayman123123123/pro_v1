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

def check_sim_status_pages():
    pages = [
        "enPortInfo.htm",
        "enPortList.htm",
        "enSysInfo_Mobile.htm",
        "enSysInfo_SIP.htm",
        "enHBSim.htm",
        "enHBPhoneNumber.htm",
        "enGSMEvent.htm",
        "enGsmOperate.htm",
        "enCurrentCallStat.htm",
        "enCallStat.htm",
        "enBCCHStat.htm"
    ]
    for p in pages:
        r = session.get(f"{URL_BASE}/{p}", timeout=5)
        print(f"\n==================== {p} (status: {r.status_code}) ====================")
        if r.status_code == 200:
            soup = BeautifulSoup(r.text, 'html.parser')
            # Extract tables
            for tr in soup.find_all('tr'):
                cells = [td.get_text(strip=True) for td in tr.find_all(['td', 'th'])]
                if cells and any(cells):
                    print("  | " + " | ".join(cells))
            
            # Extract script initialization data (like port status arrays)
            scripts = re.findall(r'<script[^>]*>(.*?)</script>', r.text, re.DOTALL)
            for s in scripts:
                for line in s.splitlines():
                    l = line.strip()
                    if any(k in l.lower() for k in ['var ', 'init', 'port', 'sim', 'signal', 'operator', 'status', 'rssi', 'imsi', 'state', 'carrier']):
                        if len(l) < 200 and not l.startswith('//'):
                            print("    [JS]:", l)

if __name__ == '__main__':
    if login():
        check_sim_status_pages()
