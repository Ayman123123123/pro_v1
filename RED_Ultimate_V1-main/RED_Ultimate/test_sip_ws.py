import websocket
import uuid
import time

def test_sip():
    uri = "ws://127.0.0.1:8088/ws"
    print(f"Connecting to {uri}...")
    
    def on_message(ws, message):
        print(f"Received:\n{message}")
        ws.close()
        
    def on_error(ws, error):
        print(f"Error: {error}")
        
    def on_close(ws, close_status_code, close_msg):
        print("Connection closed")
        
    def on_open(ws):
        print("Connected! Sending SIP REGISTER...")
        call_id = str(uuid.uuid4())
        register_msg = (
            "REGISTER sip:127.0.0.1 SIP/2.0\r\n"
            "Via: SIP/2.0/WS 127.0.0.1;branch=z9hG4bK-red-1\r\n"
            "From: <sip:red-webrtc-client@127.0.0.1>;tag=red-tag-1\r\n"
            "To: <sip:red-webrtc-client@127.0.0.1>\r\n"
            f"Call-ID: {call_id}\r\n"
            "CSeq: 1 REGISTER\r\n"
            "Contact: <sip:red-webrtc-client@127.0.0.1;transport=ws>\r\n"
            "Max-Forwards: 70\r\n"
            "Expires: 3600\r\n"
            "Content-Length: 0\r\n\r\n"
        )
        ws.send(register_msg)

    ws = websocket.WebSocketApp(uri,
                              subprotocols=["sip"],
                              on_open=on_open,
                              on_message=on_message,
                              on_error=on_error,
                              on_close=on_close)
    ws.run_forever()

if __name__ == "__main__":
    test_sip()
