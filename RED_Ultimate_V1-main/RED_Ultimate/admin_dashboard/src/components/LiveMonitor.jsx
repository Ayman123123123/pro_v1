import React, { useEffect, useState } from 'react';

/**
 * RED Admin Live Monitor
 * Tracks System A (VoIP), System B (PSTN), and System C (Messages) in Real-time.
 */
const LiveMonitor = () => {
    const [stats, setStats] = useState({ voip: 0, pstn: 0, msgs: 0 });

    useEffect(() => {
        const interval = setInterval(() => {
            // Fetch live stats from backend
            fetch('/api/admin/monitor/stats')
                .then(res => res.json())
                .then(data => setStats(data));
        }, 2000);
        return () => clearInterval(interval);
    }, []);

    return (
        <div className="monitor-container">
            <h2>Live System Monitoring</h2>
            <div className="stat-card">4K VoIP Calls: {stats.voip}</div>
            <div className="stat-card">PSTN Calls (Dumin): {stats.pstn}</div>
            <div className="stat-card">Messages Delivered: {stats.msgs}</div>
        </div>
    );
};

export default LiveMonitor;
