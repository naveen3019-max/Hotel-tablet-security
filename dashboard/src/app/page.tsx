'use client';

import { useState, useEffect, useRef } from 'react';
import { useWebSocket } from '@/hooks/useWebSocket';

interface Alert {
  id: string;
  deviceId: string;
  status: string;
  timestamp: string;
  reason: string;
}

export default function DashboardPage() {
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const lastAlertTimestamp = useRef<number>(0);

  const wsUrl = process.env.NEXT_PUBLIC_API_URL?.replace('https://', 'wss://').replace('http://', 'ws://') + '/ws/dashboard';
  const { status, lastMessage, isPollingMode } = useWebSocket(wsUrl);

  useEffect(() => {
    if (lastMessage && lastMessage.type === 'breach') {
      const ts = new Date(lastMessage.data.timestamp).getTime();
      if (ts > lastAlertTimestamp.current) lastAlertTimestamp.current = ts;
      
      setAlerts(prev => {
        if (prev.some(a => a.id === lastMessage.data.id)) return prev;
        return [lastMessage.data, ...prev].slice(0, 50);
      });
    }
  }, [lastMessage]);

  useEffect(() => {
    const pollAlerts = async () => {
      try {
        const res = await fetch('/api/alerts/recent');
        if (!res.ok) return;
        const data = await res.json();
        
        if (Array.isArray(data)) {
          let injected = false;
          setAlerts(prev => {
            const merged = [...data, ...prev];
            const unique = Array.from(new Map(merged.map(i => [i.id, i])).values());
            const sorted = unique.sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime()).slice(0, 50);
            
            if (sorted.length > 0) {
              const newestTs = new Date(sorted[0].timestamp).getTime();
              if (newestTs > lastAlertTimestamp.current) {
                lastAlertTimestamp.current = newestTs;
                injected = true;
              }
            }
            return sorted;
          });
          if (injected && isPollingMode) console.log("God Mode: Polling caught missed breach.");
        }
      } catch (e) {
        // silent catch
      }
    };

    const interval = setInterval(pollAlerts, 15000);
    pollAlerts(); 
    return () => clearInterval(interval);
  }, [isPollingMode]); 

  const displayStatus = () => {
    if (status === 'connected' && !isPollingMode) return <span className="text-green-500 flex items-center"><div className="w-3 h-3 bg-green-500 rounded-full mr-2 animate-pulse shadow-[0_0_8px_rgba(34,197,94,0.8)]"/>LIVE</span>;
    if (status === 'connecting') return <span className="text-yellow-500 flex items-center"><div className="w-3 h-3 bg-yellow-500 rounded-full mr-2"/>RECONNECTING</span>;
    return <span className="text-red-500 flex items-center"><div className="w-3 h-3 bg-red-500 rounded-full mr-2"/>POLLING</span>;
  };

  return (
    <div className="p-8 max-w-6xl mx-auto bg-gray-50 min-h-screen">
      <div className="flex items-center justify-between mb-8 bg-white p-6 rounded-xl shadow-sm border border-gray-100">
        <h1 className="text-3xl font-bold tracking-tight text-gray-900">God Mode Security</h1>
        <div className="font-bold tracking-widest bg-gray-100 px-4 py-2 rounded-lg">
          {displayStatus()}
        </div>
      </div>

      <div className="bg-white rounded-xl shadow-lg border border-gray-200 overflow-hidden transition-all duration-300">
        <table className="min-w-full">
          <thead className="bg-gray-900 text-white">
            <tr>
              <th className="px-6 py-4 text-left text-xs font-bold uppercase tracking-widest">Time</th>
              <th className="px-6 py-4 text-left text-xs font-bold uppercase tracking-widest">Device</th>
              <th className="px-6 py-4 text-left text-xs font-bold uppercase tracking-widest">Status</th>
              <th className="px-6 py-4 text-left text-xs font-bold uppercase tracking-widest">Reason</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-200">
            {alerts.length === 0 ? (
              <tr><td colSpan={4} className="px-6 py-8 text-center text-gray-500 font-medium">All Systems Secure. No Alerts.</td></tr>
            ) : (
              alerts.map((alert) => {
                const isBreach = alert.status === 'breach';
                return (
                  <tr key={alert.id} className={`${isBreach ? 'bg-red-50 border-l-4 border-red-500 animate-pulse' : 'hover:bg-gray-50'} transition-colors duration-200`}>
                    <td className="px-6 py-5 whitespace-nowrap text-sm font-medium text-gray-900">{new Date(alert.timestamp).toLocaleTimeString()}</td>
                    <td className="px-6 py-5 whitespace-nowrap text-sm font-bold text-gray-800">{alert.deviceId}</td>
                    <td className="px-6 py-5 whitespace-nowrap">
                      <span className={`px-4 py-1.5 inline-flex text-xs font-extrabold uppercase tracking-widest rounded-full ${isBreach ? 'bg-red-600 text-white shadow-[0_0_12px_rgba(220,38,38,0.8)]' : 'bg-green-100 text-green-800'}`}>
                        {isBreach ? '🚨 BREACH' : '✓ SECURE'}
                      </span>
                    </td>
                    <td className="px-6 py-5 text-sm text-gray-600 font-medium">{alert.reason}</td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
