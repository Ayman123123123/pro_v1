import React, { useEffect, useRef, useState } from 'react';
import { Alert, Card, Tag } from 'antd';
import { apiFetch } from '../../api';

const LogStreamerTab: React.FC = () => {
    const [logs, setLogs] = useState<string[]>([]);
    const [status, setStatus] = useState<'CONNECTING'|'ONLINE'|'OFFLINE'|'ERROR'>('CONNECTING');
    const [error, setError] = useState('');
    const scrollRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        let socket: WebSocket | undefined;
        let cancelled = false;
        const connect = async () => {
            setStatus('CONNECTING'); setError('');
            try {
                const response = await apiFetch('/api/admin/ws-ticket', { method: 'POST' });
                if (!response.ok) throw new Error(`HTTP ${response.status}`);
                const payload = await response.json();
                if (!payload.ticket) throw new Error('INVALID_TICKET_RESPONSE');
                if (cancelled) return;
                const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws';
                socket = new WebSocket(`${scheme}://${window.location.host}/ws/admin/logs?ticket=${encodeURIComponent(payload.ticket)}`);
                socket.onopen = () => setStatus('ONLINE');
                socket.onmessage = event => setLogs(previous => [...previous.slice(-199), String(event.data)]);
                socket.onerror = () => { setStatus('ERROR'); setError('تعذر فتح قناة السجل الآمنة'); };
                socket.onclose = () => { if (!cancelled) setStatus(current => current === 'ERROR' ? current : 'OFFLINE'); };
            } catch (reason: any) {
                if (!cancelled) { setStatus('ERROR'); setError(`تعذر إصدار تذكرة WebSocket: ${reason?.message || 'UNKNOWN'}`); }
            }
        };
        connect();
        return () => { cancelled = true; socket?.close(); };
    }, []);

    useEffect(() => {
        if (scrollRef.current) scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }, [logs]);

    const color = status === 'ONLINE' ? 'green' : status === 'CONNECTING' ? 'gold' : 'red';
    return (
        <Card title="سجل أحداث يونس المباشر" extra={<Tag color={color}>{status}</Tag>} style={{ background: '#030712', border: '1px solid #17344A' }}>
            {error && <Alert type="error" message={error} showIcon style={{ marginBottom: 12 }} />}
            <div ref={scrollRef} style={{ height: 400, overflowY: 'auto', fontFamily: 'monospace', background: '#020617', padding: 16, color: '#7EF0C5' }}>
                {logs.length === 0 && <div style={{ color: '#64748B' }}>لا توجد أحداث مستلمة بعد.</div>}
                {logs.map((log, index) => <div key={`${index}-${log}`} style={{ marginBottom: 4, borderBottom: '1px solid #0F172A' }}>{log}</div>)}
            </div>
        </Card>
    );
};

export default LogStreamerTab;
