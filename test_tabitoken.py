#!/usr/bin/env python3
"""
Test script to verify TabiToken API integration
Run: python test_tabitoken.py
"""
import os
import sys
import requests
from dotenv import load_dotenv

load_dotenv()

API_KEY = os.getenv("TABITOKEN_API_KEY")
BASE_URL = os.getenv("TABITOKEN_BASE_URL", "https://api.tabitoken.com/v1")

if not API_KEY:
    print("❌ TABITOKEN_API_KEY not found in .env")
    sys.exit(1)

headers = {
    "Authorization": f"Bearer {API_KEY}",
    "Content-Type": "application/json",
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Accept": "application/json",
    "Accept-Language": "en-US,en;q=0.9",
}

def test_models():
    """Test /v1/models endpoint"""
    print("[*] Testing /v1/models endpoint...")
    try:
        resp = requests.get(f"{BASE_URL}/models", headers=headers, timeout=10)
        if resp.status_code == 200:
            data = resp.json()
            models = [m.get('id', 'unknown') for m in data.get('data', [])]
            print(f"[OK] Success! Available models ({len(models)}):")
            for m in models[:20]:
                print(f"   - {m}")
            if len(models) > 20:
                print(f"   ... and {len(models) - 20} more")
            return models
        else:
            print(f"[FAIL] Failed: {resp.status_code} - {resp.text}")
            return []
    except Exception as e:
        print(f"[FAIL] Error: {e}")
        return []

def test_chat(model="gpt-4o-mini"):
    """Test /v1/chat/completions endpoint"""
    print(f"\n[*] Testing chat with {model}...")
    try:
        payload = {
            "model": model,
            "messages": [{"role": "user", "content": "Say 'Hello from TabiToken!'"}],
            "max_tokens": 50,
            "temperature": 0.7
        }
        resp = requests.post(f"{BASE_URL}/chat/completions", headers=headers, json=payload, timeout=30)
        if resp.status_code == 200:
            data = resp.json()
            content = data['choices'][0]['message']['content']
            usage = data.get('usage', {})
            print(f"[OK] Response: {content}")
            print(f"   Tokens: {usage.get('total_tokens', 'N/A')}")
            return True
        else:
            print(f"[FAIL] Failed: {resp.status_code} - {resp.text}")
            return False
    except Exception as e:
        print(f"[FAIL] Error: {e}")
        return False

def test_streaming(model="gpt-4o-mini"):
    """Test streaming"""
    print(f"\n[*] Testing streaming with {model}...")
    try:
        payload = {
            "model": model,
            "messages": [{"role": "user", "content": "Count from 1 to 5"}],
            "max_tokens": 50,
            "stream": True
        }
        resp = requests.post(f"{BASE_URL}/chat/completions", headers=headers, json=payload, stream=True, timeout=30)
        if resp.status_code == 200:
            print("[OK] Stream started:")
            for line in resp.iter_lines():
                if line:
                    decoded = line.decode('utf-8')
                    if decoded.startswith('data: '):
                        data_str = decoded[6:]
                        if data_str.strip() == '[DONE]':
                            break
                        try:
                            import json
                            chunk = json.loads(data_str)
                            content = chunk['choices'][0]['delta'].get('content', '')
                            if content:
                                print(content, end='', flush=True)
                        except:
                            pass
            print("\n[OK] Stream completed")
            return True
        else:
            print(f"[FAIL] Failed: {resp.status_code} - {resp.text}")
            return False
    except Exception as e:
        print(f"[FAIL] Error: {e}")
        return False

if __name__ == "__main__":
    print("=" * 50)
    print("TabiToken API Integration Test")
    print("=" * 50)
    print(f"Base URL: {BASE_URL}")
    print(f"API Key: {API_KEY[:10]}...{API_KEY[-4:]}")
    
    models = test_models()
    
    if models:
        # Test with first available model or default
        test_model = models[0] if models else "gpt-4o-mini"
        test_chat(test_model)
        test_streaming(test_model)
    
    print("\n" + "=" * 50)
    print("Test complete!")
    print("=" * 50)