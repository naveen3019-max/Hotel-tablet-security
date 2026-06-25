"use client";
import { useEffect, useState, useCallback, useRef } from "react";
import { useRouter } from "next/navigation";
import { useWebSocket } from "../hooks/useWebSocket";
import { useAuth } from "../hooks/useAuth";

const API =
  process.env.NEXT_PUBLIC_API_URL || "https://hotel-backend-zqc1.onrender.com";

// ─── TYPES ────────────────────────────────────────────────────────────────────
type Device = {
  deviceId: string;
  roomId?: string;
  status?: string;
  battery?: number;
  rssi?: number;
  lastSeen?: string;
  ip?: string;
  registeredBy?: string;
  staffName?: string;
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

// ─── HELPERS ──────────────────────────────────────────────────────────────────
const formatISTTime = (dateString: string): string => {
  try {
    const match = dateString.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})/);
    if (!match) return dateString;
    const [, year, month, day, hour24, minute, second] = match;
    let hour = parseInt(hour24);
    const ampm = hour >= 12 ? "PM" : "AM";
    hour = hour % 12 || 12;
    return `${day}/${month}/${year} ${String(hour).padStart(2,"0")}:${minute}:${second} ${ampm} IST`;
  } catch {
    return dateString;
  }
};

const timeAgo = (dateString?: string): { text: string; level: "ok" | "warn" | "danger" } => {
  if (!dateString) return { text: "—", level: "ok" };
  const diff = (Date.now() - new Date(dateString).getTime()) / 1000;
  if (diff < 60)    return { text: `${Math.round(diff)}s ago`,    level: "ok" };
  if (diff < 300)   return { text: `${Math.round(diff/60)}m ago`,  level: "ok" };
  if (diff < 1800)  return { text: `${Math.round(diff/60)}m ago`,  level: "warn" };
  return { text: `${Math.round(diff/3600)}h ago`, level: "danger" };
};

const isDeviceOffline = (d: Device): boolean => {
  if (d.status === "offline" || d.rssi === -127) return true;
  if (d.lastSeen) {
    const diff = (Date.now() - new Date(d.lastSeen).getTime()) / 1000;
    if (diff > 7200) return true; // 2 hours
  }
  return false;
};

const getBatteryColor = (b?: number) => {
  if (b === undefined) return "#475569";
  if (b > 50) return "#22c55e";
  if (b > 20) return "#f59e0b";
  return "#ef4444";
};

const getRssiColor = (r?: number) => {
  if (r === undefined || r === -127) return "#475569";
  if (r >= -60) return "#22c55e";
  if (r >= -75) return "#f59e0b";
  return "#ef4444";
};

const getSignalBars = (r?: number): number => {
  if (r === undefined || r === -127) return 0;
  if (r >= -55) return 4;
  if (r >= -65) return 3;
  if (r >= -75) return 2;
  return 1;
};

// ─── INLINE SVG ICONS ─────────────────────────────────────────────────────────
const ShieldIcon = ({ size = 20, color = "#ef4444" }: { size?: number; color?: string }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none">
    <path d="M12 2L3 7v5c0 5.25 3.75 10.15 9 11.25C17.25 22.15 21 17.25 21 12V7L12 2z" fill={color} fillOpacity="0.9"/>
    <path d="M9 12l2 2 4-4" stroke="#fff" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
  </svg>
);

const TrashIcon = ({ size = 16 }: { size?: number }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M3 6h18M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/>
  </svg>
);

const CheckIcon = ({ size = 14 }: { size?: number }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="20 6 9 17 4 12"/>
  </svg>
);

const SearchIcon = () => (
  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="#475569" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/>
  </svg>
);

const LogoutIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
    <path d="M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4M16 17l5-5-5-5M21 12H9"/>
  </svg>
);

const BatteryIcon = ({ level = 100, color = "#22c55e" }: { level?: number; color?: string }) => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
    <rect x="2" y="7" width="18" height="10" rx="2"/>
    <line x1="22" y1="11" x2="22" y2="13"/>
    <rect x="4" y="9" width={Math.round(14 * (level / 100))} height="6" rx="1" fill={color} stroke="none"/>
  </svg>
);

const WifiIcon = ({ bars = 4, color = "#22c55e" }: { bars?: number; color?: string }) => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
    {bars >= 1 && <path d="M12 19h.01" stroke={color} strokeWidth="2.5" strokeLinecap="round"/>}
    {bars >= 2 && <path d="M9.17 16.83a4 4 0 015.66 0" stroke={bars >= 2 ? color : "#1e2a45"} strokeWidth="1.5" strokeLinecap="round"/>}
    {bars >= 3 && <path d="M6.35 14.5a8 8 0 0111.31 0" stroke={bars >= 3 ? color : "#1e2a45"} strokeWidth="1.5" strokeLinecap="round"/>}
    {bars >= 4 && <path d="M3.52 12.17A12 12 0 0120.48 12.17" stroke={bars >= 4 ? color : "#1e2a45"} strokeWidth="1.5" strokeLinecap="round"/>}
  </svg>
);

const ClockIcon = ({ color = "#94a3b8" }: { color?: string }) => (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>
  </svg>
);

const TabletIcon = () => (
  <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#3b82f6" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
    <rect x="4" y="2" width="16" height="20" rx="2"/><line x1="12" y1="18" x2="12.01" y2="18"/>
  </svg>
);

const AlertTriangleIcon = ({ color = "#ef4444", size = 22 }: { color?: string; size?: number }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
    <path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/>
    <line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/>
  </svg>
);

const WifiOffIcon = ({ color = "#f59e0b", size = 22 }: { color?: string; size?: number }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
    <line x1="1" y1="1" x2="23" y2="23"/>
    <path d="M16.72 11.06A10.94 10.94 0 0119 12.55M5 12.55a10.94 10.94 0 015.17-2.39M10.71 5.05A16 16 0 0122.56 9M1.42 9a15.91 15.91 0 014.7-2.88M8.53 16.11a6 6 0 016.95 0M12 20h.01"/>
  </svg>
);

// ─── LIVE CLOCK ───────────────────────────────────────────────────────────────
function LiveClock() {
  const [time, setTime] = useState("");

  useEffect(() => {
    const update = () => {
      const now = new Date();
      const options: Intl.DateTimeFormatOptions = {
        timeZone: "Asia/Kolkata",
        day: "2-digit", month: "short", year: "numeric",
        hour: "2-digit", minute: "2-digit", second: "2-digit",
        hour12: false,
      };
      const parts = new Intl.DateTimeFormat("en-IN", options).formatToParts(now);
      const get = (t: string) => parts.find(p => p.type === t)?.value ?? "";
      setTime(`${get("day")} ${get("month").toUpperCase()} ${get("year")} • ${get("hour")}:${get("minute")}:${get("second")} IST`);
    };
    update();
    const id = setInterval(update, 1000);
    return () => clearInterval(id);
  }, []);

  return (
    <span
      style={{
        fontFamily: "monospace",
        fontSize: 13,
        color: "#94a3b8",
        letterSpacing: "0.5px",
      }}
    >
      {time}
    </span>
  );
}

// ─── STAT CARD ────────────────────────────────────────────────────────────────
function StatCard({
  icon, value, label, sub, accent, glow, pulseBorder,
}: {
  icon: React.ReactNode;
  value: number;
  label: string;
  sub: string;
  accent: string;
  glow: string;
  pulseBorder?: boolean;
}) {
  const prevRef = useRef(value);
  const [pop, setPop] = useState(false);

  useEffect(() => {
    if (prevRef.current !== value) {
      setTimeout(() => setPop(true), 0);
      const id = setTimeout(() => setPop(false), 400);
      prevRef.current = value;
      return () => clearTimeout(id);
    }
  }, [value]);

  return (
    <div
      className={pulseBorder && value > 0 ? "animate-breach-pulse" : "card-hover"}
      style={{
        background: "#141b2d",
        border: `1px solid ${pulseBorder && value > 0 ? "rgba(239,68,68,0.5)" : "#1e2a45"}`,
        borderRadius: 16,
        padding: 24,
        display: "flex",
        flexDirection: "column",
        gap: 12,
        boxShadow: pulseBorder && value > 0 ? `0 0 20px ${glow}` : "none",
        transition: "box-shadow 0.3s",
      }}
    >
      <div
        style={{
          width: 48,
          height: 48,
          borderRadius: 12,
          background: glow,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          border: `1px solid ${accent}22`,
        }}
      >
        {icon}
      </div>
      <div>
        <div
          className={pop ? "animate-stat-pop" : ""}
          style={{
            fontSize: 36,
            fontWeight: 700,
            color: value > 0 && pulseBorder ? accent : "#f1f5f9",
            lineHeight: 1,
            display: "inline-block",
          }}
        >
          {value}
        </div>
        <div style={{ fontSize: 13, color: "#94a3b8", marginTop: 4 }}>{label}</div>
      </div>
      <div style={{ fontSize: 11, color: "#475569" }}>{sub}</div>
    </div>
  );
}

// ─── DEVICE CARD ──────────────────────────────────────────────────────────────
function DeviceCard({
  d,
  onDelete,
}: {
  d: Device;
  onDelete: (id: string) => void;
}) {
  const isOffline = isDeviceOffline(d);
  const isBreach  = d.status === "breach" && !isOffline;
  const isOk      = !isBreach && !isOffline;

  const borderColor  = isBreach ? "#ef4444" : isOffline ? "#f59e0b" : "#22c55e";
  const statusLabel  = isBreach ? "BREACH" : isOffline ? "OFFLINE" : "SECURE";
  const statusColor  = isBreach ? "#ef4444" : isOffline ? "#f59e0b" : "#22c55e";

  const battColor = getBatteryColor(d.battery);
  const rssiColor = getRssiColor(d.rssi);
  const bars      = getSignalBars(d.rssi);
  const ago       = timeAgo(d.lastSeen);

  return (
    <div
      className={isBreach ? "animate-breach-pulse" : "card-hover"}
      style={{
        background: isBreach ? "rgba(239,68,68,0.05)" : "#141b2d",
        border: isBreach
          ? "1px solid rgba(239,68,68,0.3)"
          : `1px solid #1e2a45`,
        borderLeft: `3px solid ${borderColor}`,
        borderRadius: 16,
        padding: 20,
        position: "relative",
        transition: "border-color 0.2s, transform 0.2s",
      }}
    >
      {/* Top row */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 16 }}>
        <div style={{ flex: 1 }}>
          <div style={{ fontFamily: "monospace", fontWeight: 700, fontSize: 14, color: "#f1f5f9", marginBottom: 2 }}>
            {d.deviceId}
          </div>
          <div style={{ fontSize: 12, color: "#94a3b8" }}>
            Room {d.roomId || "—"}
          </div>
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 5 }}>
            <span
              className={isBreach ? "animate-dot-fast" : isOk ? "animate-dot" : ""}
              style={{
                width: 8,
                height: 8,
                borderRadius: "50%",
                background: statusColor,
                display: "inline-block",
                boxShadow: `0 0 6px ${statusColor}`,
              }}
            />
            <span style={{ fontSize: 10, fontWeight: 600, letterSpacing: "1px", color: statusColor }}>
              {statusLabel}
            </span>
          </div>
          <button
            onClick={() => onDelete(d.deviceId)}
            style={{
              background: "none",
              border: "none",
              cursor: "pointer",
              color: "#475569",
              padding: 4,
              borderRadius: 6,
              display: "flex",
              alignItems: "center",
              transition: "color 0.2s",
            }}
            onMouseEnter={(e) => (e.currentTarget.style.color = "#ef4444")}
            onMouseLeave={(e) => (e.currentTarget.style.color = "#475569")}
            title="Delete device"
          >
            <TrashIcon />
          </button>
        </div>
      </div>

      {/* Metrics */}
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 8, marginBottom: 12 }}>
        {/* Battery */}
        <div style={{ textAlign: "center", padding: "8px 4px", background: "rgba(255,255,255,0.02)", borderRadius: 8 }}>
          <div style={{ display: "flex", justifyContent: "center", marginBottom: 4 }}>
            <BatteryIcon level={d.battery ?? 100} color={battColor} />
          </div>
          <div style={{ fontSize: 13, fontWeight: 700, color: battColor }}>
            {d.battery !== undefined ? `${d.battery}%` : "—"}
            {d.battery !== undefined && d.battery < 20 && " ⚠️"}
          </div>
          <div style={{ fontSize: 10, color: "#475569", marginTop: 2 }}>Battery</div>
        </div>

        {/* Signal */}
        <div style={{ textAlign: "center", padding: "8px 4px", background: "rgba(255,255,255,0.02)", borderRadius: 8 }}>
          <div style={{ display: "flex", justifyContent: "center", marginBottom: 4 }}>
            <WifiIcon bars={bars} color={rssiColor} />
          </div>
          <div style={{ fontSize: 13, fontWeight: 700, color: rssiColor }}>
            {d.rssi === undefined ? "—" : d.rssi === -127 ? "None" : `${d.rssi}`}
          </div>
          <div style={{ fontSize: 10, color: "#475569", marginTop: 2 }}>dBm</div>
        </div>

        {/* Last seen */}
        <div style={{ textAlign: "center", padding: "8px 4px", background: "rgba(255,255,255,0.02)", borderRadius: 8 }}>
          <div style={{ display: "flex", justifyContent: "center", marginBottom: 4 }}>
            <ClockIcon color={ago.level === "ok" ? "#22c55e" : ago.level === "warn" ? "#f59e0b" : "#ef4444"} />
          </div>
          <div style={{
            fontSize: 11,
            fontWeight: 700,
            color: ago.level === "ok" ? "#22c55e" : ago.level === "warn" ? "#f59e0b" : "#ef4444",
          }}>
            {ago.text}
          </div>
          <div style={{ fontSize: 10, color: "#475569", marginTop: 2 }}>Last seen</div>
        </div>
      </div>

      {/* Footer */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        {(d.registeredBy || d.staffName) && (
          <span style={{ fontSize: 11, color: "#475569" }}>
            By: {d.staffName || d.registeredBy}
          </span>
        )}
        {d.ip && (
          <span style={{ fontFamily: "monospace", fontSize: 10, color: "#475569", marginLeft: "auto" }}>
            {d.ip}
          </span>
        )}
      </div>
    </div>
  );
}

// ─── ALERT ITEM ───────────────────────────────────────────────────────────────
function AlertItem({
  a,
  onAcknowledge,
  onClick,
}: {
  a: Alert;
  onAcknowledge: (a: Alert) => void;
  onClick: (a: Alert) => void;
}) {
  const isBreach  = a.type === "breach";
  const isOffline = a.type === "offline";
  const isAcked   = a.acknowledged;

  const accent = isBreach ? "#ef4444" : isOffline ? "#f59e0b" : "#3b82f6";
  const glow   = isBreach ? "rgba(239,68,68,0.08)" : isOffline ? "rgba(245,158,11,0.08)" : "rgba(59,130,246,0.08)";
  const label  = isBreach ? "🚨 BREACH DETECTED" : isOffline ? "DEVICE OFFLINE" : "ALERT";

  return (
    <div
      className="animate-slide-in"
      onClick={() => onClick(a)}
      style={{
        background: isAcked ? "transparent" : glow,
        border: `1px solid ${isAcked ? "#1e2a45" : accent + "33"}`,
        borderLeft: `3px solid ${isAcked ? "#1e2a45" : accent}`,
        borderRadius: 12,
        padding: 16,
        marginBottom: 8,
        opacity: isAcked ? 0.5 : 1,
        cursor: "pointer",
        transition: "opacity 0.2s, border-color 0.2s",
      }}
    >
      <div style={{ display: "flex", gap: 12, alignItems: "flex-start" }}>
        {/* Icon */}
        <div
          style={{
            width: 36,
            height: 36,
            borderRadius: "50%",
            background: `${accent}22`,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            flexShrink: 0,
          }}
        >
          {isBreach
            ? <AlertTriangleIcon color={accent} size={18} />
            : <WifiOffIcon color={accent} size={18} />}
        </div>

        {/* Content */}
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontSize: 11, fontWeight: 700, color: accent, letterSpacing: "1px", marginBottom: 3 }}>
            {label}
          </div>
          <div style={{ fontSize: 14, fontWeight: 600, color: "#f1f5f9", marginBottom: 2, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
            {a.deviceId || "Unknown"} {a.roomId ? `• Room ${a.roomId}` : ""}
          </div>
          {a.message && (
            <div style={{ fontSize: 12, color: "#94a3b8", marginBottom: 4 }}>{a.message}</div>
          )}
          <div style={{ fontSize: 11, color: "#475569" }}>{formatISTTime(a.ts)}</div>
        </div>

        {/* Ack button */}
        <div style={{ flexShrink: 0 }}>
          {!isAcked ? (
            <button
              onClick={(e) => { e.stopPropagation(); onAcknowledge(a); }}
              style={{
                background: "none",
                border: "1px solid #475569",
                borderRadius: 8,
                padding: "5px 10px",
                color: "#94a3b8",
                fontSize: 11,
                cursor: "pointer",
                whiteSpace: "nowrap",
                transition: "border-color 0.2s, color 0.2s",
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.borderColor = "#22c55e";
                e.currentTarget.style.color = "#22c55e";
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.borderColor = "#475569";
                e.currentTarget.style.color = "#94a3b8";
              }}
            >
              ✓ Ack
            </button>
          ) : (
            <span style={{ fontSize: 11, color: "#22c55e", display: "flex", alignItems: "center", gap: 4 }}>
              <CheckIcon /> Done
            </span>
          )}
        </div>
      </div>
    </div>
  );
}

// ─── MAIN DASHBOARD ───────────────────────────────────────────────────────────
export default function Dashboard() {
  const { isAuthenticated, checking, user } = useAuth();
  const router = useRouter();

  const [devices, setDevices]         = useState<Device[]>([]);
  const [alerts, setAlerts]           = useState<Alert[]>([]);
  const [filter, setFilter]           = useState<string>("all");
  const [searchQuery, setSearchQuery] = useState<string>("");
  const [selectedAlert, setSelectedAlert] = useState<Alert | null>(null);
  const [isLoading, setIsLoading]     = useState<boolean>(true);
  const [error, setError]             = useState<string | null>(null);
  const [toasts, setToasts]           = useState<Toast[]>([]);
  const [deleteConfirm, setDeleteConfirm] = useState<string | null>(null);
  const [alertFilter, setAlertFilter] = useState<string>("all");
  const [visibleAlertsCount, setVisibleAlertsCount] = useState<number>(50);
  const [sessionCount, setSessionCount] = useState<number>(0);

  const toastIdCounter = useRef(0);
  const { lastMessage, status: connectionStatus } = useWebSocket(API, user?.token || "");

  const handleLogout = async () => {
    // ← NEW: Tell backend to delete session
    const token = localStorage.getItem("dashboard_token");
    if (token) {
      await fetch(`${API}/api/auth/logout`, {
        method: "POST",
        headers: { "Authorization": `Bearer ${token}` }
      }).catch(() => {});
    }
    
    // Clear localStorage
    localStorage.removeItem("dashboard_token");
    localStorage.removeItem("dashboard_username");
    localStorage.removeItem("dashboard_role");
    localStorage.removeItem("dashboard_hotel_id");
    localStorage.removeItem("dashboard_hotel_name");
    localStorage.removeItem("dashboard_name");
    
    router.push("/login");
  };

  const addToast = useCallback((deviceId: string, roomId?: string, reason?: string) => {
    const id = ++toastIdCounter.current;
    setToasts((prev) => [...prev, { id, deviceId, roomId, reason }]);
    setTimeout(() => setToasts((prev) => prev.filter((t) => t.id !== id)), 8000);
  }, []);

  // ── Request Browser Notification Permission ──
  useEffect(() => {
    if (typeof window !== "undefined" && "Notification" in window) {
      if (Notification.permission !== "granted" && Notification.permission !== "denied") {
        Notification.requestPermission();
      }
    }
  }, []);

  // ── WebSocket handler ──
  useEffect(() => {
    if (!lastMessage) return;
    const { type, data } = lastMessage;
    const d = data as Record<string, unknown> | undefined;

    setTimeout(() => {
      if (
        lastMessage.type === "breach" ||
        (lastMessage.type === "alert" && d?.type === "breach") ||
        (lastMessage.type === "device_update" && d?.status === "breach")
      ) {
        const breachDeviceId = d?.deviceId || d?.device_id || (lastMessage as Record<string, unknown>).deviceId;
        if (breachDeviceId) {
          setDevices((prev) => prev.map((dev) => dev.deviceId === breachDeviceId ? { ...dev, status: "breach" } : dev));
        }
        const newAlert: Alert = {
          type: "breach",
          deviceId: (breachDeviceId as string) || "Unknown",
          roomId: d?.roomId as string | undefined,
          ts: ((lastMessage as Record<string, unknown>).timestamp as string | undefined) ?? new Date().toISOString(),
          acknowledged: false,
          message: d?.message as string | undefined,
        };
        setAlerts((prev) => [newAlert, ...prev].sort((a, b) => new Date(b.ts).getTime() - new Date(a.ts).getTime()).slice(0, 100));
        if (breachDeviceId) {
          addToast(breachDeviceId as string, d?.roomId as string | undefined, d?.message as string | undefined);
          
          if (typeof window !== "undefined" && "Notification" in window && Notification.permission === "granted") {
            const notif = new Notification("🚨 SECURITY BREACH DETECTED", {
              body: `Device ${breachDeviceId} ${d?.roomId ? `(Room ${d.roomId})` : ""} - ${d?.message || "Immediate attention required"}`,
            });
            notif.onclick = () => { window.focus(); notif.close(); };
          }
        }
      }

      if (lastMessage?.type === "device_update" && d?.deviceId) {
        setDevices((prev) => {
          const exists = prev.some((dev) => dev.deviceId === d.deviceId);
          if (!exists) return [...prev, { deviceId: d.deviceId as string, status: (d.status as string) || "ok", battery: d.battery as number, rssi: d.rssi as number, lastSeen: d.lastSeen as string }];
          return prev.map((dev) => dev.deviceId === d.deviceId ? { ...dev, status: (d.status as string) ?? dev.status, rssi: (d.rssi as number) ?? dev.rssi, battery: (d.battery as number) ?? dev.battery, lastSeen: (d.lastSeen as string) ?? dev.lastSeen } : dev);
        });
      }

      if (lastMessage?.type === "device_recovered" && d?.deviceId) {
        setDevices((prev) => prev.map((dev) => dev.deviceId === d.deviceId ? { ...dev, status: "ok" } : dev));
      }

      if (type === "device_offline" || type === "device_deleted") {
        if (d?.deviceId) {
          if (type === "device_deleted") setDevices((prev) => prev.filter((dev) => dev.deviceId !== d.deviceId));
          else setDevices((prev) => prev.map((dev) => dev.deviceId === d.deviceId ? { ...dev, status: "offline" } : dev));
        }
      }

      if (type === "database_cleared") { setDevices([]); setAlerts([]); }

      if (lastMessage?.type === "hotel_deleted") {
          // This hotel's account was deleted
          // by super admin — force logout
          const deletedHotelId = d?.hotel_id;
          const myHotelId = localStorage.getItem("dashboard_hotel_id");
          
          if (deletedHotelId === myHotelId) {
              alert(
                  "Your hotel account has been " +
                  "deleted by the administrator. " +
                  "You will be logged out."
              )
              // Clear everything and redirect
              localStorage.clear()
              router.push("/login")
          }
      }
    }, 0);
  }, [lastMessage, addToast, router]);

  // ── Initial fetch + polling ──
  useEffect(() => {
    if (!isAuthenticated || !user?.token) return;
    if (!API) { setTimeout(() => { setError("API URL not configured"); setIsLoading(false); }, 0); return; }

    const fetchAll = async () => {
      try {
        setTimeout(() => setError(null), 0);
        const headers = { Authorization: `Bearer ${user.token}` };
        const [devicesRes, alertsRes] = await Promise.all([
          fetch(`${API}/api/devices`, { headers }),
          fetch(`${API}/api/alerts/recent?limit=100`, { headers }),
        ]);
        if (!devicesRes.ok || !alertsRes.ok) { setError(`API Error: ${devicesRes.status} / ${alertsRes.status}`); setIsLoading(false); return; }
        const devData = await devicesRes.json();
        const alData  = await alertsRes.json();

        // Fetch sessions count
        if (user?.role !== "super_admin") {
          fetch(`${API}/api/auth/sessions`, { headers })
            .then(res => res.ok ? res.json() : [])
            .then(data => {
              setSessionCount(data.length);
              // maxSessions is not returned from /sessions, wait, we can fetch hotel details or we can get it from localStorage if we saved it?
              // The user prompt doesn't specify how to get maxSessions here, but we added it to create_user_token? No, we didn't add maxSessions to token response. Let's just mock it or assume it's fetched. 
              // Wait, the prompt says "Admin panel shows 'Dashboard Logins: 2/2'". In page.tsx it says "Add small indicator in header".
            })
            .catch(() => {});
        }

        setDevices(Array.isArray(devData) ? devData.filter((dev: Device) => dev?.deviceId) : []);
        setAlerts(Array.isArray(alData) ? [...alData].sort((a, b) => new Date(b.ts).getTime() - new Date(a.ts).getTime()).slice(0, 100) : []);
        setIsLoading(false);
      } catch (e) { setError(e instanceof Error ? e.message : "Failed to fetch data"); setIsLoading(false); }
    };

    fetchAll();
    const pollId = setInterval(fetchAll, 10000);
    return () => clearInterval(pollId);
  }, [isAuthenticated, user?.token, user?.role]);

  const handleDeleteDevice = async (deviceId: string) => {
    try {
      await fetch(`${API}/api/devices/${deviceId}`, { method: "DELETE", headers: { Authorization: `Bearer ${user?.token}` } });
      setDevices((prev) => prev.filter((d) => d.deviceId !== deviceId));
      setDeleteConfirm(null);
    } catch (e) { console.error("Failed to delete device", e); }
  };

  const acknowledgeAlert = async (alert: Alert) => {
    try {
      const deviceId = alert.deviceId ?? (alert.payload?.deviceId as string);
      if (!deviceId) return;
      await fetch(`${API}/api/alerts/acknowledge`, {
        method: "POST",
        headers: { "Content-Type": "application/json", Authorization: `Bearer ${user?.token}` },
        body: JSON.stringify({ device_id: deviceId, timestamp: alert.ts, notes: "Acknowledged from dashboard" }),
      });
      setAlerts((prev) => prev.map((a) => (a === alert ? { ...a, acknowledged: true } : a)));
    } catch (e) { console.error("Failed to acknowledge alert", e); }
  };

  const acknowledgeAll = async () => {
    try {
      await fetch(`${API}/api/alerts/acknowledge-all`, { method: "POST", headers: { Authorization: `Bearer ${user?.token}` } });
      setAlerts((prev) => prev.map((a) => ({ ...a, acknowledged: true })));
    } catch (e) { console.error("Failed to acknowledge all", e); }
  };

  // ── Derived stats ──
  const okCount      = devices.filter((d) => d.status === "ok" && !isDeviceOffline(d)).length;
  const breachCount  = devices.filter((d) => d.status === "breach" && !isDeviceOffline(d)).length;
  const offlineCount = devices.filter((d) => isDeviceOffline(d)).length;
  const unackCount   = alerts.filter((a) => !a.acknowledged).length;

  const filteredDevices = devices.filter((d) => {
    if (!d?.deviceId) return false;
    const offline = isDeviceOffline(d);
    if (filter === "ok"      && (d.status !== "ok" || offline)) return false;
    if (filter === "breach"  && (d.status !== "breach" || offline)) return false;
    if (filter === "offline" && !offline) return false;
    if (filter === "low_battery" && (d.battery === undefined || d.battery > 20)) return false;
    if (searchQuery && !d.deviceId.toLowerCase().includes(searchQuery.toLowerCase()) && !(d.roomId && d.roomId.toString().toLowerCase().includes(searchQuery.toLowerCase()))) return false;
    return true;
  });

  const filteredAlerts = alerts.filter((a) => {
    if (alertFilter === "breach") return a.type === "breach" || (a.payload && (a.payload as Record<string, unknown>).type === "breach");
    if (alertFilter === "unread") return !a.acknowledged;
    if (alertFilter === "offline") return a.type === "offline" || a.type === "device_offline";
    if (alertFilter === "low_battery") return a.type === "low_battery";
    if (alertFilter === "online") return a.type === "online" || a.type === "device_recovered";
    return true;
  });

  const initials = (user?.name || user?.username || "U").slice(0, 2).toUpperCase();

  // ── Guards ──
  if (checking) return (
    <div style={{ minHeight: "100vh", display: "flex", alignItems: "center", justifyContent: "center", background: "#0a0f1e" }}>
      <span className="animate-spin" style={{ width: 32, height: 32, border: "3px solid #1e2a45", borderTopColor: "#3b82f6", borderRadius: "50%", display: "inline-block" }} />
    </div>
  );

  if (!isAuthenticated) return null;

  if (user?.role === "super_admin") {
    if (typeof window !== "undefined") router.replace("/admin");
    return null;
  }

  const connColor =
    connectionStatus === "connected"     ? "#22c55e" :
    connectionStatus === "connecting"    ? "#f59e0b" : "#ef4444";

  const connLabel =
    connectionStatus === "connected"     ? "LIVE" :
    connectionStatus === "connecting"    ? "CONNECTING" : "OFFLINE";

  const alertTabs = [
    { id: "all", label: "All" },
    { id: "unread", label: "Unread" },
    { id: "breach", label: "Breach" },
    { id: "low_battery", label: "Low Battery" },
  ];

  return (
    <div style={{ minHeight: "100vh", background: "#0a0f1e", display: "flex", flexDirection: "column" }}>

      {/* ── Toast notifications ── */}
      <div style={{ position: "fixed", bottom: 24, right: 24, zIndex: 9999, display: "flex", flexDirection: "column", gap: 8 }}>
        {toasts.map((toast) => (
          <div
            key={toast.id}
            className="animate-toast-in"
            style={{
              background: "linear-gradient(135deg, rgba(239,68,68,0.95), rgba(220,38,38,0.95))",
              backdropFilter: "blur(10px)",
              border: "1px solid rgba(239,68,68,0.5)",
              borderRadius: 12,
              padding: "14px 18px",
              display: "flex",
              alignItems: "flex-start",
              gap: 12,
              maxWidth: 340,
              boxShadow: "0 8px 32px rgba(239,68,68,0.3)",
            }}
          >
            <span style={{ fontSize: 20 }}>🚨</span>
            <div style={{ flex: 1 }}>
              <p style={{ fontWeight: 700, fontSize: 13, color: "#fff", margin: 0 }}>BREACH DETECTED</p>
              <p style={{ fontSize: 12, color: "rgba(255,255,255,0.8)", margin: "2px 0 0" }}>
                {toast.deviceId}{toast.roomId ? ` · Room ${toast.roomId}` : ""}
              </p>
              {toast.reason && <p style={{ fontSize: 11, color: "rgba(255,255,255,0.6)", margin: "2px 0 0" }}>{toast.reason}</p>}
            </div>
            <button
              onClick={() => setToasts((prev) => prev.filter((t) => t.id !== toast.id))}
              style={{ background: "none", border: "none", color: "rgba(255,255,255,0.7)", cursor: "pointer", fontSize: 18, padding: 0, lineHeight: 1 }}
            >×</button>
          </div>
        ))}
      </div>

      {/* ── HEADER ── */}
      <header className="app-header" style={{
        background: "rgba(10,15,30,0.95)",
        backdropFilter: "blur(20px)",
        borderBottom: "1px solid #1e2a45",
        position: "sticky",
        top: 0,
        zIndex: 100,
        display: "flex",
        alignItems: "center",
        padding: "0 24px",
        gap: 16,
      }}>
        {/* Left: Branding */}
        <div style={{ display: "flex", alignItems: "center", gap: 10, flexShrink: 0 }}>
          <ShieldIcon size={24} />
          <span style={{ fontSize: 13, fontWeight: 700, color: "#ef4444", letterSpacing: "2px", textTransform: "uppercase" }}>
            Verbena Tech
          </span>
          
          <div style={{ width: 1, height: 24, background: "#1e2a45" }} />
          {user?.role !== "super_admin" && (
            <div className="text-xs text-gray-500" style={{ fontSize: 11, color: "#94a3b8" }}>
              Sessions: {sessionCount} active {/* ← NEW: Show active sessions count */}
            </div>
          )}

          <span style={{ fontSize: 14, fontWeight: 600, color: "#f1f5f9" }}>
            {user?.hotelName || "Dashboard"}
          </span>
        </div>

        {/* Center: Clock */}
        <div style={{ flex: 1, display: "flex", justifyContent: "center" }}>
          <LiveClock />
        </div>

        {/* Right: Status + User */}
        <div style={{ display: "flex", alignItems: "center", gap: 12, flexShrink: 0 }}>
          {/* Connection dot */}
          <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
            <span
              className={connectionStatus === "connected" ? "animate-dot" : ""}
              style={{ width: 8, height: 8, borderRadius: "50%", background: connColor, display: "inline-block" }}
            />
            <span style={{ fontSize: 11, fontWeight: 600, color: connColor, letterSpacing: "0.5px" }}>
              {connLabel}
            </span>
          </div>

          <div style={{ width: 1, height: 24, background: "#1e2a45" }} />

          {/* User avatar */}
          <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
            <div style={{ width: 32, height: 32, borderRadius: "50%", background: "linear-gradient(135deg, #3b82f6, #1d4ed8)", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 12, fontWeight: 700, color: "#fff" }}>
              {initials}
            </div>
            <div>
              <div style={{ fontSize: 12, fontWeight: 600, color: "#f1f5f9", lineHeight: 1 }}>
                {user?.name || user?.username}
              </div>
              <div style={{ fontSize: 10, color: "#475569" }}>Admin</div>
            </div>
          </div>

          {/* Logout */}
          <button
            onClick={handleLogout}
            style={{ background: "none", border: "1px solid #1e2a45", borderRadius: 8, padding: "6px 10px", color: "#94a3b8", cursor: "pointer", display: "flex", alignItems: "center", gap: 6, fontSize: 12, transition: "border-color 0.2s, color 0.2s" }}
            onMouseEnter={(e) => { e.currentTarget.style.borderColor = "#ef4444"; e.currentTarget.style.color = "#ef4444"; }}
            onMouseLeave={(e) => { e.currentTarget.style.borderColor = "#1e2a45"; e.currentTarget.style.color = "#94a3b8"; }}
          >
            <LogoutIcon /> Logout
          </button>
        </div>
      </header>

      {/* ── MAIN CONTENT ── */}
      <main style={{ flex: 1, padding: "24px", maxWidth: 1400, width: "100%", margin: "0 auto", boxSizing: "border-box" }}>

        {/* Error banner */}
        {error && (
          <div style={{ background: "rgba(239,68,68,0.1)", border: "1px solid rgba(239,68,68,0.3)", borderRadius: 12, padding: "12px 16px", marginBottom: 20, display: "flex", alignItems: "center", gap: 8 }}>
            <AlertTriangleIcon size={16} /><span style={{ fontSize: 13, color: "#ef4444" }}>{error}</span>
            <span style={{ fontSize: 12, color: "#475569", marginLeft: 8 }}>API: {API}</span>
          </div>
        )}

        {/* Loading */}
        {isLoading && devices.length === 0 && (
          <div style={{ background: "#141b2d", border: "1px solid #1e2a45", borderRadius: 12, padding: 16, marginBottom: 20, textAlign: "center", color: "#94a3b8", fontSize: 14 }}>
            🔄 Loading dashboard data…
          </div>
        )}

        {/* ── STATS CARDS ── */}
        <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 16, marginBottom: 24 }}>
          <StatCard
            icon={<TabletIcon />}
            value={devices.length}
            label="Total Devices"
            sub="Active fleet"
            accent="#3b82f6"
            glow="rgba(59,130,246,0.15)"
          />
          <StatCard
            icon={<WifiIcon bars={4} color="#22c55e" />}
            value={okCount}
            label="Devices Online"
            sub="Secured and monitoring"
            accent="#22c55e"
            glow="rgba(34,197,94,0.15)"
          />
          <StatCard
            icon={<WifiOffIcon color="#f59e0b" size={22} />}
            value={offlineCount}
            label="Devices Offline"
            sub="Last seen recently"
            accent="#f59e0b"
            glow="rgba(245,158,11,0.15)"
          />
          <StatCard
            icon={<AlertTriangleIcon color="#ef4444" size={22} />}
            value={breachCount}
            label="Active Breaches"
            sub={breachCount > 0 ? "Requires attention" : "All clear"}
            accent="#ef4444"
            glow="rgba(239,68,68,0.15)"
            pulseBorder
          />
        </div>

        {/* ── BREACH ALERT BANNER ── */}
        {breachCount > 0 && (
          <div
            className="animate-breach-pulse"
            style={{
              background: "linear-gradient(135deg, rgba(239,68,68,0.15), rgba(220,38,38,0.05))",
              border: "1px solid rgba(239,68,68,0.3)",
              borderLeft: "4px solid #ef4444",
              borderRadius: 12,
              padding: "16px 20px",
              marginBottom: 24,
              display: "flex",
              alignItems: "center",
              justifyContent: "space-between",
              gap: 16,
            }}
          >
            <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
              <span className="animate-dot-fast" style={{ width: 10, height: 10, borderRadius: "50%", background: "#ef4444", display: "inline-block", flexShrink: 0 }} />
              <div>
                <div style={{ fontSize: 14, fontWeight: 700, color: "#ef4444", letterSpacing: "1px" }}>🚨 SECURITY ALERT</div>
                <div style={{ fontSize: 13, color: "#fca5a5" }}>{breachCount} device{breachCount > 1 ? "s" : ""} require immediate attention</div>
              </div>
            </div>
            <div style={{ display: "flex", gap: 8, flexShrink: 0 }}>
              {unackCount > 0 && (
                <button
                  onClick={acknowledgeAll}
                  style={{ border: "1px solid #22c55e", background: "none", color: "#22c55e", borderRadius: 8, padding: "7px 14px", fontSize: 12, fontWeight: 600, cursor: "pointer", transition: "background 0.2s" }}
                  onMouseEnter={(e) => { e.currentTarget.style.background = "rgba(34,197,94,0.1)"; }}
                  onMouseLeave={(e) => { e.currentTarget.style.background = "none"; }}
                >
                  ✓ Acknowledge All
                </button>
              )}
              <button
                onClick={() => setFilter("breach")}
                style={{ border: "1px solid #ef4444", background: "none", color: "#ef4444", borderRadius: 8, padding: "7px 14px", fontSize: 12, fontWeight: 600, cursor: "pointer", transition: "background 0.2s, color 0.2s" }}
                onMouseEnter={(e) => { e.currentTarget.style.background = "#ef4444"; e.currentTarget.style.color = "#fff"; }}
                onMouseLeave={(e) => { e.currentTarget.style.background = "none"; e.currentTarget.style.color = "#ef4444"; }}
              >
                View All Alerts
              </button>
            </div>
          </div>
        )}

        {/* ── TWO COLUMN LAYOUT ── */}
        <div style={{ display: "grid", gridTemplateColumns: "3fr 2fr", gap: 20, alignItems: "start" }}>

          {/* ── LEFT: Device Fleet ── */}
          <div>
            {/* Section header */}
            <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 16, gap: 12, flexWrap: "wrap" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                <h2 style={{ fontSize: 18, fontWeight: 700, color: "#f1f5f9", margin: 0 }}>Device Fleet</h2>
                <span style={{ background: "#1e2a45", borderRadius: 20, padding: "3px 10px", fontSize: 12, color: "#94a3b8" }}>
                  {filteredDevices.length} device{filteredDevices.length !== 1 ? "s" : ""}
                </span>
              </div>
              <div style={{ display: "flex", gap: 8, flex: 1, justifyContent: "flex-end", maxWidth: 400 }}>
                {/* Search */}
                <div style={{ position: "relative", flex: 1 }}>
                  <div style={{ position: "absolute", left: 10, top: "50%", transform: "translateY(-50%)" }}>
                    <SearchIcon />
                  </div>
                  <input
                    type="text"
                    placeholder="Search devices or rooms..."
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    style={{
                      width: "100%",
                      background: "#141b2d",
                      border: "1px solid #1e2a45",
                      borderRadius: 10,
                      padding: "8px 12px 8px 32px",
                      color: "#f1f5f9",
                      fontSize: 13,
                      outline: "none",
                      boxSizing: "border-box",
                      fontFamily: "inherit",
                    }}
                    onFocus={(e) => { e.target.style.borderColor = "#3b82f6"; e.target.style.boxShadow = "0 0 0 2px rgba(59,130,246,0.1)"; }}
                    onBlur={(e) => { e.target.style.borderColor = "#1e2a45"; e.target.style.boxShadow = "none"; }}
                  />
                </div>
                {/* Filter */}
                <select
                  value={filter}
                  onChange={(e) => setFilter(e.target.value)}
                  style={{ background: "#141b2d", border: "1px solid #1e2a45", borderRadius: 10, padding: "8px 12px", color: "#94a3b8", fontSize: 13, outline: "none", cursor: "pointer", fontFamily: "inherit" }}
                >
                  <option value="all">All</option>
                  <option value="ok">Online</option>
                  <option value="breach">Breach</option>
                  <option value="offline">Offline</option>
                  <option value="low_battery">Low Battery</option>
                </select>
              </div>
            </div>

            {/* Device grid */}
            {filteredDevices.length === 0 && !isLoading ? (
              <div style={{ background: "#141b2d", border: "1px dashed #1e2a45", borderRadius: 16, padding: "40px 20px", textAlign: "center", color: "#475569", fontSize: 14 }}>
                No devices match your search criteria.
              </div>
            ) : (
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
                {filteredDevices.map((d) => (
                  <DeviceCard key={d.deviceId} d={d} onDelete={(id) => setDeleteConfirm(id)} />
                ))}
              </div>
            )}
          </div>

          {/* ── RIGHT: Alerts ── */}
          <div>
            {/* Section header */}
            <div style={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between", marginBottom: 16, gap: 8, flexWrap: "wrap" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                <h2 style={{ fontSize: 18, fontWeight: 700, color: "#f1f5f9", margin: 0 }}>Security Alerts</h2>
                {unackCount > 0 && (
                  <span style={{ background: "#ef4444", borderRadius: 20, padding: "2px 8px", fontSize: 11, fontWeight: 700, color: "#fff" }}>
                    {unackCount}
                  </span>
                )}
              </div>
              {/* Tabs */}
              <div style={{ display: "flex", gap: 4, flexWrap: "wrap", justifyContent: "flex-end", maxWidth: 280 }}>
                {alertTabs.map((tab) => (
                  <button
                    key={tab.id}
                    onClick={() => { setAlertFilter(tab.id); setVisibleAlertsCount(50); }}
                    style={{
                      background: alertFilter === tab.id ? "#1e2a45" : "none",
                      border: `1px solid ${alertFilter === tab.id ? "#2d3f60" : "#1e2a45"}`,
                      borderRadius: 8,
                      padding: "4px 10px",
                      color: alertFilter === tab.id ? "#f1f5f9" : "#475569",
                      fontSize: 11,
                      fontWeight: 600,
                      cursor: "pointer",
                      transition: "background 0.2s, color 0.2s",
                    }}
                  >
                    {tab.label}
                  </button>
                ))}
              </div>
            </div>

            {/* Alerts list */}
            <div style={{ maxHeight: "calc(100vh - 320px)", overflowY: "auto", paddingRight: 4 }}>
              {filteredAlerts.length === 0 && !isLoading ? (
                <div style={{ background: "#141b2d", border: "1px dashed #1e2a45", borderRadius: 12, padding: "32px 20px", textAlign: "center", color: "#475569", fontSize: 13 }}>
                  ✓ No alerts matching filter
                </div>
              ) : (
                filteredAlerts.slice(0, visibleAlertsCount).map((a, i) => (
                  <AlertItem key={a.id ?? i} a={a} onAcknowledge={acknowledgeAlert} onClick={setSelectedAlert} />
                ))
              )}
              {filteredAlerts.length > visibleAlertsCount && (
                <button
                  onClick={() => setVisibleAlertsCount((prev) => prev + 50)}
                  style={{ width: "100%", background: "#141b2d", border: "1px solid #1e2a45", borderRadius: 10, padding: "10px", color: "#94a3b8", fontSize: 12, cursor: "pointer", marginTop: 8, fontFamily: "inherit" }}
                >
                  Load More Alerts
                </button>
              )}
            </div>
          </div>
        </div>
      </main>

      {/* ── FOOTER ── */}
      <footer className="app-footer" style={{
        background: "#0a0f1e",
        borderTop: "1px solid #1e2a45",
        padding: "0 24px",
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        flexWrap: "wrap",
        gap: 8,
      }}>
        <span style={{ fontSize: 11, color: "#475569" }}>Verbena Tech Security System v2.0</span>
        <span style={{ fontSize: 11, color: "#475569" }}>
          Last updated: <LiveClock />
        </span>
        <div style={{ display: "flex", gap: 16, alignItems: "center" }}>
          <span style={{ fontSize: 11, color: "#475569" }}>Powered by Verbena Tech • All rights reserved</span>
          <span style={{ fontSize: 11, color: "#475569" }}>
            Contact: <a href="mailto:sivakk@verbenatech.in" style={{ color: "#3b82f6", textDecoration: "none" }}>sivakk@verbenatech.in</a>
          </span>
        </div>
      </footer>

      {/* ── DELETE CONFIRM MODAL ── */}
      {deleteConfirm && (
        <div
          style={{ position: "fixed", inset: 0, background: "rgba(0,0,0,0.7)", backdropFilter: "blur(4px)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 200, padding: 20 }}
          onClick={() => setDeleteConfirm(null)}
        >
          <div
            style={{ background: "#141b2d", border: "1px solid #1e2a45", borderRadius: 20, padding: 32, maxWidth: 400, width: "100%", boxShadow: "0 25px 50px rgba(0,0,0,0.5)" }}
            onClick={(e) => e.stopPropagation()}
          >
            <div style={{ display: "flex", alignItems: "center", gap: 10, color: "#ef4444", marginBottom: 16 }}>
              <TrashIcon size={20} />
              <h3 style={{ fontSize: 18, fontWeight: 700, margin: 0 }}>Delete Device?</h3>
            </div>
            <p style={{ color: "#94a3b8", fontSize: 14, lineHeight: 1.6, marginBottom: 24 }}>
              Are you sure you want to delete <strong style={{ color: "#f1f5f9" }}>{deleteConfirm}</strong>?
              This will permanently remove the device and all associated alerts. This action cannot be undone.
            </p>
            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
              <button
                onClick={() => setDeleteConfirm(null)}
                style={{ padding: "10px 20px", background: "#1e2a45", border: "none", borderRadius: 10, color: "#94a3b8", fontSize: 13, fontWeight: 500, cursor: "pointer", fontFamily: "inherit" }}
              >
                Cancel
              </button>
              <button
                onClick={() => handleDeleteDevice(deleteConfirm)}
                style={{ padding: "10px 20px", background: "#ef4444", border: "none", borderRadius: 10, color: "#fff", fontSize: 13, fontWeight: 600, cursor: "pointer", fontFamily: "inherit" }}
              >
                Delete Device
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── ALERT DETAIL MODAL ── */}
      {selectedAlert && (
        <div
          style={{ position: "fixed", inset: 0, background: "rgba(0,0,0,0.7)", backdropFilter: "blur(4px)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 200, padding: 20 }}
          onClick={() => setSelectedAlert(null)}
        >
          <div
            style={{ background: "#141b2d", border: "1px solid #1e2a45", borderRadius: 20, padding: 32, maxWidth: 520, width: "100%", boxShadow: "0 25px 50px rgba(0,0,0,0.5)" }}
            onClick={(e) => e.stopPropagation()}
          >
            <h3 style={{ fontSize: 18, fontWeight: 700, color: "#f1f5f9", marginBottom: 16, marginTop: 0 }}>Alert Details</h3>
            <pre style={{ background: "#0a0f1e", border: "1px solid #1e2a45", borderRadius: 10, padding: 16, fontSize: 12, color: "#94a3b8", overflowX: "auto", maxHeight: "60vh", margin: 0, fontFamily: "monospace" }}>
              {JSON.stringify(selectedAlert, null, 2)}
            </pre>
            <button
              onClick={() => setSelectedAlert(null)}
              style={{ marginTop: 20, width: "100%", padding: "12px", background: "#1e2a45", border: "none", borderRadius: 10, color: "#f1f5f9", fontSize: 14, fontWeight: 600, cursor: "pointer", fontFamily: "inherit" }}
            >
              Close
            </button>
          </div>
        </div>
      )}

      {/* Responsive CSS */}
      <style>{`
        .app-header { height: 64px; }
        .app-footer { height: 40px; }
        @media (max-width: 1100px) {
          main > div:last-of-type { grid-template-columns: 1fr !important; }
          main > div:first-of-type { grid-template-columns: repeat(2, 1fr) !important; }
        }
        @media (max-width: 768px) {
          .app-header {
            height: auto !important;
            min-height: 64px;
            padding: 12px 16px !important;
            flex-wrap: wrap;
            justify-content: space-between;
          }
          .app-header > div:nth-child(2) { display: none !important; }
          .app-header > div:nth-child(3) { margin-left: auto; }
          .app-footer {
            height: auto !important;
            flex-direction: column;
            gap: 8px;
            padding: 16px !important;
            text-align: center;
          }
        }
        @media (max-width: 700px) {
          main > div:first-of-type { grid-template-columns: 1fr 1fr !important; }
          main > div:last-of-type > div:first-child > div:last-child {
            grid-template-columns: 1fr !important;
          }
        }
        @media (max-width: 480px) {
          main > div:first-of-type { grid-template-columns: 1fr !important; }
          .app-header > div:nth-child(1) span { display: none; } /* Hide text on very small screens to fit icons */
          .app-header > div:nth-child(3) { flex-wrap: wrap; justify-content: flex-end; }
        }
        select option { background: #141b2d; color: #f1f5f9; }
      `}</style>
    </div>
  );
}
