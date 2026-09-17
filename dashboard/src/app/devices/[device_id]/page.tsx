"use client";

import React, { useState, useEffect, useCallback } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";

type DeviceIdentity = {
  device_id: string;
  room?: string;
  status?: string;
  battery?: number;
  rssi?: number;
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
  const statusLabel = isBreach ? "BREACH DETECTED" : isOffline ? "OFFLINE" : "SECURE";

  const formattedRssi =
    identity?.rssi !== undefined && identity?.rssi !== null
      ? identity.rssi === -127
        ? "No signal"
        : `${identity.rssi} dBm`
      : "No data";

  // Sort events
  const breachList = [...(history?.breach_events || [])].sort((a, b) => {
    const tA = new Date(a.timestamp).getTime();
    const tB = new Date(b.timestamp).getTime();
    return sortAsc ? tA - tB : tB - tA;
  });

  const batteryList = [...(history?.low_battery_events || [])].sort((a, b) => {
    const tA = new Date(a.timestamp).getTime();
    const tB = new Date(b.timestamp).getTime();
    return sortAsc ? tA - tB : tB - tA;
  });

  if (notFound) {
    return (
      <div style={{ minHeight: "100vh", backgroundColor: "#0f172a", color: "#f8fafc", padding: "40px 20px" }}>
        <div style={{ maxWidth: "800px", margin: "0 auto", textAlign: "center" }}>
          <Link
            href="/"
            style={{
              display: "inline-flex",
              alignItems: "center",
              gap: "8px",
              color: "#60a5fa",
              fontSize: "14px",
              fontWeight: 600,
              textDecoration: "none",
              marginBottom: "24px",
            }}
          >
            ← Back to Device Fleet
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
                padding: "10px 20px",
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
    <div style={{ minHeight: "100vh", backgroundColor: "#0f172a", color: "#f8fafc", padding: "32px 20px" }}>
      <div style={{ maxWidth: "1100px", margin: "0 auto" }}>
        {/* Navigation / Back Button */}
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "24px" }}>
          <Link
            href="/"
            style={{
              display: "inline-flex",
              alignItems: "center",
              gap: "8px",
              padding: "8px 16px",
              backgroundColor: "rgba(255, 255, 255, 0.03)",
              border: "1px solid #1e2a45",
              borderRadius: "10px",
              color: "#94a3b8",
              fontSize: "14px",
              fontWeight: 600,
              textDecoration: "none",
              transition: "all 0.2s ease",
            }}
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
              <line x1="19" y1="12" x2="5" y2="12" />
              <polyline points="12 19 5 12 12 5" />
            </svg>
            Back to Devices
          </Link>

          <button
            onClick={handleDownloadPdf}
            disabled={downloadingPdf || loading}
            style={{
              display: "inline-flex",
              alignItems: "center",
              gap: "8px",
              padding: "10px 20px",
              borderRadius: "10px",
              fontSize: "14px",
              fontWeight: 600,
              backgroundColor: downloadingPdf ? "#334155" : "#3b82f6",
              color: "#ffffff",
              border: "none",
              cursor: downloadingPdf || loading ? "not-allowed" : "pointer",
              transition: "all 0.2s ease",
              boxShadow: "0 4px 12px rgba(59, 130, 246, 0.3)",
            }}
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
              <polyline points="7 10 12 15 17 10" />
              <line x1="12" y1="15" x2="12" y2="3" />
            </svg>
            {downloadingPdf ? "Generating PDF..." : "Download PDF Report"}
          </button>
        </div>

        {/* Main Content Container */}
        <div
          style={{
            backgroundColor: "#141b2d",
            border: "1px solid #1e2a45",
            borderRadius: "20px",
            boxShadow: "0 25px 50px -12px rgba(0, 0, 0, 0.5)",
            padding: "32px",
          }}
        >
          {/* Header Identity Section */}
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: "24px" }}>
            <div>
              <div style={{ display: "flex", alignItems: "center", gap: "12px", marginBottom: "8px" }}>
                <span
                  style={{
                    display: "inline-flex",
                    alignItems: "center",
                    gap: "6px",
                    padding: "4px 12px",
                    borderRadius: "20px",
                    fontSize: "12px",
                    fontWeight: 700,
                    letterSpacing: "0.5px",
                    backgroundColor: `${statusColor}15`,
                    border: `1px solid ${statusColor}40`,
                    color: statusColor,
                  }}
                >
                  <span
                    style={{
                      width: "8px",
                      height: "8px",
                      borderRadius: "50%",
                      backgroundColor: statusColor,
                      boxShadow: `0 0 8px ${statusColor}`,
                    }}
                  />
                  {statusLabel}
                </span>
                <span style={{ fontSize: "15px", color: "#94a3b8", fontWeight: 500 }}>
                  Room {identity?.room || "Unassigned"}
                </span>
              </div>
              <h1 style={{ fontSize: "32px", fontWeight: 800, color: "#ffffff", margin: 0, fontFamily: "monospace" }}>
                {deviceId}
              </h1>
            </div>
          </div>

          {/* Quick Identity Grid */}
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "repeat(auto-fit, minmax(160px, 1fr))",
              gap: "16px",
              backgroundColor: "rgba(255, 255, 255, 0.02)",
              border: "1px solid #1e2a45",
              borderRadius: "14px",
              padding: "16px 20px",
              marginBottom: "28px",
            }}
          >
            <div>
              <div style={{ fontSize: "12px", color: "#64748b", marginBottom: "4px" }}>Battery Level</div>
              <div style={{ fontSize: "18px", fontWeight: 700, color: "#f8fafc" }}>
                {identity?.battery !== undefined && identity?.battery !== null ? `${identity.battery}%` : "N/A"}
              </div>
            </div>
            <div>
              <div style={{ fontSize: "12px", color: "#64748b", marginBottom: "4px" }}>Signal (RSSI)</div>
              <div style={{ fontSize: "18px", fontWeight: 700, color: "#f8fafc" }}>
                {formattedRssi}
              </div>
            </div>
            <div>
              <div style={{ fontSize: "12px", color: "#64748b", marginBottom: "4px" }}>Assigned Staff</div>
              <div style={{ fontSize: "18px", fontWeight: 700, color: "#f8fafc" }}>
                {identity?.assigned_by || "Unassigned"}
              </div>
            </div>
            <div>
              <div style={{ fontSize: "12px", color: "#64748b", marginBottom: "4px" }}>Status Indicator</div>
              <div style={{ fontSize: "16px", fontWeight: 600, color: statusColor }}>
                {statusLabel}
              </div>
            </div>
          </div>

          {/* Date Range Selector Bar */}
          <div
            style={{
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
              flexWrap: "wrap",
              gap: "16px",
              marginBottom: "28px",
              paddingBottom: "20px",
              borderBottom: "1px solid #1e2a45",
            }}
          >
            <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
              <span style={{ fontSize: "14px", fontWeight: 600, color: "#94a3b8", marginRight: "8px" }}>
                Date Range:
              </span>
              {(["7d", "30d", "90d", "custom"] as const).map((p) => (
                <button
                  key={p}
                  onClick={() => applyPreset(p)}
                  style={{
                    padding: "6px 14px",
                    borderRadius: "8px",
                    fontSize: "13px",
                    fontWeight: 600,
                    border: rangePreset === p ? "1px solid #3b82f6" : "1px solid #1e2a45",
                    backgroundColor: rangePreset === p ? "rgba(59, 130, 246, 0.15)" : "transparent",
                    color: rangePreset === p ? "#60a5fa" : "#94a3b8",
                    cursor: "pointer",
                  }}
                >
                  {p === "7d" ? "7 Days" : p === "30d" ? "30 Days" : p === "90d" ? "90 Days" : "Custom"}
                </button>
              ))}
            </div>

            <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
              <input
                type="date"
                value={startDate}
                onChange={(e) => {
                  setStartDate(e.target.value);
                  setRangePreset("custom");
                }}
                style={{
                  backgroundColor: "#0f172a",
                  border: "1px solid #1e2a45",
                  borderRadius: "8px",
                  padding: "6px 10px",
                  fontSize: "13px",
                  color: "#f8fafc",
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
                  backgroundColor: "#0f172a",
                  border: "1px solid #1e2a45",
                  borderRadius: "8px",
                  padding: "6px 10px",
                  fontSize: "13px",
                  color: "#f8fafc",
                }}
              />
            </div>
          </div>

          {/* 4 Stat Cards Section (Battery Drain Removed Entirely) */}
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))",
              gap: "16px",
              marginBottom: "32px",
            }}
          >
            {/* Card 1: Total Breaches */}
            <div
              style={{
                backgroundColor: "rgba(239, 68, 68, 0.05)",
                border: "1px solid rgba(239, 68, 68, 0.2)",
                borderRadius: "14px",
                padding: "18px",
              }}
            >
              <div style={{ fontSize: "12px", fontWeight: 700, color: "#f87171", textTransform: "uppercase", letterSpacing: "0.5px" }}>
                Total Breaches
              </div>
              <div style={{ fontSize: "28px", fontWeight: 800, color: "#ef4444", marginTop: "6px" }}>
                {loading ? "..." : stats?.total_breaches ?? 0}
              </div>
            </div>

            {/* Card 2: Uptime % */}
            <div
              style={{
                backgroundColor: "rgba(34, 197, 94, 0.05)",
                border: "1px solid rgba(34, 197, 94, 0.2)",
                borderRadius: "14px",
                padding: "18px",
              }}
            >
              <div style={{ fontSize: "12px", fontWeight: 700, color: "#4ade80", textTransform: "uppercase", letterSpacing: "0.5px" }}>
                Uptime %
              </div>
              <div style={{ fontSize: "28px", fontWeight: 800, color: "#22c55e", marginTop: "6px" }}>
                {loading ? "..." : `${stats?.uptime_percent ?? 100}%`}
              </div>
            </div>

            {/* Card 3: Low Battery Events */}
            <div
              style={{
                backgroundColor: "rgba(168, 85, 247, 0.05)",
                border: "1px solid rgba(168, 85, 247, 0.2)",
                borderRadius: "14px",
                padding: "18px",
              }}
            >
              <div style={{ fontSize: "12px", fontWeight: 700, color: "#c084fc", textTransform: "uppercase", letterSpacing: "0.5px" }}>
                Low Battery Events
              </div>
              <div style={{ fontSize: "28px", fontWeight: 800, color: "#a855f7", marginTop: "6px" }}>
                {loading ? "..." : stats?.low_battery_event_count ?? 0}
              </div>
            </div>

            {/* Card 4: Peak Breach Hour */}
            <div
              style={{
                backgroundColor: "rgba(59, 130, 246, 0.05)",
                border: "1px solid rgba(59, 130, 246, 0.2)",
                borderRadius: "14px",
                padding: "18px",
              }}
            >
              <div style={{ fontSize: "12px", fontWeight: 700, color: "#60a5fa", textTransform: "uppercase", letterSpacing: "0.5px" }}>
                Peak Breach Hour
              </div>
              <div
                style={{
                  fontSize: stats?.most_common_breach_hour !== null && stats?.most_common_breach_hour !== undefined ? "28px" : "15px",
                  fontWeight: 800,
                  color: "#3b82f6",
                  marginTop: "6px",
                  lineHeight: "34px",
                }}
              >
                {loading
                  ? "..."
                  : stats?.most_common_breach_hour !== null && stats?.most_common_breach_hour !== undefined
                  ? `${stats.most_common_breach_hour}:00`
                  : "Not enough activity yet"}
              </div>
            </div>
          </div>

          {/* History Section Tabs */}
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "20px" }}>
            <div style={{ display: "flex", gap: "10px" }}>
              <button
                onClick={() => setActiveTab("breaches")}
                style={{
                  padding: "10px 20px",
                  borderRadius: "10px",
                  fontSize: "14px",
                  fontWeight: 700,
                  border: "none",
                  backgroundColor: activeTab === "breaches" ? "rgba(239, 68, 68, 0.15)" : "transparent",
                  color: activeTab === "breaches" ? "#ef4444" : "#94a3b8",
                  cursor: "pointer",
                }}
              >
                Breach History ({breachList.length})
              </button>
              <button
                onClick={() => setActiveTab("battery")}
                style={{
                  padding: "10px 20px",
                  borderRadius: "10px",
                  fontSize: "14px",
                  fontWeight: 700,
                  border: "none",
                  backgroundColor: activeTab === "battery" ? "rgba(245, 158, 11, 0.15)" : "transparent",
                  color: activeTab === "battery" ? "#f59e0b" : "#94a3b8",
                  cursor: "pointer",
                }}
              >
                Low Battery History ({batteryList.length})
              </button>
            </div>

            <button
              onClick={() => setSortAsc(!sortAsc)}
              style={{
                padding: "8px 14px",
                borderRadius: "8px",
                fontSize: "13px",
                fontWeight: 600,
                backgroundColor: "rgba(255, 255, 255, 0.05)",
                border: "1px solid #1e2a45",
                color: "#94a3b8",
                cursor: "pointer",
              }}
            >
              Sort Date: {sortAsc ? "Oldest First ▲" : "Newest First ▼"}
            </button>
          </div>

          {/* History Tables */}
          {loading ? (
            <div style={{ textAlign: "center", padding: "60px", color: "#64748b" }}>
              <div style={{ fontSize: "16px", fontWeight: 600 }}>Loading event history...</div>
            </div>
          ) : activeTab === "breaches" ? (
            breachList.length > 0 ? (
              <div style={{ overflowX: "auto", border: "1px solid #1e2a45", borderRadius: "14px" }}>
                <table style={{ width: "100%", borderCollapse: "collapse", textAlign: "left", fontSize: "14px" }}>
                  <thead>
                    <tr style={{ backgroundColor: "rgba(255, 255, 255, 0.03)", borderBottom: "1px solid #1e2a45", color: "#94a3b8" }}>
                      <th style={{ padding: "14px 16px" }}>Timestamp (IST)</th>
                      <th style={{ padding: "14px 16px" }}>Breach Type</th>
                      <th style={{ padding: "14px 16px" }}>Duration</th>
                      <th style={{ padding: "14px 16px" }}>RSSI</th>
                      <th style={{ padding: "14px 16px" }}>Status / Resolved At</th>
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
                        <td style={{ padding: "14px 16px", color: "#f8fafc", fontWeight: 500 }}>
                          {b.timestamp ? new Date(b.timestamp).toLocaleString() : "N/A"}
                        </td>
                        <td style={{ padding: "14px 16px" }}>
                          <span
                            style={{
                              padding: "4px 10px",
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
                        <td style={{ padding: "14px 16px", color: "#cbd5e1" }}>
                          {b.duration_seconds !== null ? `${b.duration_seconds}s` : "Active"}
                        </td>
                        <td style={{ padding: "14px 16px", color: "#cbd5e1" }}>
                          {b.rssi_at_breach !== null ? `${b.rssi_at_breach} dBm` : "N/A"}
                        </td>
                        <td style={{ padding: "14px 16px" }}>
                          {b.resolved_at ? (
                            <span style={{ color: "#4ade80", fontSize: "13px" }}>
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
              <table style={{ width: "100%", borderCollapse: "collapse", textAlign: "left", fontSize: "14px" }}>
                <thead>
                  <tr style={{ backgroundColor: "rgba(255, 255, 255, 0.03)", borderBottom: "1px solid #1e2a45", color: "#94a3b8" }}>
                    <th style={{ padding: "14px 16px" }}>Timestamp (IST)</th>
                    <th style={{ padding: "14px 16px" }}>Battery Level</th>
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
                      <td style={{ padding: "14px 16px", color: "#f8fafc", fontWeight: 500 }}>
                        {e.timestamp ? new Date(e.timestamp).toLocaleString() : "N/A"}
                      </td>
                      <td style={{ padding: "14px 16px" }}>
                        <span
                          style={{
                            padding: "4px 12px",
                            borderRadius: "6px",
                            fontSize: "13px",
                            fontWeight: 700,
                            backgroundColor: "rgba(245, 158, 11, 0.15)",
                            color: "#fbbf24",
                          }}
                        >
                          ⚠️ {e.battery_percent}%
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
        </div>
      </div>
    </div>
  );
}
