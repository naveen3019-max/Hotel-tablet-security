"use client";
import { useEffect, useState, useCallback, useRef } from "react";
import { useWebSocket } from "../hooks/useWebSocket";
import { useAuth } from "../hooks/useAuth";
import LiveIndicator from "../components/LiveIndicator";

const API = process.env.NEXT_PUBLIC_API_URL || "https://hotel-backend-zqc1.onrender.com";
const DASHBOARD_VERSION = "v5.0-premium-dark";

const TrashIcon = ({ className }: { className?: string }) => (
  <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}>
    <path d="M3 6h18"/>
    <path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/>
    <path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/>
  </svg>
);

const CheckIcon = ({ className }: { className?: string }) => (
  <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}>
    <polyline points="20 6 9 17 4 12"/>
  </svg>
);

// Format timestamp to Indian Standard Time
const formatISTTime = (dateString: string): string => {
  try {
    const match = dateString.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})/);
    if (!match) return dateString;
    const [, year, month, day, hour24, minute, second] = match;
    let hour = parseInt(hour24);
    const ampm = hour >= 12 ? "PM" : "AM";
    hour = hour % 12 || 12;
    return `${day}/${month}/${year} ${String(hour).padStart(2, "0")}:${minute}:${second} ${ampm} IST`;
  } catch {
    return dateString;
  }
};

type Device = {
  deviceId: string;
  roomId?: string;
  status?: string;
  battery?: number;
  rssi?: number;
  lastSeen?: string;
  ip?: string;
};

type Alert = {
  id?: string;
  type: string;
  deviceId?: string;
  roomId?: string;
  payload?: Record<string, unknown>;
  ts: string;
  acknowledged?: boolean;
  notes?: string;
  message?: string;
};

type Toast = {
  id: number;
  deviceId: string;
  roomId?: string;
  reason?: string;
};

export default function EnhancedDashboard() {
  const { isAuthenticated, checking, logout, user } = useAuth();
  const [devices, setDevices] = useState<Device[]>([]);
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [filter, setFilter] = useState<string>("all");
  const [searchQuery, setSearchQuery] = useState<string>("");
  const [selectedAlert, setSelectedAlert] = useState<Alert | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const [toasts, setToasts] = useState<Toast[]>([]);
  const toastIdCounter = useRef(0);

  // New states for redesign
  const [deleteConfirm, setDeleteConfirm] = useState<string | null>(null);
  const [alertFilter, setAlertFilter] = useState<string>("all");
  const [visibleAlertsCount, setVisibleAlertsCount] = useState<number>(50);

  const { lastMessage, status: connectionStatus } = useWebSocket(API);

  const addToast = useCallback((deviceId: string, roomId?: string, reason?: string) => {
    const id = ++toastIdCounter.current;
    setToasts((prev: Toast[]) => [...prev, { id, deviceId, roomId, reason }]);
    setTimeout(() => {
      setToasts((prev: Toast[]) => prev.filter((t: Toast) => t.id !== id));
    }, 8000);
  }, []);

  useEffect(() => {
    if (!lastMessage) return;

    const { type, data } = lastMessage;
    const d = data as Record<string, unknown> | undefined;

    setTimeout(() => {
      if (lastMessage && (
          lastMessage.type === 'breach' ||
          (lastMessage.type === 'alert' && d?.type === 'breach') ||
          (lastMessage.type === 'device_update' && d?.status === 'breach')
      )) {
          const breachDeviceId = d?.deviceId || d?.device_id || (lastMessage as Record<string, unknown>).deviceId;
          
          if (breachDeviceId) {
              setDevices((prev: Device[]) =>
                  prev.map((dev: Device) =>
                      dev.deviceId === breachDeviceId ? { ...dev, status: 'breach' } : dev
                  )
              );
          }
          
          const newAlert: Alert = {
            type: "breach",
            deviceId: (breachDeviceId as string) || "Unknown",
            roomId: d?.roomId as string | undefined,
            ts: ((lastMessage as Record<string, unknown>).timestamp as string | undefined) ?? new Date().toISOString(),
            acknowledged: false,
            message: d?.message as string | undefined,
          };
          
          setAlerts((prev: Alert[]) => [newAlert, ...prev].sort((a, b) => new Date(b.ts).getTime() - new Date(a.ts).getTime()).slice(0, 100));
          
          if (breachDeviceId) {
            addToast(breachDeviceId as string, d?.roomId as string | undefined, d?.message as string | undefined);
          }
      }

      if (lastMessage?.type === 'device_update' && d?.deviceId) {
          const update = d;
          setDevices((prev: Device[]) => {
              const exists = prev.some(dev => dev.deviceId === update.deviceId);
              if (!exists) {
                  return [...prev, {
                      deviceId: update.deviceId as string,
                      status: (update.status as string) || 'ok',
                      battery: update.battery as number,
                      rssi: update.rssi as number,
                      lastSeen: update.lastSeen as string
                  }];
              }
              return prev.map((dev: Device) =>
                  dev.deviceId === update.deviceId
                      ? {
                          ...dev,
                          status: (update.status as string) ?? dev.status,
                          rssi: (update.rssi as number) ?? dev.rssi,
                          battery: (update.battery as number) ?? dev.battery,
                          lastSeen: (update.lastSeen as string) ?? dev.lastSeen
                        }
                      : dev
              );
          });
      }

      if (lastMessage?.type === 'device_recovered' && d?.deviceId) {
          setDevices((prev: Device[]) =>
              prev.map((dev: Device) =>
                  dev.deviceId === d.deviceId ? { ...dev, status: 'ok' } : dev
              )
          );
      }

      if (type === "device_offline" || type === "device_deleted") {
          if (d?.deviceId) {
              if (type === "device_deleted") {
                  setDevices((prev: Device[]) => prev.filter((dev: Device) => dev.deviceId !== d.deviceId));
              } else {
                  setDevices((prev: Device[]) =>
                      prev.map((dev: Device) =>
                          dev.deviceId === d.deviceId ? { ...dev, status: "offline" } : dev
                      )
                  );
              }
          }
      }
      
      if (type === "database_cleared") {
          setDevices([]);
          setAlerts([]);
      }
    }, 0);
  }, [lastMessage, addToast]);

  useEffect(() => {
    if (!API) {
      setTimeout(() => {
        setError("API URL not configured");
        setIsLoading(false);
      }, 0);
      return;
    }

    const fetchAll = async () => {
      try {
        setTimeout(() => setError(null), 0);
        const headers = { Authorization: `Bearer ${user?.token}` };
        const [devicesRes, alertsRes] = await Promise.all([
          fetch(`${API}/api/devices`, { headers }),
          fetch(`${API}/api/alerts/recent?limit=100`, { headers }),
        ]);

        if (!devicesRes.ok || !alertsRes.ok) {
          setError(`API Error: ${devicesRes.status} / ${alertsRes.status}`);
          setIsLoading(false);
          return;
        }

        const d = await devicesRes.json();
        const a = await alertsRes.json();

        setDevices(Array.isArray(d) ? d.filter((dev: Device) => dev?.deviceId) : []);
        setAlerts(Array.isArray(a) ? [...a].sort((a, b) => new Date(b.ts).getTime() - new Date(a.ts).getTime()).slice(0, 100) : []);
        setIsLoading(false);
      } catch (e) {
        setError(e instanceof Error ? e.message : "Failed to fetch data");
        setIsLoading(false);
      }
    };

    fetchAll();
    const pollId = setInterval(fetchAll, 10000);
    return () => clearInterval(pollId);
  }, [user?.token]);

  const handleDeleteDevice = async (deviceId: string) => {
    try {
      await fetch(`${API}/api/devices/${deviceId}`, { 
        method: 'DELETE',
        headers: { Authorization: `Bearer ${user?.token}` }
      });
      setDevices((prev) => prev.filter((d) => d.deviceId !== deviceId));
      setDeleteConfirm(null);
    } catch (e) {
      console.error("Failed to delete device", e);
    }
  };

  const acknowledgeAlert = async (alert: Alert) => {
    try {
      const deviceId = alert.deviceId ?? (alert.payload?.deviceId as string);
      if (!deviceId) return;
      await fetch(`${API}/api/alerts/acknowledge`, {
        method: "POST",
        headers: { 
          "Content-Type": "application/json",
          "Authorization": `Bearer ${user?.token}`
        },
        body: JSON.stringify({
          device_id: deviceId,
          timestamp: alert.ts,
          notes: "Acknowledged from dashboard",
        }),
      });
      setAlerts((prev: Alert[]) =>
        prev.map((a: Alert) => (a === alert ? { ...a, acknowledged: true } : a))
      );
    } catch (e) {
      console.error("Failed to acknowledge alert", e);
    }
  };

  const acknowledgeAll = async () => {
    try {
      await fetch(`${API}/api/alerts/acknowledge-all`, { 
        method: 'POST',
        headers: { Authorization: `Bearer ${user?.token}` }
      });
      setAlerts((prev) => prev.map((a) => ({ ...a, acknowledged: true })));
    } catch (e) {
      console.error("Failed to acknowledge all", e);
    }
  };

  const filteredDevices = devices.filter((d: Device) => {
    if (!d?.deviceId) return false;
    
    // Status filter
    if (filter === "ok" && d.status !== "ok") return false;
    if (filter === "breach" && d.status !== "breach") return false;
    if (filter === "offline" && (d.status !== "offline" && d.rssi !== -127)) return false;
    if (filter === "missing" && d.status !== "missing") return false;
    if (filter === "low_battery" && (d.battery === undefined || d.battery > 20)) return false;

    // Search query
    if (
      searchQuery &&
      !d.deviceId.toLowerCase().includes(searchQuery.toLowerCase()) &&
      !(d.roomId && d.roomId.toString().toLowerCase().includes(searchQuery.toLowerCase()))
    )
      return false;
      
    return true;
  });

  const getStatusCardClasses = (status?: string, rssi?: number) => {
    if (status === "breach") {
      return "border-2 border-red-400 bg-red-900/20/50 shadow-[0_8px_32px_rgba(0,0,0,0.5)]-red-100 shadow-[0_8px_32px_rgba(0,0,0,0.5)]-md";
    }
    if (status === "offline" || rssi === -127) {
      return "border border-gray-700 bg-[#0a0f1e] opacity-75";
    }
    // Default OK
    return "border border-green-800/50 bg-green-900/20/30";
  };

  const getStatusDotColor = (status?: string, rssi?: number) => {
    if (status === "breach") return "bg-red-900/200 animate-pulse";
    if (status === "offline" || rssi === -127) return "bg-gray-400";
    if (status === "missing") return "bg-yellow-500";
    return "bg-green-900/200";
  };

  const getBatteryClass = (battery?: number) => {
    if (battery === undefined) return "text-gray-500";
    if (battery > 50) return "text-green-600";
    if (battery > 20) return "text-yellow-600";
    return "text-red-600 font-bold";
  };

  const getRssiClass = (rssi?: number) => {
    if (rssi === undefined) return "text-gray-500";
    if (rssi === -127) return "text-gray-400 italic";
    if (rssi >= -60) return "text-green-600";
    if (rssi >= -70) return "text-yellow-600";
    if (rssi >= -80) return "text-orange-500";
    return "text-red-500";
  };

  const getRssiText = (rssi?: number) => {
    if (rssi === undefined) return "ΓÇö";
    if (rssi === -127) return "No signal";
    return `${rssi} dBm`;
  };

  const getBatteryText = (battery?: number) => {
    if (battery === undefined) return "ΓÇö";
    if (battery <= 20) return `${battery}% ΓÜá∩╕Å`;
    return `${battery}%`;
  };

  // Header stats
  const okCount = devices.filter(d => d.status === "ok" && d.rssi !== -127).length;
  const breachCount = devices.filter(d => d.status === "breach").length;
  const offlineCount = devices.filter(d => d.status === "offline" || d.rssi === -127).length;
  const unackCount = alerts.filter(a => !a.acknowledged).length;

  // Filtered alerts
  const filteredAlerts = alerts.filter(a => {
    if (alertFilter === 'all') return true;
    if (alertFilter === 'breach') return a.type === 'breach';
    if (alertFilter === 'wifi') return a.message?.toLowerCase().includes('wifi');
    if (alertFilter === 'offline') return a.message?.toLowerCase().includes('heartbeat') || a.type === 'offline';
    return true;
  });


  if (checking) return (
    <div className="min-h-screen flex items-center justify-center">
      <div className="animate-spin w-8 h-8 border-4 border-blue-500 border-t-transparent rounded-full" />
    </div>
  );

  if (!isAuthenticated) return null;

  return (
    <main className="dark-theme min-h-screen bg-[#0a0f1e] p-4 sm:p-6">
      <div className="fixed bottom-4 right-4 z-50 flex flex-col gap-2">
        {toasts.map((toast: Toast) => (
          <div key={toast.id} className="bg-red-600 text-white px-5 py-3 rounded-lg shadow-[0_8px_32px_rgba(0,0,0,0.5)]-xl flex items-start gap-3 max-w-sm animate-bounce">
            <span className="text-2xl">≡ƒÜ¿</span>
            <div>
              <p className="font-bold text-sm">BREACH DETECTED</p>
              <p className="text-xs mt-0.5">Device: {toast.deviceId}</p>
              {toast.roomId && <p className="text-xs">Room: {toast.roomId}</p>}
              {toast.reason && <p className="text-xs opacity-80 mt-1">{toast.reason}</p>}
            </div>
            <button
              onClick={() => setToasts((prev: Toast[]) => prev.filter((t: Toast) => t.id !== toast.id))}
              className="ml-auto text-white opacity-70 hover:opacity-100 text-lg leading-none min-h-[44px] min-w-[44px] flex items-center justify-center"
            >
              ├ù
            </button>
          </div>
        ))}
      </div>

      <div className="max-w-7xl mx-auto space-y-6">
        {error && (
          <div className="bg-red-900/20 border-2 border-red-800/50 rounded-lg p-4">
            <div className="flex items-center gap-2">
              <span className="text-red-600 font-semibold">ΓÜá∩╕Å Error:</span>
              <span className="text-red-700">{error}</span>
            </div>
            <div className="mt-2 text-sm text-red-600">
              <p>Backend URL: {API || "Not configured"}</p>
            </div>
          </div>
        )}

        {isLoading && devices.length === 0 && (
          <div className="bg-blue-900/20 border-2 border-blue-800/50 rounded-lg p-4 text-center">
            <div className="text-blue-600 font-semibold">≡ƒöä Loading dashboard...</div>
          </div>
        )}

        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
          <div>
            <h1 className="text-2xl sm:text-3xl font-bold text-gray-100">Hotel Tablet Security</h1>
            <div className="flex flex-wrap items-center gap-4 text-sm mt-1">
              <span className="text-green-600 font-medium">Γ£ô {okCount} secure</span>
              <span className="text-red-500 font-medium">≡ƒÜ¿ {breachCount} breach</span>
              <span className="text-gray-400 font-medium">ΓÜ½ {offlineCount} offline</span>
              <span className="text-orange-500 font-medium">≡ƒöö {unackCount} unacknowledged</span>
            </div>
          </div>
          <div className="flex items-center gap-3 w-full sm:w-auto justify-between sm:justify-end">
            {unackCount > 0 && (
              <button 
                onClick={acknowledgeAll} 
                className="text-xs text-blue-600 font-medium hover:underline bg-blue-900/20 px-3 py-2 rounded-md min-h-[44px] sm:min-h-0"
              >
                Acknowledge all alerts
              </button>
            )}
            <div className="flex items-center gap-2">
              <button
                onClick={logout}
                className="text-xs text-gray-500 hover:text-red-500 border border-gray-800 hover:border-red-300 px-3 py-1 rounded-full transition-colors"
              >
                Sign Out
              </button>
              <span className="text-xs text-gray-400 hidden sm:inline">{DASHBOARD_VERSION}</span>
              <LiveIndicator status={connectionStatus} />
            </div>
          </div>
        </div>

        <div className="bg-[#141b2d]/80 backdrop-blur-xl border-gray-800 rounded-lg shadow-[0_8px_32px_rgba(0,0,0,0.5)] p-4 flex flex-col sm:flex-row gap-2 w-full">
          <input
            type="text"
            placeholder="Search devices or rooms..."
            className="flex-1 px-4 py-2 border rounded-lg min-h-[44px] text-sm sm:text-base"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
          <select
            className="px-4 py-2 border rounded-lg min-h-[44px] text-sm sm:text-base"
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
          >
            <option value="all">All Devices</option>
            <option value="ok">Secure Only</option>
            <option value="breach">Breach Only</option>
            <option value="offline">Offline Only</option>
            <option value="low_battery">Low Battery</option>
          </select>
        </div>

        <section>
          <h2 className="text-xl font-semibold mb-4">Fleet Status</h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {filteredDevices.length === 0 && !isLoading && (
              <div className="col-span-full text-center py-8 text-gray-500 bg-[#141b2d]/80 backdrop-blur-xl border-gray-800 rounded-lg border border-dashed border-gray-700">
                No devices found matching your criteria.
              </div>
            )}
            {filteredDevices.map((d: Device) => (
                <div
                  key={d.deviceId}
                  className={`rounded-lg p-5 transition-shadow-[0_8px_32px_rgba(0,0,0,0.5)] relative ${getStatusCardClasses(d.status, d.rssi)}`}
                >
                  <div className="flex justify-between items-start mb-3">
                    <div className="flex-1 pr-8">
                      <h3 className="font-bold text-lg truncate max-w-[180px] sm:max-w-[200px]" title={d.deviceId}>
                        {d.deviceId}
                      </h3>
                      <p className="text-sm text-gray-400 font-medium">Room {d.roomId || "ΓÇö"}</p>
                    </div>
                    <div className="flex items-center gap-2">
                      <button
                        onClick={() => setDeleteConfirm(d.deviceId)}
                        className="p-2 -mr-2 -mt-2 rounded hover:bg-red-100/50 text-gray-400 hover:text-red-500 transition-colors min-h-[44px] min-w-[44px] flex items-center justify-center"
                        title="Delete device"
                      >
                        <TrashIcon className="w-4 h-4" />
                      </button>
                      <span className={`w-3.5 h-3.5 rounded-full shadow-[0_8px_32px_rgba(0,0,0,0.5)]-[0_4px_24px_rgba(0,0,0,0.4)] border border-white ${getStatusDotColor(d.status, d.rssi)}`} />
                    </div>
                  </div>
                  <div className="space-y-2 text-sm">
                    <div className="flex justify-between items-center">
                      <span className="text-gray-400">Status:</span>
                      <span className="font-semibold uppercase tracking-wider text-xs">{(d.rssi === -127 && d.status !== "breach") ? "offline" : (d.status || "ΓÇö")}</span>
                    </div>
                    <div className="flex justify-between items-center">
                      <span className="text-gray-400">Battery:</span>
                      <span className={getBatteryClass(d.battery)}>
                        {getBatteryText(d.battery)}
                      </span>
                    </div>
                    <div className="flex justify-between items-center">
                      <span className="text-gray-400">RSSI:</span>
                      <span className={getRssiClass(d.rssi)}>
                        {getRssiText(d.rssi)}
                      </span>
                    </div>
                    <div className="flex justify-between items-center">
                      <span className="text-gray-400">IP:</span>
                      <span className="font-mono text-xs">{d.ip || "ΓÇö"}</span>
                    </div>
                    <div className="flex justify-between items-center">
                      <span className="text-gray-400">Last Seen:</span>
                      <span className="text-xs text-gray-500">
                        {d.lastSeen ? formatISTTime(d.lastSeen) : "ΓÇö"}
                      </span>
                    </div>
                  </div>
                </div>
              ))}
          </div>
        </section>

        <section>
          <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center mb-4 gap-3">
            <h2 className="text-xl font-semibold">Recent Alerts</h2>
            <div className="flex flex-wrap gap-2 w-full sm:w-auto">
              {['all', 'breach', 'wifi', 'offline'].map(tab => (
                <button
                  key={tab}
                  onClick={() => { setAlertFilter(tab); setVisibleAlertsCount(50); }}
                  className={`px-3 py-1.5 rounded-full text-xs font-medium min-h-[44px] sm:min-h-0 flex-1 sm:flex-none transition-colors ${
                    alertFilter === tab 
                      ? 'bg-blue-600 text-white' 
                      : 'bg-[#141b2d]/80 backdrop-blur-xl border-gray-800 border text-gray-400 hover:bg-blue-600'
                  }`}
                >
                  {tab.charAt(0).toUpperCase() + tab.slice(1)}
                </button>
              ))}
            </div>
          </div>
          
          <div className="bg-[#141b2d]/80 backdrop-blur-xl border-gray-800 rounded-lg shadow-[0_8px_32px_rgba(0,0,0,0.5)] overflow-hidden">
            <div className="flex flex-col">
              {filteredAlerts.length === 0 && !isLoading && (
                <div className="text-center py-12 text-gray-500 bg-[#0a0f1e]/50">
                  No alerts matching your filter.
                </div>
              )}
              {filteredAlerts.slice(0, visibleAlertsCount).map((a: Alert, i: number) => {
                const isBreach = a.type === "breach";
                const isAcked = a.acknowledged;
                
                return (
                  <div
                    key={a.id ?? i}
                    className={`border-b border-gray-100 p-4 transition-colors flex flex-col sm:flex-row sm:justify-between sm:items-start gap-3 relative
                      ${isAcked ? "bg-[#141b2d]/80 backdrop-blur-xl border-gray-800 opacity-60" : isBreach ? "bg-red-900/20/40" : "bg-[#141b2d]/80 backdrop-blur-xl border-gray-800"}
                    `}
                    onClick={() => setSelectedAlert(a)}
                  >
                    <div className={`absolute left-0 top-0 bottom-0 w-1 ${isBreach ? 'bg-red-900/200' : 'bg-yellow-500'} ${isAcked ? 'opacity-30' : ''}`} />
                    
                    <div className="flex-1 pl-2">
                      <div className="flex flex-wrap items-center gap-2 mb-1">
                        <span className={`px-2 py-0.5 rounded text-xs font-bold uppercase tracking-wide ${
                            isBreach ? "bg-red-100 text-red-800" : "bg-yellow-100 text-yellow-800"
                        }`}>
                          {a.type}
                        </span>
                        <span className="text-sm font-bold text-gray-100">
                          {a.deviceId || (a.payload?.deviceId as string) || "Unknown"}
                        </span>
                        <span className="text-sm text-gray-500 font-medium">
                          Room {a.roomId || (a.payload?.roomId as string) || "ΓÇö"}
                        </span>
                        {isAcked && (
                          <span className="text-xs text-green-600 flex items-center gap-1 font-medium bg-green-900/20 px-2 py-0.5 rounded-full">
                            <CheckIcon className="w-3 h-3" /> Acknowledged
                          </span>
                        )}
                      </div>
                      
                      {a.message && (
                        <p className="text-sm text-gray-700 mt-1">{a.message}</p>
                      )}
                      
                      {a.payload && Object.keys(a.payload).length > 0 && !isAcked && (
                        <div className="mt-2 text-xs text-gray-500 bg-[#0a0f1e] p-2 rounded border border-gray-100 overflow-x-auto w-full">
                          {JSON.stringify(a.payload)}
                        </div>
                      )}
                    </div>
                    
                    <div className="flex flex-row sm:flex-col justify-between sm:items-end items-center sm:ml-4 w-full sm:w-auto pl-2 sm:pl-0 border-t sm:border-0 border-gray-100 pt-2 sm:pt-0 mt-2 sm:mt-0">
                      <div className="text-xs font-medium text-gray-400 whitespace-nowrap">
                        {formatISTTime(a.ts)}
                      </div>
                      
                      {!isAcked ? (
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            acknowledgeAlert(a);
                          }}
                          className="mt-0 sm:mt-2 text-xs font-semibold text-blue-600 hover:text-blue-800 hover:bg-blue-900/20 px-3 py-1.5 rounded transition-colors min-h-[44px] sm:min-h-0 flex items-center justify-center"
                        >
                          Acknowledge
                        </button>
                      ) : (
                        <div className="mt-0 sm:mt-2 text-xs text-gray-400 flex items-center gap-1 min-h-[44px] sm:min-h-0">
                          <CheckIcon className="w-3 h-3" /> Done
                        </div>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
            {filteredAlerts.length > visibleAlertsCount && (
              <div className="p-4 bg-[#0a0f1e] border-t text-center">
                <button
                  onClick={() => setVisibleAlertsCount(prev => prev + 50)}
                  className="text-sm font-medium text-gray-400 hover:text-gray-100 bg-[#141b2d]/80 backdrop-blur-xl border-gray-800 border border-gray-700 px-6 py-2 rounded-lg shadow-[0_8px_32px_rgba(0,0,0,0.5)]-[0_4px_24px_rgba(0,0,0,0.4)] w-full sm:w-auto min-h-[44px]"
                >
                  Load More Alerts
                </button>
              </div>
            )}
          </div>
        </section>

        {deleteConfirm && (
          <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50 p-4">
            <div className="bg-[#141b2d]/80 backdrop-blur-xl border-gray-800 rounded-xl shadow-[0_8px_32px_rgba(0,0,0,0.5)]-2xl p-6 max-w-sm w-full">
              <div className="flex items-center gap-3 text-red-600 mb-4">
                <TrashIcon className="w-6 h-6" />
                <h3 className="text-lg font-bold">Delete Device?</h3>
              </div>
              <p className="text-gray-400 text-sm mb-6 leading-relaxed">
                Are you sure you want to delete <strong className="text-gray-100">{deleteConfirm}</strong>?
                This will permanently remove the device and all associated alerts from the system. 
                This action cannot be undone.
              </p>
              <div className="flex gap-3 justify-end">
                <button
                  onClick={() => setDeleteConfirm(null)}
                  className="px-4 py-2 text-sm font-medium text-gray-400 hover:bg-blue-600 rounded-lg min-h-[44px] transition-colors flex-1 sm:flex-none"
                >
                  Cancel
                </button>
                <button
                  onClick={() => handleDeleteDevice(deleteConfirm)}
                  className="px-4 py-2 text-sm font-medium bg-red-600 hover:bg-red-700 text-white rounded-lg shadow-[0_8px_32px_rgba(0,0,0,0.5)]-[0_4px_24px_rgba(0,0,0,0.4)] min-h-[44px] transition-colors flex-1 sm:flex-none"
                >
                  Delete Device
                </button>
              </div>
            </div>
          </div>
        )}

        {selectedAlert && (
          <div
            className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50 p-4"
            onClick={() => setSelectedAlert(null)}
          >
            <div
              className="bg-[#141b2d]/80 backdrop-blur-xl border-gray-800 rounded-xl shadow-[0_8px_32px_rgba(0,0,0,0.5)]-2xl p-6 max-w-lg w-full"
              onClick={(e) => e.stopPropagation()}
            >
              <h3 className="text-lg font-bold mb-4">Alert Details</h3>
              <div className="text-xs bg-[#0a0f1e] p-4 rounded-lg overflow-auto max-h-[60vh] border border-gray-100 shadow-[0_8px_32px_rgba(0,0,0,0.5)]-inner">
                <pre>{JSON.stringify(selectedAlert, null, 2)}</pre>
              </div>
              <button
                onClick={() => setSelectedAlert(null)}
                className="mt-6 w-full px-4 py-2 bg-blue-600 text-white font-medium rounded-lg hover:bg-gray-900 min-h-[44px] transition-colors"
              >
                Close
              </button>
            </div>
          </div>
        )}
      </div>
    </main>
  );
}
