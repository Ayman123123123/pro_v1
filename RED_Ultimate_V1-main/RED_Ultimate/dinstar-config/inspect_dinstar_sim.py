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
    if 'devckie' in session.cookies or 'JSESSIONID' in session.cookies or r.status_code == 302:
        print("[✓] Logged in to DINSTAR successfully.")
        return True
    print("[✗] Login failed.")
    return False

def parse_table(html):
    soup = BeautifulSoup(html, 'html.parser')
    rows = []
    for tr in soup.find_all('tr'):
        cells = [td.get_text(strip=True) for td in tr.find_all(['td', 'th'])]
        if cells and any(cells):
            rows.append(cells)
    return rows

def inspect_sim_and_wireless():
    pages = [
        ("enWIAPortStatNew.htm", "📱 WIRELESS PORT & SIM STATUS"),
        ("enSysInfo_Mobile.htm", "📶 MOBILE SYSTEM INFO"),
        ("enSysInfo_SIP.htm", "📞 SIP SYSTEM INFO"),
        ("enRouteIP2PSTNList.htm", "➡️ ROUTING IP -> PSTN (OUTBOUND)"),
        ("enRoutePSTN2IPList.htm", "⬅️ ROUTING PSTN -> IP (INBOUND)"),
        ("enPortGroup.htm", "👥 PORT GROUPS"),
        ("enPortAPNCfg.htm", "🌐 APN CONFIGURATION"),
        ("enWIABasicCfg.htm", "⚙️ WIRELESS BASIC CONFIG (Network/Band)"),
    ]

    for p, title in pages:
        res = session.get(f"{URL_BASE}/{p}", timeout=5)
        print(f"\n{'='*70}\n {title} ({p})\n{'='*70}")
        if res.status_code == 200:
            # Print script data if any
            scripts = re.findall(r'<script[^>]*>(.*?)</script>', res.text, re.DOTALL)
            for s in scripts:
                lines = [l.strip() for l in s.strip().splitlines() if l.strip() and not l.strip().startswith('//')]
                # Look for data arrays or definitions
                data_lines = [l for l in lines if 'var ' in l or 'Array' in l or 'data' in l.lower() or 'port' in l.lower() or 'stat' in l.lower()]
                if data_lines:
                    print("  [JS Data]:")
                    for dl in data_lines[:25]:
                        print("   ", dl)

            rows = parse_table(res.text)
            for r in rows:
                print("  | " + " | ".join(r))
        else:
            print(f"  Status: {res.status_code}")

if __name__ == '__main__':
    if login():
        inspect_sim_and_wireless()
