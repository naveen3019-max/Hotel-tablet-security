"use client";
import { useEffect, useState } from "react";

const API = process.env.NEXT_PUBLIC_API_URL || "https://hotel-backend-zqc1.onrender.com";
const DASHBOARD_VERSION = "v2.9-force-redeploy"; // Force Vercel to redeploy with correct timestamp fix

// Format timestamp to Indian Standard Time
const formatISTTime = (dateString: string): string => {
  try {
    // Backend sends IST timestamps like: 2026-02-13T16:58:10.033921+05:30
    // Parse the timestamp directly without timezone conversion
    const match = dateString.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})/);
    if (!match) {
      return dateString;
    }
    
    const [_, year, month, day, hour24, minute, second] = match;
    let hour = parseInt(hour24);
    const ampm = hour >= 12 ? 'PM' : 'AM';
    hour = hour % 12 || 12; // Convert to 12-hour format
    
    return `${day}/${month}/${year} ${String(hour).padStart(2, '0')}:${minute}:${second} ${ampm} IST`;
  } catch (e) {
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
  type: string;
  deviceId?: string;
  roomId?: string;
  payload?: any;
  ts: string;
  acknowledged?: boolean;
  notes?: string;
};

export default function EnhancedDashboard() {
  const [devices, setDevices] = useState<Device[]>([]);
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [filter, setFilter] = useState<string>("all");
  const [searchQuery, setSearchQuery] = useState<string>("");
  const [selectedAlert, setSelectedAlert] = useState<Alert | null>(null);
  const [eventSource, setEventSource] = useState<EventSource | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Initialize SSE connection for real-time updates
  useEffect(() => {
    if (!API) {
      setError("API URL not configured");
      setIsLoading(false);
      return;
    }
    
    // Try SSE, fallback to polling
    try {
      const es = new EventSource(`${API}/api/events`);
      
      es.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          if (data.type === "device_update" && data.device && data.device.deviceId) {
            setDevices((prev) => {
              const idx = prev.findIndex((d) => d && d.deviceId === data.device.deviceId);
              if (idx >= 0) {
                const updated = [...prev];
                updated[idx] = data.device;
                return updated;
              }
              return [...prev, data.device];
            });
          } else if (data.type === "alert" && data.alert) {
            setAlerts((prev) => [data.alert, ...prev].slice(0, 100));
          }
        } catch (err) {
          console.error("Error parsing SSE message", err);
        }
      };
      
      es.onerror = () => {
        console.error("SSE connection failed, falling back to polling");
        es.close();
        setEventSource(null);
      };
      
      setEventSource(es);
      
      return () => es.close();
    } catch (e) {
      console.warn("SSE not available, using polling");
    }
  }, []);

  // Polling fallback
  useEffect(() => {
    if (eventSource) return; // SSE active, skip polling
    if (!API) return; // No API configured
    
    const tick = async () => {
      try {
        setIsLoading(true);
        setError(null);
        
        const [devicesRes, alertsRes] = await Promise.all([
          fetch(`${API}/api/devices`),
          fetch(`${API}/api/alerts/recent?limit=100`)
        ]);
        
        if (!devicesRes.ok || !alertsRes.ok) {
          console.error("API request failed", devicesRes.status, alertsRes.status);
          setError(`API Error: ${devicesRes.status} / ${alertsRes.status}`);
          setIsLoading(false);
          return;
        }
        
        const d = await devicesRes.json();
        const a = await alertsRes.json();
        
        // Ensure d is an array and filter out any undefined/null values
        const validDevices = Array.isArray(d) ? d.filter(device => device && device.deviceId) : [];
        const validAlerts = Array.isArray(a) ? a.filter(alert => alert) : [];
        
        setDevices(validDevices);
        setAlerts(validAlerts.reverse());
        setIsLoading(false);
        setError(null);
      } catch (e) {
        console.error("Failed to fetch data", e);
        setError(e instanceof Error ? e.message : "Failed to fetch data");
        setIsLoading(false);
      }
    };
    
    tick();
    const id = setInterval(tick, 3000);
    return () => clearInterval(id);
  }, [eventSource]);

  const filteredDevices = devices.filter((d) => {
    if (!d || !d.deviceId) return false; // Safety check
    if (filter !== "all" && d.status !== filter) return false;
    if (searchQuery && !d.deviceId.toLowerCase().includes(searchQuery.toLowerCase()) 
        && !d.roomId?.toLowerCase().includes(searchQuery.toLowerCase())) return false;
    return true;
  });

  const acknowledgeAlert = async (alert: Alert) => {
    try {
      const deviceId = alert.deviceId || alert.payload?.deviceId;
      if (!deviceId) {
        console.error("Alert missing deviceId", alert);
        return;
      }
      
      await fetch(`${API}/api/alerts/acknowledge`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          device_id: deviceId,
          timestamp: alert.ts,
          notes: "Acknowledged from dashboard"
        })
      });
      
      setAlerts((prev) =>
        prev.map((a) => (a === alert ? { ...a, acknowledged: true } : a))
      );
    } catch (e) {
      console.error("Failed to acknowledge alert", e);
    }
  };

  const getStatusColor = (status?: string) => {
    switch (status) {
      case "ok": return "bg-green-500";
      case "breach": return "bg-red-500 animate-pulse";
      case "missing": return "bg-yellow-500";
      default: return "bg-gray-500";
    }
  };

  const getBatteryColor = (battery?: number) => {
    if (!battery) return "text-gray-500";
    if (battery > 50) return "text-green-600";
    if (battery > 20) return "text-yellow-600";
    return "text-red-600 font-bold";
  };

  const getRssiColor = (rssi?: number) => {
    if (!rssi) return "text-gray-500";
    if (rssi > -60) return "text-green-600";
    if (rssi > -70) return "text-yellow-600";
    return "text-red-600";
  };

  return (
    <main className="min-h-screen bg-gray-50 p-6">
      <div className="max-w-7xl mx-auto space-y-6">
        {/* Error Display */}
        {error && (
          <div className="bg-red-50 border-2 border-red-200 rounded-lg p-4">
            <div className="flex items-center gap-2">
              <span className="text-red-600 font-semibold">⚠️ Error:</span>
              <span className="text-red-700">{error}</span>
            </div>
            <div className="mt-2 text-sm text-red-600">
              <p>Backend URL: {API || "Not configured"}</p>
            </div>
          </div>
        )}
        
        {/* Loading Display */}
        {isLoading && devices.length === 0 && (
          <div className="bg-blue-50 border-2 border-blue-200 rounded-lg p-4 text-center">
            <div className="text-blue-600 font-semibold">🔄 Loading dashboard...</div>
          </div>
        )}
        
        {/* Header */}
        <div className="flex justify-between items-center">
          <div>
            <h1 className="text-3xl font-bold text-gray-900">Hotel Tablet Security</h1>
            <p className="text-gray-600">
              {devices.length} devices • {alerts.filter((a) => a && !a.acknowledged).length} unacknowledged alerts
              <span className="ml-3 text-xs text-gray-400">{DASHBOARD_VERSION}</span>
            </p>
          </div>
          <div className="flex items-center gap-2">
            <span className="w-3 h-3 bg-green-500 rounded-full animate-pulse"></span>
            <span className="text-sm text-gray-600">
              {eventSource ? "Live (SSE)" : "Polling"}
            </span>
          </div>
        </div>

        {/* Controls */}
        <div className="bg-white rounded-lg shadow p-4 flex gap-4">
          <input
            type="text"
            placeholder="Search devices or rooms..."
            className="flex-1 px-4 py-2 border rounded-lg"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
          <select
            className="px-4 py-2 border rounded-lg"
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
          >
            <option value="all">All Devices</option>
            <option value="ok">OK</option>
            <option value="breach">Breach</option>
            <option value="missing">Missing</option>
          </select>
        </div>

        {/* Fleet Grid */}
        <section>
          <h2 className="text-xl font-semibold mb-4">Fleet Status</h2>
          <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-4">
            {filteredDevices.length === 0 && !isLoading && (
              <div className="col-span-full text-center py-8 text-gray-500">
                No devices found. Register a device using the Android app.
              </div>
            )}
            {filteredDevices.filter(d => d && d.deviceId).map((d) => (
              <div
                key={d.deviceId}
                className="bg-white rounded-lg shadow-md p-5 hover:shadow-lg transition-shadow"
              >
                <div className="flex justify-between items-start mb-3">
                  <div>
                    <h3 className="font-bold text-lg">{d.deviceId}</h3>
                    <p className="text-sm text-gray-600">Room {d.roomId || "—"}</p>
                  </div>
                  <span className={`w-4 h-4 rounded-full ${getStatusColor(d.status)}`}></span>
                </div>
                
                <div className="space-y-2 text-sm">
                  <div className="flex justify-between">
                    <span className="text-gray-600">Status:</span>
                    <span className="font-semibold">{d.status || "—"}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-gray-600">Battery:</span>
                    <span className={getBatteryColor(d.battery)}>
                      {d.battery ? `${d.battery}%` : "—"}
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-gray-600">RSSI:</span>
                    <span className={getRssiColor(d.rssi)}>
                      {d.rssi ? `${d.rssi} dBm` : "—"}
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-gray-600">IP:</span>
                    <span className="font-mono text-xs">{d.ip || "—"}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-gray-600">Last Seen:</span>
                    <span className="text-xs">
                      {d.lastSeen ? formatISTTime(d.lastSeen) : "—"}
                    </span>
                  </div>
                  {d.lastSeen && (
                    <div className="flex justify-between text-xs text-gray-400">
                      <span>Raw:</span>
                      <span className="font-mono truncate ml-2" title={d.lastSeen}>
                        {d.lastSeen.substring(0, 19)}
                      </span>
                    </div>
                  )}
                </div>
              </div>
            ))}
          </div>
          {filteredDevices.length === 0 && (
            <div className="text-center text-gray-500 py-12">
              No devices found matching your criteria
            </div>
          )}
        </section>

        {/* Recent Alerts */}
        <section>
          <h2 className="text-xl font-semibold mb-4">Recent Alerts</h2>
          <div className="bg-white rounded-lg shadow overflow-hidden">
            <div className="max-h-96 overflow-y-auto">
              {alerts.length === 0 && !isLoading && (
                <div className="text-center py-8 text-gray-500">
                  No alerts yet. Breaches will appear here.
                </div>
              )}
              {alerts.filter(a => a && a.type).map((a, i) => (
                <div
                  key={i}
                  className={`border-b p-4 hover:bg-gray-50 cursor-pointer ${
                    a.acknowledged ? "opacity-50" : ""
                  }`}
                  onClick={() => setSelectedAlert(a)}
                >
                  <div className="flex justify-between items-start">
                    <div className="flex-1">
                      <div className="flex items-center gap-2">
                        <span
                          className={`px-2 py-1 rounded text-xs font-semibold ${
                            a.type === "breach"
                              ? "bg-red-100 text-red-800"
                              : "bg-yellow-100 text-yellow-800"
                          }`}
                        >
                          {a.type}
                        </span>
                        <span className="text-sm font-medium">
                          {a.deviceId || a.payload?.deviceId || 'Unknown'} • Room {a.roomId || a.payload?.roomId || 'Unknown'}
                        </span>
                        {a.acknowledged && (
                          <span className="text-xs text-green-600">✓ Acknowledged</span>
                        )}
                      </div>
                      <pre className="mt-2 text-xs text-gray-600 overflow-auto">
                        {JSON.stringify(a.payload || {}, null, 2)}
                      </pre>
                    </div>
                    <div className="text-right ml-4">
                      <div className="text-xs text-gray-500">
                        {formatISTTime(a.ts)}
                      </div>
                      {!a.acknowledged && (
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            acknowledgeAlert(a);
                          }}
                          className="mt-2 text-xs text-blue-600 hover:underline"
                        >
                          Acknowledge
                        </button>
                      )}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </section>
      </div>
    </main>
  );
}
