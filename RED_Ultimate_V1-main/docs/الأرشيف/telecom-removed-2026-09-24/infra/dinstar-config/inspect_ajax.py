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

def inspect_ajax_endpoints():
    res = session.get(f"{URL_BASE}/enWIAPortStatNew.htm")
    print("--- enWIAPortStatNew.htm Script Extraction ---")
    scripts = re.findall(r'<script[^>]*>(.*?)</script>', res.text, re.DOTALL)
    for s in scripts:
        print(s)

    # Also test common DINSTAR AJAX endpoints
    ajax_endpoints = [
        "goform/get_port_status",
        "goform/get_mobile_status",
        "goform/get_sim_status",
        "goform/get_sys_info",
        "goform/get_port_stat",
        "goform/get_sim_info",
        "xml/port_stat.xml",
        "xml/sim_info.xml",
        "xml/mobile_info.xml",
        "ajax_get_port_status",
        "ajax_get_sim_status",
        "goform/WiaPortStatNew",
        "goform/getWiaPortStat"
    ]
    print("\n--- Testing AJAX endpoints ---")
    for ep in ajax_endpoints:
        r = session.get(f"{URL_BASE}/{ep}", timeout=2)
        if r.status_code != 404:
            print(f"[FOUND AJAX] {ep} (status: {r.status_code}, len: {len(r.text)})")
            print("  Content preview:", r.text[:200])

if __name__ == '__main__':
    if login():
        inspect_ajax_endpoints()
