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
    if 'devckie' in session.cookies or 'JSESSIONID' in session.cookies or r.status_code == 302:
        print("[✓] Logged in to DINSTAR successfully.")
        return True
    print("[✗] Login failed.")
    return False

def configure_sip_and_auto_route():
    print("\n--- 1. Configuring SIP Server & Auto Route ---")
    r = session.get(f"{URL_BASE}/enSIPCfg.htm")
    soup = BeautifulSoup(r.text, 'html.parser')
    form = soup.find('form', id='SipCfg')
    if not form:
        print("[!] Form SipCfg not found.")
        return

    payload = {}
    for inp in form.find_all('input'):
        n = inp.get('name')
        if not n:
            continue
        t = inp.get('type', 'text').lower()
        if t in ('radio', 'checkbox'):
            if inp.has_attr('checked'):
                payload[n] = inp.get('value', '1')
        else:
            payload[n] = inp.get('value', '')

    for sel in form.find_all('select'):
        n = sel.get('name')
        if not n:
            continue
        opt = sel.find('option', selected=True)
        if opt:
            payload[n] = opt.get('value', '')
        else:
            opts = sel.find_all('option')
            if opts:
                payload[n] = opts[0].get('value', '')

    # Set Asterisk IP and Port
    payload['SipPxyIP'] = '192.168.11.100'
    payload['SipPxyPort'] = '5060'
    # Enable Auto IP->TEL Route so all inbound SIP calls can dial out via GSM
    payload['AutoIp2TelRoute'] = '1'

    action = form.get('action', '/goform/SipCfg')
    res = session.post(f"{URL_BASE}{action}", data=payload)
    print(f"[✓] SIP Configuration saved! Status: {res.status_code}")

def configure_wireless_apn_and_mode():
    print("\n--- 2. Configuring Wireless Port Parameters (Sabafon / YOU) ---")
    r = session.get(f"{URL_BASE}/enWIAPortStatNew.htm")
    soup = BeautifulSoup(r.text, 'html.parser')
    form = soup.find('form')
    if not form:
        print("[!] Wireless form not found.")
        return

    payload = {}
    for inp in form.find_all('input'):
        n = inp.get('name')
        if not n:
            continue
        t = inp.get('type', 'text').lower()
        if t in ('radio', 'checkbox'):
            if inp.has_attr('checked'):
                payload[n] = inp.get('value', '1')
        else:
            payload[n] = inp.get('value', '')

    for sel in form.find_all('select'):
        n = sel.get('name')
        if not n:
            continue
        opt = sel.find('option', selected=True)
        if opt:
            payload[n] = opt.get('value', '')
        else:
            opts = sel.find_all('option')
            if opts:
                payload[n] = opts[0].get('value', '')

    # Configure Port 2 (Sabafon) & Port 7
    payload['APN2'] = 'internet'
    payload['APNName2'] = 'internet'
    payload['APNPSW2'] = 'internet'
    payload['SimWorkMode2'] = '0'  # Auto
    payload['IsGsmOpen2'] = '1'    # Enabled

    payload['APN7'] = 'YOU-DATA'
    payload['SimWorkMode7'] = '0'  # Auto
    payload['IsGsmOpen7'] = '1'    # Enabled

    # General auto settings
    payload['SimModeAll'] = '0'
    payload['IsGsmOpenAll'] = '1'

    action = form.get('action', '/goform/WIAPortCfgNew')
    res = session.post(f"{URL_BASE}{action}", data=payload)
    print(f"[✓] Wireless Ports configured! Status: {res.status_code}")

def verify_live_state():
    print("\n--- 3. Verifying Live SIM State ---")
    r = session.get(f"{URL_BASE}/WebGetPortInfoAll")
    if r.status_code == 200:
        import json
        try:
            data = json.loads(r.text.strip())
            for p in data:
                if p.get('port') != 'Total':
                    port_no = p.get('port')
                    status = p.get('status')
                    operator = p.get('operator') or 'N/A'
                    signal = p.get('signal') or '0'
                    imsi = p.get('imsi') or 'N/A'
                    imei = p.get('imei') or 'N/A'
                    call_stat = p.get('call_status')
                    print(f"  [Port {port_no}] Operator: {operator.ljust(10)} | Signal: {signal}/5 | Status: {status.ljust(20)} | IMSI: {imsi} | CallState: {call_stat}")
        except Exception as e:
            print("Parse error:", e)

if __name__ == '__main__':
    if login():
        configure_sip_and_auto_route()
        configure_wireless_apn_and_mode()
        verify_live_state()
