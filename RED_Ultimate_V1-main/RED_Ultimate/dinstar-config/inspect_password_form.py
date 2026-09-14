import requests
from bs4 import BeautifulSoup
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
        r = session.get(f"{URL_BASE}/enPassword.htm")
        print("=== enPassword.htm ===")
        soup = BeautifulSoup(r.text, 'html.parser')
        form = soup.find('form')
        if form:
            print("Form Action:", form.get('action'))
            print("Inputs:")
            for inp in form.find_all('input'):
                print(f"  name: {inp.get('name')}, type: {inp.get('type')}, value: {inp.get('value')}")
            for tr in form.find_all('tr'):
                cells = [td.get_text(strip=True) for td in tr.find_all(['td', 'th'])]
                if cells and any(cells):
                    print("  | " + " | ".join(cells))
        else:
            print("No form found. Text:")
            print(r.text)
