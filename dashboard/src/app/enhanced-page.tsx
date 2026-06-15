"use client";
import { useEffect, useState, useCallback, useRef } from "react"; // ← NEW: added useCallback and useRef
import { useWebSocket } from "../hooks/useWebSocket"; // ← NEW: our WebSocket hook
import LiveIndicator from "../components/LiveIndicator"; // ← NEW: live status badge

const API = process.env.NEXT_PUBLIC_API_URL || "https://hotel-backend-zqc1.onrender.com";
const DASHBOARD_VERSION = "v3.0-websocket-live";

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

// ← NEW: Toast notification shown when a breach fires
type Toast = {
  id: number;
  deviceId: string;
  roomId?: string;
  reason?: string;
};

export default function EnhancedDashboard() {
  const [devices, setDevices] = useState<Device[]>([]);
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [filter, setFilter] = useState<string>("all");
  const [searchQuery, setSearchQuery] = useState<string>("");
  const [selectedAlert, setSelectedAlert] = useState<Alert | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // ← NEW: Toast queue for breach notifications
  const [toasts, setToasts] = useState<Toast[]>([]);
  const toastIdCounter = useRef(0);

  // ← NEW: Connect WebSocket hook — this drives all live updates
  const { lastMessage, status: connectionStatus } = useWebSocket(API);

  // ← NEW: Helper to add a breach toast and auto-dismiss it after 8 seconds
  const addToast = useCallback((deviceId: string, roomId?: string, reason?: string) => {
    const id = ++toastIdCounter.current;
    setToasts((prev: Toast[]) => [...prev, { id, deviceId, roomId, reason }]);
    // ← NEW: Auto-remove toast after 8 seconds
    setTimeout(() => {
      setToasts((prev: Toast[]) => prev.filter((t: Toast) => t.id !== id));
    }, 8000);
  }, []);

  // ← NEW: React to every WebSocket message the server pushes
  useEffect(() => {
    if (!lastMessage) return;

    const { type, data } = lastMessage;
    const d = data as Record<string, unknown> | undefined;

    setTimeout(() => {
      switch (type) {
        case "device_update": {
          // ← NEW: Update single device in-place without re-fetching all devices
          if (!d?.deviceId) break;
          setDevices((prev: Device[]) => {
            const idx = prev.findIndex((dev: Device) => dev.deviceId === d.deviceId);
            const updated: Device = {
              ...(idx >= 0 ? prev[idx] : { deviceId: d.deviceId as string }),
              status: (d.status as string) ?? prev[idx]?.status,
              battery: d.battery !== undefined ? (d.battery as number) : prev[idx]?.battery,
              rssi: d.rssi !== undefined ? (d.rssi as number) : prev[idx]?.rssi,
              lastSeen: (d.lastSeen as string) ?? prev[idx]?.lastSeen,
            };
            if (idx >= 0) {
              const copy = [...prev];
              copy[idx] = updated;
              return copy;
            }
            return [...prev, updated];
          });
          break;
        }

        case "alert": {
          // ← NEW: Prepend the incoming alert to the list — no fetch needed
          if (!d?.type) break;
          const newAlert: Alert = {
            type: d.type as string,
            deviceId: d.deviceId as string,
            roomId: d.roomId as string | undefined,
            ts: (lastMessage.timestamp as string | undefined) ?? new Date().toISOString(),
            acknowledged: false,
            message: d.message as string | undefined,
          };
          setAlerts((prev: Alert[]) => [newAlert, ...prev].slice(0, 100));

          // ← NEW: Show red toast notification for breach events
          if (d.type === "breach") {
            addToast(d.deviceId as string, d.roomId as string | undefined, d.message as string | undefined);

            // ← NEW: Mark the device as breached instantly in the device grid
            setDevices((prev: Device[]) =>
              prev.map((dev: Device) =>
                dev.deviceId === d.deviceId ? { ...dev, status: "breach" } : dev
              )
            );
          }
          break;
        }

        case "device_recovered": {
          // ← NEW: Clear breach state when device comes back online
          if (!d?.deviceId) break;
          setDevices((prev: Device[]) =>
            prev.map((dev: Device) =>
              dev.deviceId === d.deviceId ? { ...dev, status: "ok" } : dev
            )
          );
          break;
        }

        case "device_offline":
        case "device_deleted": {
          // ← NEW: Remove or mark offline the device that disconnected
          if (!d?.deviceId) break;
          if (type === "device_deleted") {
            setDevices((prev: Device[]) => prev.filter((dev: Device) => dev.deviceId !== d.deviceId));
          } else {
            setDevices((prev: Device[]) =>
              prev.map((dev: Device) =>
                dev.deviceId === d.deviceId ? { ...dev, status: "offline" } : dev
              )
            );
          }
          break;
        }

        case "database_cleared": {
          // ← NEW: Wipe the UI when an admin clears the database
          setDevices([]);
          setAlerts([]);
          break;
        }
      }
    }, 0);
  }, [lastMessage, addToast]);

  // ── Initial data load + polling fallback ──────────────────────────────────
  // ← NEW: Fetch initial data once on mount so the dashboard isn't blank
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
        const [devicesRes, alertsRes] = await Promise.all([
          fetch(`${API}/api/devices`),
          fetch(`${API}/api/alerts/recent?limit=100`),
        ]);

        if (!devicesRes.ok || !alertsRes.ok) {
          setError(`API Error: ${devicesRes.status} / ${alertsRes.status}`);
          setIsLoading(false);
          return;
        }

        const d = await devicesRes.json();
        const a = await alertsRes.json();

        setDevices(Array.isArray(d) ? d.filter((dev: Device) => dev?.deviceId) : []);
        // ← FIXED: Explicit descending sort (newest first)
        setAlerts(Array.isArray(a) ? [...a].sort((a, b) => new Date(b.ts).getTime() - new Date(a.ts).getTime()).slice(0, 100) : []);
        setIsLoading(false);
      } catch (e) {
        setError(e instanceof Error ? e.message : "Failed to fetch data");
        setIsLoading(false);
      }
    };

    fetchAll();

    // ← NEW: Fallback polling every 10s in case WebSocket drops between retries
    const pollId = setInterval(fetchAll, 10000);
    return () => clearInterval(pollId);
  }, []);

  // ── Derived lists ──────────────────────────────────────────────────────────
  const filteredDevices = devices.filter((d: Device) => {
    if (!d?.deviceId) return false;
    if (filter !== "all" && d.status !== filter) return false;
    if (
      searchQuery &&
      !d.deviceId.toLowerCase().includes(searchQuery.toLowerCase()) &&
      !d.roomId?.toLowerCase().includes(searchQuery.toLowerCase())
    )
      return false;
    return true;
  });

  const acknowledgeAlert = async (alert: Alert) => {
    try {
      const deviceId = alert.deviceId ?? (alert.payload?.deviceId as string);
      if (!deviceId) return;
      await fetch(`${API}/api/alerts/acknowledge`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
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

  const getStatusColor = (status?: string) => {
    switch (status) {
      case "ok": return "bg-green-500";
      case "breach": return "bg-red-500 animate-pulse";
      case "offline": return "bg-gray-400";
      case "missing": return "bg-yellow-500";
      default: return "bg-gray-400";
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
      {/* ← NEW: Toast container — stacks in bottom-right, auto-dismisses */}
      <div className="fixed bottom-4 right-4 z-50 flex flex-col gap-2">
        {toasts.map((toast: Toast) => (
          <div
            key={toast.id}
            className="bg-red-600 text-white px-5 py-3 rounded-lg shadow-xl flex items-start gap-3 max-w-sm animate-bounce"
          >
            <span className="text-2xl">🚨</span>
            <div>
              <p className="font-bold text-sm">BREACH DETECTED</p>
              <p className="text-xs mt-0.5">Device: {toast.deviceId}</p>
              {toast.roomId && <p className="text-xs">Room: {toast.roomId}</p>}
              {toast.reason && <p className="text-xs opacity-80 mt-1">{toast.reason}</p>}
            </div>
            {/* ← NEW: Manual dismiss button */}
            <button
              onClick={() => setToasts((prev: Toast[]) => prev.filter((t: Toast) => t.id !== toast.id))}
              className="ml-auto text-white opacity-70 hover:opacity-100 text-lg leading-none"
            >
              ×
            </button>
          </div>
        ))}
      </div>

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

        {/* Loading */}
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
              {devices.length} devices •{" "}
              {alerts.filter((a) => a && !a.acknowledged).length} unacknowledged alerts
              <span className="ml-3 text-xs text-gray-400">{DASHBOARD_VERSION}</span>
            </p>
          </div>
          {/* ← NEW: LiveIndicator replaces the old hardcoded green dot */}
          <LiveIndicator status={connectionStatus} />
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
            <option value="offline">Offline</option>
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
            {filteredDevices
              .filter((d: Device) => d?.deviceId)
              .map((d: Device) => (
                <div
                  key={d.deviceId}
                  className={`bg-white rounded-lg shadow-md p-5 hover:shadow-lg transition-shadow ${
                    d.status === "breach" ? "border-2 border-red-400" : ""
                  }`}
                >
                  <div className="flex justify-between items-start mb-3">
                    <div>
                      <h3 className="font-bold text-lg">{d.deviceId}</h3>
                      <p className="text-sm text-gray-600">Room {d.roomId || "—"}</p>
                    </div>
                    <span className={`w-4 h-4 rounded-full ${getStatusColor(d.status)}`} />
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
                  No alerts yet. Breaches will appear here instantly.
                </div>
              )}
              {alerts
                .filter((a: Alert) => a?.type)
                .map((a: Alert, i: number) => (
                  <div
                    key={a.id ?? i}
                    className={`border-b p-4 hover:bg-gray-50 cursor-pointer ${
                      a.acknowledged ? "opacity-50" : ""
                    } ${a.type === "breach" && !a.acknowledged ? "bg-red-50" : ""}`}
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
                            {a.deviceId || (a.payload?.deviceId as string) || "Unknown"} •{" "}
                            Room {a.roomId || (a.payload?.roomId as string) || "Unknown"}
                          </span>
                          {a.acknowledged && (
                            <span className="text-xs text-green-600">✓ Acknowledged</span>
                          )}
                        </div>
                        {a.message && (
                          <p className="mt-1 text-xs text-gray-600">{a.message}</p>
                        )}
                        {a.payload && Object.keys(a.payload).length > 0 && (
                          <pre className="mt-2 text-xs text-gray-600 overflow-auto">
                            {JSON.stringify(a.payload, null, 2)}
                          </pre>
                        )}
                      </div>
                      <div className="text-right ml-4">
                        <div className="text-xs text-gray-500">{formatISTTime(a.ts)}</div>
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

        {/* Selected Alert Modal */}
        {selectedAlert && (
          <div
            className="fixed inset-0 bg-black bg-opacity-40 flex items-center justify-center z-50"
            onClick={() => setSelectedAlert(null)}
          >
            <div
              className="bg-white rounded-xl shadow-2xl p-6 max-w-lg w-full mx-4"
              onClick={(e) => e.stopPropagation()}
            >
              <h3 className="text-lg font-bold mb-4">Alert Details</h3>
              <pre className="text-xs bg-gray-50 p-4 rounded overflow-auto max-h-80">
                {JSON.stringify(selectedAlert, null, 2)}
              </pre>
              <button
                onClick={() => setSelectedAlert(null)}
                className="mt-4 px-4 py-2 bg-gray-800 text-white rounded hover:bg-gray-700"
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
