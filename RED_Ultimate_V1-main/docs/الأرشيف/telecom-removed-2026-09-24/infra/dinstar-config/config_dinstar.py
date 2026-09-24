import requests
from bs4 import BeautifulSoup
import urllib3

urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

URL_BASE = 'https://192.168.11.1'
USERNAME = 'admin'
PASSWORD = 'admin'

session = requests.Session()
session.verify = False

def login():
    print("Logging in...")
    data = {'username': USERNAME, 'password': PASSWORD}
    r = session.post(f"{URL_BASE}/goform/IADIdentityAuth", data=data, allow_redirects=False)
    if 'JSESSIONID' in session.cookies or r.status_code == 302:
        print("Logged in successfully.")
        return True
    print("Login failed.")
    return False

def configure_sip():
    print("Fetching SIP configuration page...")
    r = session.get(f"{URL_BASE}/enSIPCfg.htm")
    soup = BeautifulSoup(r.text, 'html.parser')
    form = soup.find('form', id='SipCfg')
    if not form:
        print("Could not find SIP configuration form.")
        return

    # Extract all inputs
    payload = {}
    for input_tag in form.find_all('input'):
        name = input_tag.get('name')
        if not name:
            continue
        
        type_ = input_tag.get('type', 'text').lower()
        if type_ in ('radio', 'checkbox'):
            if input_tag.has_attr('checked'):
                payload[name] = input_tag.get('value', 'on')
        else:
            payload[name] = input_tag.get('value', '')

    for select_tag in form.find_all('select'):
        name = select_tag.get('name')
        if not name:
            continue
        selected_option = select_tag.find('option', selected=True)
        if selected_option:
            payload[name] = selected_option.get('value', '')
        else:
            options = select_tag.find_all('option')
            if options:
                payload[name] = options[0].get('value', '')

    print(f"Current SIP Server: {payload.get('SipPxyIP')}:{payload.get('SipPxyPort')}")
    
    # Update values
    payload['SipPxyIP'] = '192.168.11.100'
    payload['SipPxyPort'] = '5060'

    # Send POST request
    action = form.get('action', '/goform/SipCfg')
    print("Saving new SIP configuration...")
    post_res = session.post(f"{URL_BASE}{action}", data=payload)
    print(f"Response status: {post_res.status_code}")

if __name__ == '__main__':
    if login():
        configure_sip()
        print("Configuration applied!")
