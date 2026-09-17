"use client";

import React, { useState } from "react";

interface BreachHourBarChartProps {
  events: Array<{ timestamp: string }>;
  peakHour?: number | null;
  height?: number;
}

export default function BreachHourBarChart({
  events,
  peakHour = null,
  height = 220,
}: BreachHourBarChartProps) {
  const [hoveredHour, setHoveredHour] = useState<number | null>(null);

  // Compute 24-hour breach counts (0 to 23)
  const hourCounts = new Array(24).fill(0);

  events.forEach((evt) => {
    if (!evt.timestamp) return;
    try {
      const dt = new Date(evt.timestamp);
      const hour = dt.getHours(); // 0 to 23 local time
      if (hour >= 0 && hour < 24) {
        hourCounts[hour] += 1;
      }
    } catch {
      // ignore invalid date
    }
  });

  const maxCount = Math.max(...hourCounts, 1);
  const totalBreachesInChart = hourCounts.reduce((a, b) => a + b, 0);

  // SVG dimensions
  const paddingLeft = 35;
  const paddingRight = 15;
  const paddingTop = 25;
  const paddingBottom = 35;
  const viewBoxWidth = 600;
  const viewBoxHeight = height;

  const chartWidth = viewBoxWidth - paddingLeft - paddingRight;
  const chartHeight = viewBoxHeight - paddingTop - paddingBottom;

  const barGap = 4;
  const totalGaps = 23 * barGap;
  const barWidth = Math.max(4, (chartWidth - totalGaps) / 24);

  // Y-axis grid ticks (e.g., 0, maxCount/2, maxCount)
  const yTicks = [maxCount, Math.ceil(maxCount / 2), 0];

  return (
    <div style={{ position: "relative", width: "100%" }}>
      <svg
        viewBox={`0 0 ${viewBoxWidth} ${viewBoxHeight}`}
        style={{ width: "100%", height: "auto", overflow: "visible" }}
      >
        <defs>
          <linearGradient id="barGradNormal" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#3b82f6" stopOpacity="0.9" />
            <stop offset="100%" stopColor="#1d4ed8" stopOpacity="0.4" />
          </linearGradient>
          <linearGradient id="barGradPeak" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#ef4444" stopOpacity="1" />
            <stop offset="100%" stopColor="#991b1b" stopOpacity="0.6" />
          </linearGradient>
          <filter id="barGlow" x="-20%" y="-20%" width="140%" height="140%">
            <feGaussianBlur stdDeviation="3" result="blur" />
            <feComposite in="SourceGraphic" in2="blur" operator="over" />
          </filter>
        </defs>

        {/* Grid lines */}
        {yTicks.map((tick, i) => {
          const norm = tick / maxCount;
          const y = paddingTop + chartHeight - norm * chartHeight;
          return (
            <g key={i}>
              <line
                x1={paddingLeft}
                y1={y}
                x2={viewBoxWidth - paddingRight}
                y2={y}
                stroke="#1e2a45"
                strokeDasharray={i === yTicks.length - 1 ? undefined : "3 3"}
                strokeWidth="1"
              />
              <text
                x={paddingLeft - 8}
                y={y + 4}
                fill="#64748b"
                fontSize="10"
                fontWeight="500"
                textAnchor="end"
              >
                {tick}
              </text>
            </g>
          );
        })}

        {/* 24 Bars */}
        {hourCounts.map((count, hour) => {
          const x = paddingLeft + hour * (barWidth + barGap);
          const barHeightNorm = count / maxCount;
          const bHeight = Math.max(count > 0 ? 4 : 0, barHeightNorm * chartHeight);
          const y = paddingTop + chartHeight - bHeight;

          const isPeak = (peakHour !== null && peakHour !== undefined && hour === peakHour) || (maxCount > 0 && count === maxCount && count > 0);
          const isHovered = hoveredHour === hour;

          return (
            <g
              key={hour}
              onMouseEnter={() => setHoveredHour(hour)}
              onMouseLeave={() => setHoveredHour(null)}
              style={{ cursor: "pointer" }}
            >
              {/* Subtle background bar slot */}
              <rect
                x={x}
                y={paddingTop}
                width={barWidth}
                height={chartHeight}
                fill="rgba(255, 255, 255, 0.02)"
                rx="3"
              />

              {/* Active Bar */}
              {count > 0 ? (
                <rect
                  x={x}
                  y={y}
                  width={barWidth}
                  height={bHeight}
                  fill={isPeak ? "url(#barGradPeak)" : "url(#barGradNormal)"}
                  rx="3"
                  stroke={isHovered ? "#ffffff" : isPeak ? "#f87171" : "transparent"}
                  strokeWidth={isHovered ? 1.5 : 1}
                  filter={isPeak || isHovered ? "url(#barGlow)" : undefined}
                  style={{ transition: "all 0.15s ease" }}
                />
              ) : (
                /* Empty bar indicator dot */
                <circle
                  cx={x + barWidth / 2}
                  cy={paddingTop + chartHeight - 2}
                  r="1.5"
                  fill="#1e2a45"
                />
              )}

              {/* Peak indicator dot on top of peak bar */}
              {isPeak && count > 0 && (
                <circle
                  cx={x + barWidth / 2}
                  cy={y - 6}
                  r="3.5"
                  fill="#ef4444"
                  stroke="#ffffff"
                  strokeWidth="1"
                />
              )}
            </g>
          );
        })}

        {/* X Axis Hour Labels (00:00, 04:00, 08:00, 12:00, 16:00, 20:00, 23:00) */}
        {[0, 4, 8, 12, 16, 20, 23].map((h) => {
          const x = paddingLeft + h * (barWidth + barGap) + barWidth / 2;
          const label = `${h.toString().padStart(2, "0")}:00`;
          return (
            <text
              key={h}
              x={x}
              y={viewBoxHeight - 10}
              fill={h === peakHour ? "#f87171" : "#64748b"}
              fontSize="10"
              fontWeight={h === peakHour ? "700" : "500"}
              textAnchor="middle"
            >
              {label}
            </text>
          );
        })}
      </svg>

      {/* Tooltip */}
      {hoveredHour !== null && (
        <div
          style={{
            position: "absolute",
            top: "10px",
            right: "15px",
            backgroundColor: "#1e2a45",
            border: "1px solid #3b82f6",
            borderRadius: "8px",
            padding: "8px 12px",
            fontSize: "12px",
            color: "#ffffff",
            boxShadow: "0 4px 12px rgba(0,0,0,0.5)",
            pointerEvents: "none",
            zIndex: 10,
          }}
        >
          <div style={{ fontWeight: 700, color: hoveredHour === peakHour ? "#ef4444" : "#60a5fa" }}>
            Hour {hoveredHour.toString().padStart(2, "0")}:00 - {((hoveredHour + 1) % 24).toString().padStart(2, "0")}:00
          </div>
          <div style={{ color: "#94a3b8", fontSize: "11px", marginTop: "2px" }}>
            Breaches recorded: <strong style={{ color: "#ffffff" }}>{hourCounts[hoveredHour]}</strong>
          </div>
        </div>
      )}

      {totalBreachesInChart === 0 && (
        <div
          style={{
            position: "absolute",
            inset: 0,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            backgroundColor: "rgba(20, 27, 45, 0.6)",
            backdropFilter: "blur(2px)",
            borderRadius: "12px",
            color: "#64748b",
            fontSize: "13px",
          }}
        >
          No breach events recorded in this time period
        </div>
      )}
    </div>
  );
}
