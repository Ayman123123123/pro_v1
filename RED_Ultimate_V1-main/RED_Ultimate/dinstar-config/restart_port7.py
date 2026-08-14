import requests
import urllib3

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

def restart_port(port_no):
    print(f"Restarting SIM Port {port_no}...")
    data = {'PortNo': str(port_no)}
    r = session.post(f"{URL_BASE}/goform/SimGotoRestart", data=data)
    print(f"Response status: {r.status_code}")

if __name__ == '__main__':
    if login():
        restart_port(7)
