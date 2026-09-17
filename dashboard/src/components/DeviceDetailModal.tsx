"use client";
import React, { useState, useEffect, useCallback } from "react";

type DeviceIdentity = {
  deviceId: string;
  roomId?: string;
  status?: string;
  battery?: number;
  rssi?: number;
  lastSeen?: string;
  staffName?: string;
  registeredBy?: string;
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
  avg_battery_drain_per_hour: number | null;
  low_battery_event_count: number;
  most_common_breach_hour: number | null;
};

interface DeviceDetailModalProps {
  device: DeviceIdentity | null;
  isOpen: boolean;
  onClose: () => void;
  apiBaseUrl?: string;
}

export function DeviceDetailModal({
  device,
  isOpen,
  onClose,
  apiBaseUrl = process.env.NEXT_PUBLIC_API_URL || "https://hotel-backend-zqc1.onrender.com",
}: DeviceDetailModalProps) {
  // Date range presets (default 30 days)
  const [rangePreset, setRangePreset] = useState<"7d" | "30d" | "90d" | "custom">("30d");
  const [startDate, setStartDate] = useState<string>("");
  const [endDate, setEndDate] = useState<string>("");

  const [history, setHistory] = useState<{
    breach_events: BreachEvent[];
    low_battery_events: LowBatteryEvent[];
  } | null>(null);

  const [stats, setStats] = useState<DeviceStats | null>(null);
  const [loading, setLoading] = useState<boolean>(false);
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
    if (isOpen) {
      applyPreset("30d");
    }
  }, [isOpen, applyPreset]);

  // Fetch history & stats
  const fetchData = useCallback(async () => {
    if (!device?.deviceId) return;
    setLoading(true);

    try {
      let queryParams = "";
      if (startDate && endDate) {
        queryParams = `?start_date=${startDate}&end_date=${endDate}`;
      }

      const [histRes, statsRes] = await Promise.all([
        fetch(`${apiBaseUrl}/api/devices/${device.deviceId}/history${queryParams}`),
        fetch(`${apiBaseUrl}/api/devices/${device.deviceId}/stats${queryParams}`),
      ]);

      if (histRes.ok) {
        const histData = await histRes.json();
        setHistory(histData);
      }
      if (statsRes.ok) {
        const statsData = await statsRes.json();
        setStats(statsData);
      }
    } catch (err) {
      console.error("Failed to fetch device detail history/stats:", err);
    } finally {
      setLoading(false);
    }
  }, [device?.deviceId, startDate, endDate, apiBaseUrl]);

  useEffect(() => {
    if (isOpen && device?.deviceId) {
      fetchData();
    }
  }, [isOpen, device?.deviceId, startDate, endDate, fetchData]);

  // PDF download handler
  const handleDownloadPdf = async () => {
    if (!device?.deviceId) return;
    setDownloadingPdf(true);
    try {
      let queryParams = "";
      if (startDate && endDate) {
        queryParams = `?start_date=${startDate}&end_date=${endDate}`;
      }
      const pdfUrl = `${apiBaseUrl}/api/devices/${device.deviceId}/report.pdf${queryParams}`;

      const res = await fetch(pdfUrl);
      if (!res.ok) throw new Error("Failed to generate PDF");

      const blob = await res.blob();
      const downloadUrl = window.URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = downloadUrl;
      a.download = `device_${device.deviceId}_report.pdf`;
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

  if (!isOpen || !device) return null;

  const status = (device.status || "ok").toLowerCase();
  const isBreach = status === "breach";
  const isOffline = status === "offline";
  const statusColor = isBreach ? "#ef4444" : isOffline ? "#f59e0b" : "#22c55e";
  const statusLabel = isBreach ? "BREACH DETECTED" : isOffline ? "OFFLINE" : "SECURE";

  const formattedRssi =
    device.rssi !== undefined && device.rssi !== null
      ? device.rssi === -127
        ? "No signal"
        : `${device.rssi} dBm`
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

  return (
    <div
      style={{
        position: "fixed",
        inset: 0,
        zIndex: 9999,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        backgroundColor: "rgba(15, 23, 42, 0.8)",
        backdropFilter: "blur(8px)",
        padding: "20px",
      }}
      onClick={onClose}
    >
      <div
        style={{
          width: "100%",
          maxWidth: "920px",
          maxHeight: "90vh",
          overflowY: "auto",
          backgroundColor: "#141b2d",
          border: "1px solid #1e2a45",
          borderRadius: "20px",
          boxShadow: "0 25px 50px -12px rgba(0, 0, 0, 0.6)",
          color: "#f8fafc",
          padding: "24px",
          position: "relative",
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header Bar */}
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: "20px" }}>
          <div>
            <div style={{ display: "flex", alignItems: "center", gap: "10px", marginBottom: "6px" }}>
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
              <span style={{ fontSize: "14px", color: "#94a3b8" }}>
                Room {device.roomId || "Unassigned"}
              </span>
            </div>
            <h2 style={{ fontSize: "24px", fontWeight: 800, color: "#ffffff", margin: 0 }}>
              {device.deviceId}
            </h2>
          </div>

          <div style={{ display: "flex", alignItems: "center", gap: "12px" }}>
            <button
              onClick={handleDownloadPdf}
              disabled={downloadingPdf}
              style={{
                display: "inline-flex",
                alignItems: "center",
                gap: "8px",
                padding: "8px 16px",
                borderRadius: "10px",
                fontSize: "13px",
                fontWeight: 600,
                backgroundColor: downloadingPdf ? "#334155" : "#3b82f6",
                color: "#ffffff",
                border: "none",
                cursor: downloadingPdf ? "not-allowed" : "pointer",
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

            <button
              onClick={onClose}
              style={{
                background: "rgba(255, 255, 255, 0.05)",
                border: "1px solid rgba(255, 255, 255, 0.1)",
                borderRadius: "10px",
                width: "36px",
                height: "36px",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                color: "#94a3b8",
                cursor: "pointer",
                fontSize: "18px",
              }}
            >
              ✕
            </button>
          </div>
        </div>

        {/* Device Quick Identity Bar */}
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fit, minmax(140px, 1fr))",
            gap: "12px",
            backgroundColor: "rgba(255, 255, 255, 0.02)",
            border: "1px solid #1e2a45",
            borderRadius: "12px",
            padding: "12px 16px",
            marginBottom: "20px",
          }}
        >
          <div>
            <div style={{ fontSize: "11px", color: "#64748b" }}>Battery Level</div>
            <div style={{ fontSize: "15px", fontWeight: 700, color: "#f8fafc" }}>
              {device.battery !== undefined && device.battery !== null ? `${device.battery}%` : "N/A"}
            </div>
          </div>
          <div>
            <div style={{ fontSize: "11px", color: "#64748b" }}>Signal (RSSI)</div>
            <div style={{ fontSize: "15px", fontWeight: 700, color: "#f8fafc" }}>
              {formattedRssi}
            </div>
          </div>
          <div>
            <div style={{ fontSize: "11px", color: "#64748b" }}>Assigned Staff</div>
            <div style={{ fontSize: "15px", fontWeight: 700, color: "#f8fafc" }}>
              {device.staffName || device.registeredBy || "Unassigned"}
            </div>
          </div>
          <div>
            <div style={{ fontSize: "11px", color: "#64748b" }}>Last Seen</div>
            <div style={{ fontSize: "14px", fontWeight: 600, color: "#94a3b8" }}>
              {device.lastSeen ? new Date(device.lastSeen).toLocaleString() : "Unknown"}
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
            gap: "12px",
            marginBottom: "20px",
            paddingBottom: "16px",
            borderBottom: "1px solid #1e2a45",
          }}
        >
          <div style={{ display: "flex", alignItems: "center", gap: "6px" }}>
            <span style={{ fontSize: "13px", fontWeight: 600, color: "#94a3b8", marginRight: "6px" }}>
              Range:
            </span>
            {(["7d", "30d", "90d", "custom"] as const).map((p) => (
              <button
                key={p}
                onClick={() => applyPreset(p)}
                style={{
                  padding: "5px 12px",
                  borderRadius: "8px",
                  fontSize: "12px",
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

          <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
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
                padding: "4px 8px",
                fontSize: "12px",
                color: "#f8fafc",
              }}
            />
            <span style={{ fontSize: "12px", color: "#64748b" }}>to</span>
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
                padding: "4px 8px",
                fontSize: "12px",
                color: "#f8fafc",
              }}
            />
          </div>
        </div>

        {/* 5 Stat Cards Section */}
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))",
            gap: "12px",
            marginBottom: "24px",
          }}
        >
          {/* Card 1: Total Breaches */}
          <div
            style={{
              backgroundColor: "rgba(239, 68, 68, 0.05)",
              border: "1px solid rgba(239, 68, 68, 0.2)",
              borderRadius: "12px",
              padding: "14px",
            }}
          >
            <div style={{ fontSize: "11px", fontWeight: 600, color: "#f87171", textTransform: "uppercase" }}>
              Total Breaches
            </div>
            <div style={{ fontSize: "24px", fontWeight: 800, color: "#ef4444", marginTop: "4px" }}>
              {loading ? "..." : stats?.total_breaches ?? 0}
            </div>
          </div>

          {/* Card 2: Uptime % */}
          <div
            style={{
              backgroundColor: "rgba(34, 197, 94, 0.05)",
              border: "1px solid rgba(34, 197, 94, 0.2)",
              borderRadius: "12px",
              padding: "14px",
            }}
          >
            <div style={{ fontSize: "11px", fontWeight: 600, color: "#4ade80", textTransform: "uppercase" }}>
              Uptime %
            </div>
            <div style={{ fontSize: "24px", fontWeight: 800, color: "#22c55e", marginTop: "4px" }}>
              {loading ? "..." : `${stats?.uptime_percent ?? 100}%`}
            </div>
          </div>

          {/* Card 3: Avg Battery Drain/hr */}
          <div
            style={{
              backgroundColor: "rgba(245, 158, 11, 0.05)",
              border: "1px solid rgba(245, 158, 11, 0.2)",
              borderRadius: "12px",
              padding: "14px",
            }}
          >
            <div style={{ fontSize: "11px", fontWeight: 600, color: "#fbbf24", textTransform: "uppercase" }}>
              Avg Battery Drain
            </div>
            <div style={{ fontSize: "20px", fontWeight: 800, color: "#f59e0b", marginTop: "4px" }}>
              {loading
                ? "..."
                : stats?.avg_battery_drain_per_hour !== null && stats?.avg_battery_drain_per_hour !== undefined
                ? `${stats.avg_battery_drain_per_hour}% / hr`
                : "N/A"}
            </div>
          </div>

          {/* Card 4: Low Battery Events */}
          <div
            style={{
              backgroundColor: "rgba(168, 85, 247, 0.05)",
              border: "1px solid rgba(168, 85, 247, 0.2)",
              borderRadius: "12px",
              padding: "14px",
            }}
          >
            <div style={{ fontSize: "11px", fontWeight: 600, color: "#c084fc", textTransform: "uppercase" }}>
              Low Battery Events
            </div>
            <div style={{ fontSize: "24px", fontWeight: 800, color: "#a855f7", marginTop: "4px" }}>
              {loading ? "..." : stats?.low_battery_event_count ?? 0}
            </div>
          </div>

          {/* Card 5: Peak Breach Hour */}
          <div
            style={{
              backgroundColor: "rgba(59, 130, 246, 0.05)",
              border: "1px solid rgba(59, 130, 246, 0.2)",
              borderRadius: "12px",
              padding: "14px",
            }}
          >
            <div style={{ fontSize: "11px", fontWeight: 600, color: "#60a5fa", textTransform: "uppercase" }}>
              Peak Breach Hour
            </div>
            <div style={{ fontSize: "20px", fontWeight: 800, color: "#3b82f6", marginTop: "4px" }}>
              {loading
                ? "..."
                : stats?.most_common_breach_hour !== null && stats?.most_common_breach_hour !== undefined
                ? `${stats.most_common_breach_hour}:00`
                : "Insuff. Data"}
            </div>
          </div>
        </div>

        {/* Tabs for Breach History vs Low Battery History */}
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "16px" }}>
          <div style={{ display: "flex", gap: "8px" }}>
            <button
              onClick={() => setActiveTab("breaches")}
              style={{
                padding: "8px 16px",
                borderRadius: "10px",
                fontSize: "13px",
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
                padding: "8px 16px",
                borderRadius: "10px",
                fontSize: "13px",
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
              padding: "6px 12px",
              borderRadius: "8px",
              fontSize: "12px",
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
          <div style={{ textAlign: "center", padding: "40px", color: "#64748b" }}>
            <div style={{ fontSize: "14px", fontWeight: 600 }}>Loading event history...</div>
          </div>
        ) : activeTab === "breaches" ? (
          breachList.length > 0 ? (
            <div style={{ overflowX: "auto", border: "1px solid #1e2a45", borderRadius: "12px" }}>
              <table style={{ width: "100%", borderCollapse: "collapse", textAlign: "left", fontSize: "13px" }}>
                <thead>
                  <tr style={{ backgroundColor: "rgba(255, 255, 255, 0.03)", borderBottom: "1px solid #1e2a45", color: "#94a3b8" }}>
                    <th style={{ padding: "12px" }}>Timestamp</th>
                    <th style={{ padding: "12px" }}>Breach Type</th>
                    <th style={{ padding: "12px" }}>Duration</th>
                    <th style={{ padding: "12px" }}>RSSI</th>
                    <th style={{ padding: "12px" }}>Status / Resolved At</th>
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
                      <td style={{ padding: "12px", color: "#f8fafc", fontWeight: 500 }}>
                        {b.timestamp ? new Date(b.timestamp).toLocaleString() : "N/A"}
                      </td>
                      <td style={{ padding: "12px" }}>
                        <span
                          style={{
                            padding: "3px 8px",
                            borderRadius: "6px",
                            fontSize: "11px",
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
                      <td style={{ padding: "12px", color: "#cbd5e1" }}>
                        {b.duration_seconds !== null ? `${b.duration_seconds}s` : "Active"}
                      </td>
                      <td style={{ padding: "12px", color: "#cbd5e1" }}>
                        {b.rssi_at_breach !== null ? `${b.rssi_at_breach} dBm` : "N/A"}
                      </td>
                      <td style={{ padding: "12px" }}>
                        {b.resolved_at ? (
                          <span style={{ color: "#4ade80", fontSize: "12px" }}>
                            Resolved ({new Date(b.resolved_at).toLocaleTimeString()})
                          </span>
                        ) : (
                          <span style={{ color: "#ef4444", fontWeight: 700, fontSize: "12px" }}>
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
                padding: "40px",
                backgroundColor: "rgba(255, 255, 255, 0.01)",
                border: "1px dashed #1e2a45",
                borderRadius: "12px",
                color: "#64748b",
              }}
            >
              No breach events recorded for this device in the selected date range.
            </div>
          )
        ) : batteryList.length > 0 ? (
          <div style={{ overflowX: "auto", border: "1px solid #1e2a45", borderRadius: "12px" }}>
            <table style={{ width: "100%", borderCollapse: "collapse", textAlign: "left", fontSize: "13px" }}>
              <thead>
                <tr style={{ backgroundColor: "rgba(255, 255, 255, 0.03)", borderBottom: "1px solid #1e2a45", color: "#94a3b8" }}>
                  <th style={{ padding: "12px" }}>Timestamp</th>
                  <th style={{ padding: "12px" }}>Battery Level</th>
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
                    <td style={{ padding: "12px", color: "#f8fafc", fontWeight: 500 }}>
                      {e.timestamp ? new Date(e.timestamp).toLocaleString() : "N/A"}
                    </td>
                    <td style={{ padding: "12px" }}>
                      <span
                        style={{
                          padding: "3px 10px",
                          borderRadius: "6px",
                          fontSize: "12px",
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
              padding: "40px",
              backgroundColor: "rgba(255, 255, 255, 0.01)",
              border: "1px dashed #1e2a45",
              borderRadius: "12px",
              color: "#64748b",
            }}
          >
            No low battery events recorded for this device in the selected date range.
          </div>
        )}
      </div>
    </div>
  );
}
