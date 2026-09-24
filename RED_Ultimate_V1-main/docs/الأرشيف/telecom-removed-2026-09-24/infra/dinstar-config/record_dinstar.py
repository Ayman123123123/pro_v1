from playwright.sync_api import sync_playwright
import time
import os
import glob

def record_dinstar():
    with sync_playwright() as p:
        print("Launching browser...")
        browser = p.chromium.launch(headless=True)
        # We record the video to the artifacts directory
        video_dir = r"C:\Users\hpc01\.gemini\antigravity-ide\brain\068e9d4f-34ca-4808-ab58-a99a5d5ca629\browser"
        os.makedirs(video_dir, exist_ok=True)
        context = browser.new_context(
            record_video_dir=video_dir,
            record_video_size={"width": 1280, "height": 720},
            ignore_https_errors=True
        )
        page = context.new_page()
        
        print("Navigating to Dinstar...")
        page.goto("https://192.168.11.1/enLogin.htm")
        page.wait_for_timeout(1000)
        
        print("Logging in...")
        page.fill("input[name='username']", "admin")
        page.fill("input[name='password']", "admin")
        page.click("input[id='login_button']")
        
        print("Waiting for dashboard frames...")
        page.wait_for_timeout(2000)
        
        print("Navigating to SIP Configuration...")
        # Since it uses frames, we should find the menu frame and click SIP config, or just go directly to the page.
        page.goto("https://192.168.11.1/enSIPCfg.htm")
        page.wait_for_timeout(3000)
        
        print("Taking a screenshot for good measure...")
        page.screenshot(path=os.path.join(video_dir, "sip_config_screenshot.png"))
        
        print("Closing context to save video...")
        context.close()
        browser.close()
        
        # Rename the latest webm file to a nice name
        list_of_files = glob.glob(f'{video_dir}/*.webm')
        if list_of_files:
            latest_file = max(list_of_files, key=os.path.getctime)
            new_name = os.path.join(video_dir, "dinstar_login_demo.webm")
            if os.path.exists(new_name):
                os.remove(new_name)
            os.rename(latest_file, new_name)
            print(f"Video saved as {new_name}")
        else:
            print("Video file not found.")

if __name__ == "__main__":
    record_dinstar()
