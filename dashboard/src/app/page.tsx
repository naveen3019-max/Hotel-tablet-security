'use client';

import { useState, useEffect, useRef } from 'react';
import { useWebSocket } from '@/hooks/useWebSocket';
import LiveIndicator from '@/components/LiveIndicator';

interface Alert {
  id: string;
  deviceId: string;
  status: string;
  timestamp: string;
  reason: string;
}

export default function DashboardPage() {
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const lastFetchTimeRef = useRef<number>(Date.now());

  const { status, lastMessage, isLikelySilentDisconnect } = useWebSocket(
    process.env.NEXT_PUBLIC_API_URL?.replace('https://', 'wss://').replace('http://', 'ws://') + '/ws/dashboard'
  );

  useEffect(() => {
    if (lastMessage && lastMessage.type === 'breach') {
      lastFetchTimeRef.current = Date.now();
      setAlerts(prev => {
        const exists = prev.some(a => a.id === lastMessage.data.id);
        if (exists) return prev;
        return [lastMessage.data, ...prev].slice(0, 50);
      });
    }
  }, [lastMessage]);

  useEffect(() => {
    const pollAlerts = async () => {
      try {
        const timeSinceLastWsMessage = Date.now() - lastFetchTimeRef.current;
        
        if (status === 'connected' && !isLikelySilentDisconnect && timeSinceLastWsMessage < 20000) {
          return;
        }

        const res = await fetch('/api/alerts/recent');
        if (!res.ok) throw new Error('Poll failed');
        const data = await res.json();
        
        if (data && Array.isArray(data)) {
          lastFetchTimeRef.current = Date.now();
          setAlerts(prev => {
            const merged = [...data, ...prev];
            const unique = Array.from(new Map(merged.map(item => [item.id, item])).values());
            return unique.sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime()).slice(0, 50);
          });
        }
      } catch (err) {
        console.error('Backup polling error:', err);
      }
    };

    const interval = setInterval(pollAlerts, 15000);
    pollAlerts(); 

    return () => clearInterval(interval);
  }, [status, isLikelySilentDisconnect]);

  const getConnectionState = () => {
    if (status === 'connected' && !isLikelySilentDisconnect) return 'connected';
    if (status === 'connecting') return 'connecting';
    return 'disconnected';
  };

  return (
    <div className="p-8 max-w-6xl mx-auto">
      <div className="flex items-center justify-between mb-8">
        <h1 className="text-3xl font-bold">Security Dashboard</h1>
        <div className="flex items-center">
          <span className="text-gray-500 text-sm mr-2">Status:</span>
          <LiveIndicator status={getConnectionState()} />
        </div>
      </div>

      <div className="bg-white rounded-lg shadow overflow-hidden">
        <table className="min-w-full">
          <thead className="bg-gray-50">
            <tr>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Time</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Device</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Status</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Reason</th>
            </tr>
          </thead>
          <tbody className="bg-white divide-y divide-gray-200">
            {alerts.length === 0 ? (
              <tr>
                <td colSpan={4} className="px-6 py-4 text-center text-gray-500">No recent alerts</td>
              </tr>
            ) : (
              alerts.map((alert) => (
                <tr key={alert.id} className={alert.status === 'breach' ? 'bg-red-50 animate-pulse' : ''}>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                    {new Date(alert.timestamp).toLocaleTimeString()}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">
                    {alert.deviceId}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm">
                    <span className={`px-2 inline-flex text-xs leading-5 font-semibold rounded-full ${
                      alert.status === 'breach' ? 'bg-red-500 text-white shadow-[0_0_10px_rgba(239,68,68,0.7)]' : 'bg-green-100 text-green-800'
                    }`}>
                      {alert.status === 'breach' ? '🔴 BREACH' : '🟢 SECURE'}
                    </span>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    {alert.reason}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
