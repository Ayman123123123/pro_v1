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

def scan_navigation_and_scripts():
    nav_pages = [
        "enIndex.htm", "enMain.htm", "enLeft.htm", "enTop.htm",
        "enMenu.htm", "common/menu.js", "common/func.js",
        "common/common.js", "common/table.js"
    ]
    all_found_links = set()
    for p in nav_pages:
        res = session.get(f"{URL_BASE}/{p}", timeout=5)
        if res.status_code == 200:
            print(f"\n[FOUND NAV] {p} (len: {len(res.text)})")
            links = re.findall(r'[\'"]([a-zA-Z0-9_\-\/]+\.(?:htm|html|js|cgi))[\'"]', res.text)
            for l in links:
                all_found_links.add(l)
                
            # Also find menu arrays: e.g. ['Title', 'link.htm']
            menu_items = re.findall(r'\[\s*["\']([^"\']+)["\']\s*,\s*["\']([^"\']+\.htm)["\']', res.text)
            if menu_items:
                print("Menu Items:")
                for title, link in menu_items:
                    print(f"  • {title} -> {link}")
                    all_found_links.add(link)

    print("\n--- All Discovered Links ---")
    for l in sorted(all_found_links):
        print(f"  {l}")

if __name__ == '__main__':
    if login():
        scan_navigation_and_scripts()
