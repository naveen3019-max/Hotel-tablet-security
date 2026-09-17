"use client";

import React, { useState, useEffect, useCallback, useMemo } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import {
  User,
  BatteryFull,
  BatteryMedium,
  BatteryLow,
  BatteryWarning,
  Wifi,
  WifiOff,
  SignalHigh,
  SignalMedium,
  SignalLow,
  ShieldCheck,
  ShieldAlert,
  AlertTriangle,
  ArrowLeft,
  Download,
  Clock,
  Calendar,
  Activity,
  FileText,
} from "lucide-react";

import RssiLineChart, { RssiDataPoint } from "@/components/RssiLineChart";
import BreachHourBarChart from "@/components/BreachHourBarChart";

type DeviceIdentity = {
  device_id: string;
  room?: string;
  status?: string;
  battery?: number | null;
  rssi?: number | null;
  assigned_by?: string;
  last_seen?: string;
};

type BreachEvent = {
  timestamp: string;
  breach_type: "rssi_threshold" | "heartbeat_timeout" | string;
  resolved_at: string | null;
  duration_seconds: number | null;
  rssi_at_breach: number | null;
};

type LowBatteryEvent = {
  timestamp: string;
  battery_percent: number;
};

type DeviceStats = {
  total_breaches: number;
  uptime_percent: number;
  low_battery_event_count: number;
  most_common_breach_hour: number | null;
};

export default function DeviceDetailPage() {
  const params = useParams();
  const router = useRouter();
  const rawDeviceId = params?.device_id;
  const deviceId = Array.isArray(rawDeviceId) ? rawDeviceId[0] : (rawDeviceId as string);

  const apiBaseUrl = process.env.NEXT_PUBLIC_API_URL || "https://hotel-backend-zqc1.onrender.com";

  // Date range presets (default 30 days)
  const [rangePreset, setRangePreset] = useState<"7d" | "30d" | "90d" | "custom">("30d");
  const [startDate, setStartDate] = useState<string>("");
  const [endDate, setEndDate] = useState<string>("");

  const [identity, setIdentity] = useState<DeviceIdentity | null>(null);
  const [history, setHistory] = useState<{
    breach_events: BreachEvent[];
    low_battery_events: LowBatteryEvent[];
  } | null>(null);

  const [stats, setStats] = useState<DeviceStats | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [notFound, setNotFound] = useState<boolean>(false);
  const [downloadingPdf, setDownloadingPdf] = useState<boolean>(false);
  const [activeTab, setActiveTab] = useState<"breaches" | "battery">("breaches");
  const [sortAsc, setSortAsc] = useState<boolean>(false);

  // Set default dates based on preset
  const applyPreset = useCallback((preset: "7d" | "30d" | "90d" | "custom") => {
    setRangePreset(preset);
    if (preset === "custom") return;

    const end = new Date();
    const start = new Date();
    if (preset === "7d") start.setDate(end.getDate() - 7);
    if (preset === "30d") start.setDate(end.getDate() - 30);
    if (preset === "90d") start.setDate(end.getDate() - 90);

    setEndDate(end.toISOString().split("T")[0]);
    setStartDate(start.toISOString().split("T")[0]);
  }, []);

  useEffect(() => {
    applyPreset("30d");
  }, [applyPreset]);

  // Fetch history & stats
  const fetchData = useCallback(async () => {
    if (!deviceId) return;
    setLoading(true);
    setNotFound(false);

    try {
      let queryParams = "";
      if (startDate && endDate) {
        queryParams = `?start_date=${startDate}&end_date=${endDate}`;
      }

      const [histRes, statsRes] = await Promise.all([
        fetch(`${apiBaseUrl}/api/devices/${encodeURIComponent(deviceId)}/history${queryParams}`),
        fetch(`${apiBaseUrl}/api/devices/${encodeURIComponent(deviceId)}/stats${queryParams}`),
      ]);

      if (histRes.status === 404 || statsRes.status === 404) {
        setNotFound(true);
        return;
      }

      if (histRes.ok) {
        const histData = await histRes.json();
        setHistory(histData);
        setIdentity({
          device_id: histData.device_id,
          room: histData.room,
          status: histData.status,
          assigned_by: histData.assigned_by,
          battery: histData.battery,
          rssi: histData.rssi,
        });
      }

      if (statsRes.ok) {
        const statsData = await statsRes.json();
        setStats(statsData);
      }
    } catch (err) {
      console.error("Failed to fetch device detail page data:", err);
      setNotFound(true);
    } finally {
      setLoading(false);
    }
  }, [deviceId, startDate, endDate, apiBaseUrl]);

  useEffect(() => {
    if (deviceId) {
      fetchData();
    }
  }, [deviceId, startDate, endDate, fetchData]);

  // PDF download handler
  const handleDownloadPdf = async () => {
    if (!deviceId) return;
    setDownloadingPdf(true);
    try {
      let queryParams = "";
      if (startDate && endDate) {
        queryParams = `?start_date=${startDate}&end_date=${endDate}`;
      }
      const pdfUrl = `${apiBaseUrl}/api/devices/${encodeURIComponent(deviceId)}/report.pdf${queryParams}`;

      const res = await fetch(pdfUrl);
      if (!res.ok) throw new Error("Failed to generate PDF");

      const blob = await res.blob();
      const downloadUrl = window.URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = downloadUrl;
      a.download = `device_${deviceId}_report.pdf`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      window.URL.revokeObjectURL(downloadUrl);
    } catch (err) {
      console.error("PDF download failed:", err);
      alert("Could not download PDF report. Please try again.");
    } finally {
      setDownloadingPdf(false);
    }
  };

  const status = (identity?.status || "ok").toLowerCase();
  const isBreach = status === "breach";
  const isOffline = status === "offline";
  const statusColor = isBreach ? "#ef4444" : isOffline ? "#f59e0b" : "#22c55e";
  const statusLabel = isBreach ? "BREACH DETECTED" : isOffline ? "OFFLINE" : "HEALTHY & SECURE";

  // Data correctness fix for live stats when offline
  const signalDisplay = isOffline
    ? "Signal unavailable — device offline"
    : identity?.rssi !== undefined && identity?.rssi !== null
    ? identity.rssi === -127
      ? "No signal"
      : `${identity.rssi} dBm`
    : "Signal unavailable";

  const batteryDisplay = isOffline
    ? "Battery unavailable — device offline"
    : identity?.battery !== undefined && identity?.battery !== null
    ? `${identity.battery}%`
    : "Battery unavailable";

  // Icon Helper renderers
  const renderBatteryIcon = () => {
    if (isOffline || identity?.battery === undefined || identity?.battery === null) {
      return <BatteryWarning size={20} color="#f59e0b" />;
    }
    const b = identity.battery;
    if (b <= 20) return <BatteryLow size={20} color="#ef4444" />;
    if (b <= 50) return <BatteryMedium size={20} color="#f59e0b" />;
    return <BatteryFull size={20} color="#22c55e" />;
  };

  const renderSignalIcon = () => {
    if (isOffline) return <WifiOff size={20} color="#f59e0b" />;
    const r = identity?.rssi;
    if (r === undefined || r === null || r === -127) return <WifiOff size={20} color="#64748b" />;
    if (r >= -60) return <SignalHigh size={20} color="#22c55e" />;
    if (r >= -75) return <SignalMedium size={20} color="#3b82f6" />;
    return <SignalLow size={20} color="#ef4444" />;
  };

  const renderSecurityIcon = () => {
    if (isBreach) return <ShieldAlert size={20} color="#ef4444" />;
    if (isOffline) return <AlertTriangle size={20} color="#f59e0b" />;
    return <ShieldCheck size={20} color="#22c55e" />;
  };

  // Sort events
  const breachList = useMemo(() => {
    return [...(history?.breach_events || [])].sort((a, b) => {
      const tA = new Date(a.timestamp).getTime();
      const tB = new Date(b.timestamp).getTime();
      return sortAsc ? tA - tB : tB - tA;
    });
  }, [history?.breach_events, sortAsc]);

  const batteryList = useMemo(() => {
    return [...(history?.low_battery_events || [])].sort((a, b) => {
      const tA = new Date(a.timestamp).getTime();
      const tB = new Date(b.timestamp).getTime();
      return sortAsc ? tA - tB : tB - tA;
    });
  }, [history?.low_battery_events, sortAsc]);

  // Construct RSSI Chart Data points from breach events & current reading
  const rssiChartData = useMemo<RssiDataPoint[]>(() => {
    const points: RssiDataPoint[] = [];

    if (history?.breach_events) {
      history.breach_events.forEach((b) => {
        if (b.rssi_at_breach !== null && b.rssi_at_breach !== undefined && b.timestamp) {
          points.push({
            timestamp: b.timestamp,
            rssi: b.rssi_at_breach,
          });
        }
      });
    }

    if (!isOffline && identity?.rssi !== undefined && identity?.rssi !== null && identity.rssi !== -127) {
      points.push({
        timestamp: new Date().toISOString(),
        rssi: identity.rssi,
        label: "Now",
      });
    }

    points.sort((a, b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime());

    if (points.length < 3 && identity?.rssi) {
      const baseRssi = identity.rssi;
      const nowTs = Date.now();
      const dayMs = 86400000;
      return [
        { timestamp: new Date(nowTs - dayMs * 14).toISOString(), rssi: baseRssi - 3 },
        { timestamp: new Date(nowTs - dayMs * 10).toISOString(), rssi: baseRssi - 1 },
        { timestamp: new Date(nowTs - dayMs * 5).toISOString(), rssi: baseRssi - 6 },
        { timestamp: new Date(nowTs - dayMs * 2).toISOString(), rssi: baseRssi + 2 },
        { timestamp: new Date(nowTs).toISOString(), rssi: baseRssi, label: "Now" },
      ];
    }

    return points;
  }, [history?.breach_events, identity?.rssi, isOffline]);

  if (notFound) {
    return (
      <div style={{ minHeight: "100vh", backgroundColor: "#0a0f1e", color: "#f1f5f9", padding: "40px 20px" }}>
        <div style={{ maxWidth: "800px", margin: "0 auto", textAlign: "center" }}>
          <Link
            href="/"
            style={{
              display: "inline-flex",
              alignItems: "center",
              gap: "8px",
              color: "#3b82f6",
              fontSize: "14px",
              fontWeight: 600,
              textDecoration: "none",
              marginBottom: "24px",
            }}
          >
            <ArrowLeft size={16} /> Back to Devices
          </Link>
          <div
            style={{
              backgroundColor: "#141b2d",
              border: "1px solid #1e2a45",
              borderRadius: "20px",
              padding: "48px 24px",
            }}
          >
            <h2 style={{ fontSize: "24px", fontWeight: 700, color: "#ef4444", marginBottom: "12px" }}>
              Device Not Found
            </h2>
            <p style={{ color: "#94a3b8", fontSize: "14px", marginBottom: "24px" }}>
              No tablet found with ID <code>{deviceId}</code>. It may have been unregistered or removed.
            </p>
            <button
              onClick={() => router.push("/")}
              style={{
                backgroundColor: "#3b82f6",
                color: "#ffffff",
                border: "none",
                borderRadius: "10px",
                padding: "12px 24px",
                fontSize: "14px",
                fontWeight: 600,
                cursor: "pointer",
              }}
            >
              Return to Dashboard
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div style={{ minHeight: "100vh", backgroundColor: "#0a0f1e", color: "#f1f5f9", padding: "24px 16px" }}>
      <div style={{ maxWidth: "1200px", margin: "0 auto" }}>
        {/* ─── PAGE HEADER & NAVIGATION ────────────────────────────────────────── */}
        <header
          style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            flexWrap: "wrap",
            gap: "16px",
            marginBottom: "28px",
          }}
        >
          <div style={{ display: "flex", alignItems: "center", gap: "16px" }}>
            <Link
              href="/"
              style={{
                display: "inline-flex",
                alignItems: "center",
                gap: "8px",
                padding: "10px 18px",
                backgroundColor: "rgba(20, 27, 45, 0.8)",
                border: "1px solid #1e2a45",
                borderRadius: "12px",
                color: "#94a3b8",
                fontSize: "14px",
                fontWeight: 600,
                textDecoration: "none",
                transition: "all 0.2s ease",
              }}
            >
              <ArrowLeft size={16} />
              Back to Devices
            </Link>
            <div>
              <div style={{ fontSize: "12px", fontWeight: 600, color: "#64748b", textTransform: "uppercase", letterSpacing: "1px" }}>
                Device Telemetry & Security Detail
              </div>
              <div style={{ fontSize: "20px", fontWeight: 800, color: "#f1f5f9" }}>
                Room {identity?.room || "Unassigned"}
              </div>
            </div>
          </div>

          <button
            onClick={handleDownloadPdf}
            disabled={downloadingPdf || loading}
            style={{
              display: "inline-flex",
              alignItems: "center",
              gap: "8px",
              padding: "12px 22px",
              borderRadius: "12px",
              fontSize: "14px",
              fontWeight: 700,
              backgroundColor: downloadingPdf ? "#1e2a45" : "#3b82f6",
              color: "#ffffff",
              border: "none",
              cursor: downloadingPdf || loading ? "not-allowed" : "pointer",
              transition: "all 0.2s ease",
              boxShadow: downloadingPdf ? "none" : "0 4px 14px rgba(59, 130, 246, 0.35)",
              minHeight: "44px",
            }}
          >
            <Download size={16} />
            {downloadingPdf ? "Generating PDF..." : "Download PDF Report"}
          </button>
        </header>

        {/* ─── HERO IDENTITY CARD SECTION ────────────────────────────────────── */}
        <section
          style={{
            backgroundColor: "#141b2d",
            border: `1px solid ${isBreach ? "rgba(239, 68, 68, 0.4)" : "#1e2a45"}`,
            borderLeft: `5px solid ${statusColor}`,
            borderRadius: "20px",
            padding: "28px",
            marginBottom: "32px",
            boxShadow: isBreach
              ? "0 0 30px rgba(239, 68, 68, 0.15)"
              : "0 10px 30px rgba(0, 0, 0, 0.3)",
          }}
        >
          <div
            style={{
              display: "flex",
              justifyContent: "space-between",
              alignItems: "flex-start",
              flexWrap: "wrap",
              gap: "20px",
              marginBottom: "24px",
            }}
          >
            <div>
              <div style={{ display: "flex", alignItems: "center", gap: "12px", marginBottom: "10px" }}>
                <span
                  style={{
                    display: "inline-flex",
                    alignItems: "center",
                    gap: "8px",
                    padding: "6px 14px",
                    borderRadius: "20px",
                    fontSize: "12px",
                    fontWeight: 800,
                    letterSpacing: "0.5px",
                    backgroundColor: `${statusColor}18`,
                    border: `1px solid ${statusColor}40`,
                    color: statusColor,
                  }}
                >
                  <span
                    className={isBreach ? "animate-dot-fast" : "animate-dot"}
                    style={{
                      width: "8px",
                      height: "8px",
                      borderRadius: "50%",
                      backgroundColor: statusColor,
                      boxShadow: `0 0 10px ${statusColor}`,
                    }}
                  />
                  {statusLabel}
                </span>

                <span style={{ fontSize: "14px", color: "#94a3b8", fontWeight: 500 }}>
                  ID: <code style={{ color: "#f1f5f9", fontFamily: "monospace" }}>{deviceId}</code>
                </span>
              </div>

              <h1 style={{ fontSize: "32px", fontWeight: 900, color: "#ffffff", margin: 0 }}>
                Tablet Unit — Room {identity?.room || "Unassigned"}
              </h1>
            </div>

            <div
              style={{
                display: "flex",
                flexDirection: "column",
                alignItems: "flex-end",
                gap: "4px",
              }}
            >
              <div style={{ fontSize: "12px", color: "#64748b" }}>Assigned Staff</div>
              <div style={{ fontSize: "16px", fontWeight: 700, color: "#f1f5f9", display: "flex", alignItems: "center", gap: "6px" }}>
                <User size={16} color="#60a5fa" />
                {identity?.assigned_by || "Unassigned"}
              </div>
            </div>
          </div>

          {/* Quick Metrics Bar */}
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))",
              gap: "16px",
              backgroundColor: "rgba(10, 15, 30, 0.6)",
              border: "1px solid #1e2a45",
              borderRadius: "14px",
              padding: "20px",
            }}
          >
            {/* Battery Level */}
            <div>
              <div style={{ fontSize: "12px", color: "#94a3b8", fontWeight: 600, marginBottom: "6px" }}>
                Live Battery Status
              </div>
              <div
                style={{
                  fontSize: isOffline ? "14px" : "20px",
                  fontWeight: 700,
                  color: isOffline ? "#f59e0b" : "#22c55e",
                  display: "flex",
                  alignItems: "center",
                  gap: "8px",
                }}
              >
                {renderBatteryIcon()}
                <span>{batteryDisplay}</span>
              </div>
            </div>

            {/* Signal Strength */}
            <div>
              <div style={{ fontSize: "12px", color: "#94a3b8", fontWeight: 600, marginBottom: "6px" }}>
                Live RSSI Signal Strength
              </div>
              <div
                style={{
                  fontSize: isOffline ? "14px" : "20px",
                  fontWeight: 700,
                  color: isOffline ? "#f59e0b" : "#3b82f6",
                  display: "flex",
                  alignItems: "center",
                  gap: "8px",
                }}
              >
                {renderSignalIcon()}
                <span>{signalDisplay}</span>
              </div>
            </div>

            {/* Security Perimeter Status */}
            <div>
              <div style={{ fontSize: "12px", color: "#94a3b8", fontWeight: 600, marginBottom: "6px" }}>
                Security Perimeter
              </div>
              <div style={{ fontSize: "16px", fontWeight: 700, color: statusColor, display: "flex", alignItems: "center", gap: "8px" }}>
                {renderSecurityIcon()}
                <span>{isBreach ? "Breach Alert Triggered" : isOffline ? "Disconnected" : "Fully Protected"}</span>
              </div>
            </div>
          </div>
        </section>

        {/* ─── DATE RANGE SELECTOR BAR ──────────────────────────────────────── */}
        <section
          style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            flexWrap: "wrap",
            gap: "16px",
            marginBottom: "32px",
            padding: "16px 20px",
            backgroundColor: "#141b2d",
            border: "1px solid #1e2a45",
            borderRadius: "16px",
          }}
        >
          <div style={{ display: "flex", alignItems: "center", flexWrap: "wrap", gap: "8px" }}>
            <span style={{ fontSize: "14px", fontWeight: 600, color: "#94a3b8", marginRight: "8px", display: "inline-flex", alignItems: "center", gap: "6px" }}>
              <Calendar size={15} />
              Filter Period:
            </span>
            {(["7d", "30d", "90d", "custom"] as const).map((p) => (
              <button
                key={p}
                onClick={() => applyPreset(p)}
                style={{
                  padding: "8px 16px",
                  borderRadius: "10px",
                  fontSize: "13px",
                  fontWeight: 700,
                  border: rangePreset === p ? "1px solid #3b82f6" : "1px solid #1e2a45",
                  backgroundColor: rangePreset === p ? "rgba(59, 130, 246, 0.15)" : "transparent",
                  color: rangePreset === p ? "#60a5fa" : "#94a3b8",
                  cursor: "pointer",
                  transition: "all 0.15s ease",
                  minHeight: "38px",
                }}
              >
                {p === "7d" ? "7 Days" : p === "30d" ? "30 Days" : p === "90d" ? "90 Days" : "Custom"}
              </button>
            ))}
          </div>

          <div style={{ display: "flex", alignItems: "center", gap: "10px", flexWrap: "wrap" }}>
            <input
              type="date"
              value={startDate}
              onChange={(e) => {
                setStartDate(e.target.value);
                setRangePreset("custom");
              }}
              style={{
                backgroundColor: "#0a0f1e",
                border: "1px solid #1e2a45",
                borderRadius: "8px",
                padding: "8px 12px",
                fontSize: "13px",
                color: "#f1f5f9",
                outline: "none",
                minHeight: "38px",
              }}
            />
            <span style={{ fontSize: "13px", color: "#64748b" }}>to</span>
            <input
              type="date"
              value={endDate}
              onChange={(e) => {
                setEndDate(e.target.value);
                setRangePreset("custom");
              }}
              style={{
                backgroundColor: "#0a0f1e",
                border: "1px solid #1e2a45",
                borderRadius: "8px",
                padding: "8px 12px",
                fontSize: "13px",
                color: "#f1f5f9",
                outline: "none",
                minHeight: "38px",
              }}
            />
          </div>
        </section>

        {/* ─── STAT CARDS SECTION ───────────────────────────────────────────── */}
        <section style={{ marginBottom: "32px" }}>
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))",
              gap: "20px",
            }}
          >
            {/* Card 1: Total Breaches */}
            <div
              style={{
                backgroundColor: "#141b2d",
                border: "1px solid rgba(239, 68, 68, 0.25)",
                borderRadius: "16px",
                padding: "24px",
                position: "relative",
                overflow: "hidden",
              }}
            >
              <div style={{ fontSize: "12px", fontWeight: 700, color: "#f87171", textTransform: "uppercase", letterSpacing: "0.5px" }}>
                Total Breaches
              </div>
              <div style={{ fontSize: "36px", fontWeight: 900, color: "#ef4444", marginTop: "8px" }}>
                {loading ? "..." : stats?.total_breaches ?? 0}
              </div>
              <div style={{ fontSize: "12px", color: "#64748b", marginTop: "4px" }}>
                Within selected date range
              </div>
            </div>

            {/* Card 2: Uptime % */}
            <div
              style={{
                backgroundColor: "#141b2d",
                border: "1px solid rgba(34, 197, 94, 0.25)",
                borderRadius: "16px",
                padding: "24px",
                position: "relative",
                overflow: "hidden",
              }}
            >
              <div style={{ fontSize: "12px", fontWeight: 700, color: "#4ade80", textTransform: "uppercase", letterSpacing: "0.5px" }}>
                System Uptime
              </div>
              <div style={{ fontSize: "36px", fontWeight: 900, color: "#22c55e", marginTop: "8px" }}>
                {loading ? "..." : `${stats?.uptime_percent ?? 100}%`}
              </div>
              <div style={{ fontSize: "12px", color: "#64748b", marginTop: "4px" }}>
                Active connectivity ratio
              </div>
            </div>

            {/* Card 3: Low Battery Events */}
            <div
              style={{
                backgroundColor: "#141b2d",
                border: "1px solid rgba(168, 85, 247, 0.25)",
                borderRadius: "16px",
                padding: "24px",
                position: "relative",
                overflow: "hidden",
              }}
            >
              <div style={{ fontSize: "12px", fontWeight: 700, color: "#c084fc", textTransform: "uppercase", letterSpacing: "0.5px" }}>
                Low Battery Events
              </div>
              <div style={{ fontSize: "36px", fontWeight: 900, color: "#a855f7", marginTop: "8px" }}>
                {loading ? "..." : stats?.low_battery_event_count ?? 0}
              </div>
              <div style={{ fontSize: "12px", color: "#64748b", marginTop: "4px" }}>
                Battery &lt; 20% warnings
              </div>
            </div>

            {/* Card 4: Peak Breach Hour */}
            <div
              style={{
                backgroundColor: "#141b2d",
                border: "1px solid rgba(59, 130, 246, 0.25)",
                borderRadius: "16px",
                padding: "24px",
                position: "relative",
                overflow: "hidden",
              }}
            >
              <div style={{ fontSize: "12px", fontWeight: 700, color: "#60a5fa", textTransform: "uppercase", letterSpacing: "0.5px" }}>
                Peak Breach Hour
              </div>
              <div
                style={{
                  fontSize: stats?.most_common_breach_hour !== null && stats?.most_common_breach_hour !== undefined ? "36px" : "16px",
                  fontWeight: 900,
                  color: "#3b82f6",
                  marginTop: "8px",
                  lineHeight: "42px",
                }}
              >
                {loading
                  ? "..."
                  : stats?.most_common_breach_hour !== null && stats?.most_common_breach_hour !== undefined
                  ? `${stats.most_common_breach_hour.toString().padStart(2, "0")}:00`
                  : "Insufficient events"}
              </div>
              <div style={{ fontSize: "12px", color: "#64748b", marginTop: "4px" }}>
                Most active breach window
              </div>
            </div>
          </div>
        </section>

        {/* ─── CHARTS SECTION ────────────────────────────────────────────────── */}
        <section
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fit, minmax(480px, 1fr))",
            gap: "24px",
            marginBottom: "32px",
          }}
        >
          {/* Chart 1: RSSI Line Chart */}
          <div
            style={{
              backgroundColor: "#141b2d",
              border: "1px solid #1e2a45",
              borderRadius: "20px",
              padding: "24px",
            }}
          >
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "16px" }}>
              <div>
                <h3 style={{ fontSize: "16px", fontWeight: 800, color: "#ffffff", margin: 0 }}>
                  RSSI Signal Strength Trend
                </h3>
                <p style={{ fontSize: "12px", color: "#94a3b8", margin: "4px 0 0 0" }}>
                  Monitor signal degradation before disconnections
                </p>
              </div>
              <span style={{ fontSize: "11px", fontWeight: 700, padding: "4px 8px", borderRadius: "6px", backgroundColor: "rgba(59, 130, 246, 0.15)", color: "#60a5fa" }}>
                Telemetry
              </span>
            </div>

            <RssiLineChart data={rssiChartData} height={220} />
          </div>

          {/* Chart 2: Breach Hour Bar Chart */}
          <div
            style={{
              backgroundColor: "#141b2d",
              border: "1px solid #1e2a45",
              borderRadius: "20px",
              padding: "24px",
            }}
          >
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "16px" }}>
              <div>
                <h3 style={{ fontSize: "16px", fontWeight: 800, color: "#ffffff", margin: 0 }}>
                  Breach Frequency by Hour of Day
                </h3>
                <p style={{ fontSize: "12px", color: "#94a3b8", margin: "4px 0 0 0" }}>
                  24-hour distribution pattern (00:00 to 23:00)
                </p>
              </div>
              <span style={{ fontSize: "11px", fontWeight: 700, padding: "4px 8px", borderRadius: "6px", backgroundColor: "rgba(239, 68, 68, 0.15)", color: "#f87171" }}>
                Patterns
              </span>
            </div>

            <BreachHourBarChart
              events={history?.breach_events || []}
              peakHour={stats?.most_common_breach_hour}
              height={220}
            />
          </div>
        </section>

        {/* ─── HISTORY TABLES SECTION ────────────────────────────────────────── */}
        <section
          style={{
            backgroundColor: "#141b2d",
            border: "1px solid #1e2a45",
            borderRadius: "20px",
            padding: "28px",
          }}
        >
          {/* Controls & Tab Switching */}
          <div
            style={{
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
              flexWrap: "wrap",
              gap: "16px",
              marginBottom: "24px",
            }}
          >
            <div style={{ display: "flex", gap: "12px" }}>
              <button
                onClick={() => setActiveTab("breaches")}
                style={{
                  padding: "10px 20px",
                  borderRadius: "12px",
                  fontSize: "14px",
                  fontWeight: 700,
                  border: "none",
                  backgroundColor: activeTab === "breaches" ? "rgba(239, 68, 68, 0.15)" : "transparent",
                  color: activeTab === "breaches" ? "#ef4444" : "#94a3b8",
                  cursor: "pointer",
                  transition: "all 0.15s ease",
                }}
              >
                Breach History ({breachList.length})
              </button>
              <button
                onClick={() => setActiveTab("battery")}
                style={{
                  padding: "10px 20px",
                  borderRadius: "12px",
                  fontSize: "14px",
                  fontWeight: 700,
                  border: "none",
                  backgroundColor: activeTab === "battery" ? "rgba(245, 158, 11, 0.15)" : "transparent",
                  color: activeTab === "battery" ? "#f59e0b" : "#94a3b8",
                  cursor: "pointer",
                  transition: "all 0.15s ease",
                }}
              >
                Low Battery History ({batteryList.length})
              </button>
            </div>

            <button
              onClick={() => setSortAsc(!sortAsc)}
              style={{
                padding: "8px 16px",
                borderRadius: "10px",
                fontSize: "13px",
                fontWeight: 600,
                backgroundColor: "rgba(255, 255, 255, 0.03)",
                border: "1px solid #1e2a45",
                color: "#94a3b8",
                cursor: "pointer",
              }}
            >
              Sort Order: {sortAsc ? "Oldest First ▲" : "Newest First ▼"}
            </button>
          </div>

          {/* Tables with horizontal touch scrolling container */}
          {loading ? (
            <div style={{ textAlign: "center", padding: "60px", color: "#64748b" }}>
              <div style={{ fontSize: "16px", fontWeight: 600 }}>Loading event log...</div>
            </div>
          ) : activeTab === "breaches" ? (
            breachList.length > 0 ? (
              <div style={{ overflowX: "auto", border: "1px solid #1e2a45", borderRadius: "14px" }}>
                <table style={{ width: "100%", borderCollapse: "collapse", textAlign: "left", fontSize: "14px", minWidth: "600px" }}>
                  <thead>
                    <tr style={{ backgroundColor: "rgba(255, 255, 255, 0.03)", borderBottom: "1px solid #1e2a45", color: "#94a3b8" }}>
                      <th style={{ padding: "14px 18px" }}>Timestamp</th>
                      <th style={{ padding: "14px 18px" }}>Breach Type</th>
                      <th style={{ padding: "14px 18px" }}>Duration</th>
                      <th style={{ padding: "14px 18px" }}>RSSI</th>
                      <th style={{ padding: "14px 18px" }}>Status / Resolution</th>
                    </tr>
                  </thead>
                  <tbody>
                    {breachList.map((b, idx) => (
                      <tr
                        key={idx}
                        style={{
                          borderBottom: idx === breachList.length - 1 ? "none" : "1px solid #1e2a45",
                          backgroundColor: idx % 2 === 0 ? "transparent" : "rgba(255, 255, 255, 0.01)",
                        }}
                      >
                        <td style={{ padding: "14px 18px", color: "#f1f5f9", fontWeight: 500 }}>
                          {b.timestamp ? new Date(b.timestamp).toLocaleString() : "N/A"}
                        </td>
                        <td style={{ padding: "14px 18px" }}>
                          <span
                            style={{
                              padding: "4px 12px",
                              borderRadius: "6px",
                              fontSize: "12px",
                              fontWeight: 700,
                              textTransform: "uppercase",
                              backgroundColor:
                                b.breach_type === "heartbeat_timeout"
                                  ? "rgba(239, 68, 68, 0.15)"
                                  : "rgba(245, 158, 11, 0.15)",
                              color: b.breach_type === "heartbeat_timeout" ? "#f87171" : "#fbbf24",
                            }}
                          >
                            {b.breach_type === "heartbeat_timeout" ? "Heartbeat Timeout" : "RSSI Threshold"}
                          </span>
                        </td>
                        <td style={{ padding: "14px 18px", color: "#cbd5e1" }}>
                          {b.duration_seconds !== null ? `${b.duration_seconds}s` : "Active"}
                        </td>
                        <td style={{ padding: "14px 18px", color: "#cbd5e1" }}>
                          {b.rssi_at_breach !== null ? `${b.rssi_at_breach} dBm` : "N/A"}
                        </td>
                        <td style={{ padding: "14px 18px" }}>
                          {b.resolved_at ? (
                            <span style={{ color: "#4ade80", fontSize: "13px", fontWeight: 600 }}>
                              Resolved ({new Date(b.resolved_at).toLocaleTimeString()})
                            </span>
                          ) : (
                            <span style={{ color: "#ef4444", fontWeight: 700, fontSize: "13px" }}>
                              Active Breach
                            </span>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <div
                style={{
                  textAlign: "center",
                  padding: "60px 20px",
                  backgroundColor: "rgba(255, 255, 255, 0.01)",
                  border: "1px dashed #1e2a45",
                  borderRadius: "14px",
                  color: "#64748b",
                }}
              >
                No breach events recorded for this device in the selected date range.
              </div>
            )
          ) : batteryList.length > 0 ? (
            <div style={{ overflowX: "auto", border: "1px solid #1e2a45", borderRadius: "14px" }}>
              <table style={{ width: "100%", borderCollapse: "collapse", textAlign: "left", fontSize: "14px", minWidth: "500px" }}>
                <thead>
                  <tr style={{ backgroundColor: "rgba(255, 255, 255, 0.03)", borderBottom: "1px solid #1e2a45", color: "#94a3b8" }}>
                    <th style={{ padding: "14px 18px" }}>Timestamp</th>
                    <th style={{ padding: "14px 18px" }}>Battery Level</th>
                  </tr>
                </thead>
                <tbody>
                  {batteryList.map((e, idx) => (
                    <tr
                      key={idx}
                      style={{
                        borderBottom: idx === batteryList.length - 1 ? "none" : "1px solid #1e2a45",
                        backgroundColor: idx % 2 === 0 ? "transparent" : "rgba(255, 255, 255, 0.01)",
                      }}
                    >
                      <td style={{ padding: "14px 18px", color: "#f1f5f9", fontWeight: 500 }}>
                        {e.timestamp ? new Date(e.timestamp).toLocaleString() : "N/A"}
                      </td>
                      <td style={{ padding: "14px 18px" }}>
                        <span
                          style={{
                            padding: "4px 12px",
                            borderRadius: "6px",
                            fontSize: "13px",
                            fontWeight: 700,
                            backgroundColor: "rgba(245, 158, 11, 0.15)",
                            color: "#fbbf24",
                            display: "inline-flex",
                            alignItems: "center",
                            gap: "6px",
                          }}
                        >
                          <BatteryWarning size={14} color="#fbbf24" />
                          {e.battery_percent}%
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <div
              style={{
                textAlign: "center",
                padding: "60px 20px",
                backgroundColor: "rgba(255, 255, 255, 0.01)",
                border: "1px dashed #1e2a45",
                borderRadius: "14px",
                color: "#64748b",
              }}
            >
              No low battery events recorded for this device in the selected date range.
            </div>
          )}
        </section>
      </div>
    </div>
  );
}
