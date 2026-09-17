"use client";

import React, { useState } from "react";

export type RssiDataPoint = {
  timestamp: string; // ISO date string or formatted date
  rssi: number | null;
  label?: string;
};

interface RssiLineChartProps {
  data: RssiDataPoint[];
  height?: number;
}

export default function RssiLineChart({ data, height = 220 }: RssiLineChartProps) {
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null);

  // Filter out invalid points or sanitize
  const validData = data.filter((d) => d.rssi !== null && d.rssi !== undefined && d.rssi !== -127);

  if (validData.length === 0) {
    return (
      <div
        style={{
          height: `${height}px`,
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          justifyContent: "center",
          backgroundColor: "rgba(255, 255, 255, 0.01)",
          border: "1px dashed #1e2a45",
          borderRadius: "12px",
          color: "#64748b",
          fontSize: "13px",
        }}
      >
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" style={{ marginBottom: "8px", opacity: 0.5 }}>
          <path d="M5 12.55a11 11 0 0 1 14.08 0" />
          <path d="M1.42 9a16 16 0 0 1 21.16 0" />
          <path d="M8.53 16.11a6 6 0 0 1 6.95 0" />
          <line x1="12" y1="20" x2="12.01" y2="20" />
        </svg>
        No signal telemetry recorded in selected date range
      </div>
    );
  }

  // Dimensions
  const paddingLeft = 45;
  const paddingRight = 20;
  const paddingTop = 25;
  const paddingBottom = 35;
  const viewBoxWidth = 600;
  const viewBoxHeight = height;

  const chartWidth = viewBoxWidth - paddingLeft - paddingRight;
  const chartHeight = viewBoxHeight - paddingTop - paddingBottom;

  // Y Axis bounds (-100 dBm to -30 dBm typically)
  const rssiValues = validData.map((d) => d.rssi as number);
  const minRssi = Math.min(...rssiValues, -95);
  const maxRssi = Math.max(...rssiValues, -40);
  const rssiRange = maxRssi - minRssi || 1;

  // Helper mapping functions
  const getX = (index: number) => {
    if (validData.length <= 1) return paddingLeft + chartWidth / 2;
    return paddingLeft + (index / (validData.length - 1)) * chartWidth;
  };

  const getY = (val: number) => {
    const norm = (val - minRssi) / rssiRange;
    return paddingTop + chartHeight - norm * chartHeight;
  };

  // Build SVG path
  const points = validData.map((d, i) => `${getX(i)},${getY(d.rssi as number)}`);
  const pathD = `M ${points.join(" L ")}`;

  // Area path for gradient under line
  const areaD = `${pathD} L ${getX(validData.length - 1)},${paddingTop + chartHeight} L ${getX(0)},${paddingTop + chartHeight} Z`;

  // Threshold line at -80 dBm
  const thresholdY = getY(-80);
  const showThreshold = thresholdY >= paddingTop && thresholdY <= paddingTop + chartHeight;

  // Y-axis grid ticks
  const yTicks = [
    maxRssi,
    Math.round(maxRssi - rssiRange * 0.33),
    Math.round(maxRssi - rssiRange * 0.66),
    minRssi,
  ];

  return (
    <div style={{ position: "relative", width: "100%" }}>
      <svg
        viewBox={`0 0 ${viewBoxWidth} ${viewBoxHeight}`}
        style={{ width: "100%", height: "auto", overflow: "visible" }}
      >
        <defs>
          <linearGradient id="rssiGradient" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#3b82f6" stopOpacity="0.35" />
            <stop offset="100%" stopColor="#3b82f6" stopOpacity="0.0" />
          </linearGradient>
          <filter id="lineGlow" x="-10%" y="-10%" width="120%" height="120%">
            <feGaussianBlur stdDeviation="2" result="blur" />
            <feComposite in="SourceGraphic" in2="blur" operator="over" />
          </filter>
        </defs>

        {/* Grid lines */}
        {yTicks.map((tick, i) => {
          const y = getY(tick);
          return (
            <g key={i}>
              <line
                x1={paddingLeft}
                y1={y}
                x2={viewBoxWidth - paddingRight}
                y2={y}
                stroke="#1e2a45"
                strokeDasharray={i === 0 || i === yTicks.length - 1 ? undefined : "3 3"}
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
                {tick} dBm
              </text>
            </g>
          );
        })}

        {/* Weak Signal Threshold Line (-80 dBm) */}
        {showThreshold && (
          <g>
            <line
              x1={paddingLeft}
              y1={thresholdY}
              x2={viewBoxWidth - paddingRight}
              y2={thresholdY}
              stroke="#ef4444"
              strokeDasharray="4 4"
              strokeWidth="1"
              opacity="0.6"
            />
            <text
              x={viewBoxWidth - paddingRight}
              y={thresholdY - 4}
              fill="#ef4444"
              fontSize="9"
              fontWeight="600"
              textAnchor="end"
              opacity="0.8"
            >
              Weak Signal (-80 dBm)
            </text>
          </g>
        )}

        {/* Area fill */}
        <path d={areaD} fill="url(#rssiGradient)" />

        {/* Line */}
        <path
          d={pathD}
          fill="none"
          stroke="#3b82f6"
          strokeWidth="2.5"
          strokeLinecap="round"
          strokeLinejoin="round"
          filter="url(#lineGlow)"
        />

        {/* Data Points */}
        {validData.map((d, i) => {
          const cx = getX(i);
          const cy = getY(d.rssi as number);
          const isHovered = hoveredIndex === i;
          const isWeak = (d.rssi as number) <= -80;

          return (
            <g key={i} onMouseEnter={() => setHoveredIndex(i)} onMouseLeave={() => setHoveredIndex(null)}>
              {/* Touch area */}
              <circle cx={cx} cy={cy} r="12" fill="transparent" style={{ cursor: "pointer" }} />
              {/* Visible node */}
              <circle
                cx={cx}
                cy={cy}
                r={isHovered ? 6 : 3.5}
                fill={isWeak ? "#ef4444" : "#3b82f6"}
                stroke="#0f172a"
                strokeWidth="2"
                style={{ transition: "all 0.15s ease", cursor: "pointer" }}
              />
            </g>
          );
        })}

        {/* X Axis Labels */}
        {validData.length > 0 && (
          <>
            <text
              x={paddingLeft}
              y={viewBoxHeight - 10}
              fill="#64748b"
              fontSize="10"
              fontWeight="500"
              textAnchor="start"
            >
              {validData[0].label || new Date(validData[0].timestamp).toLocaleDateString(undefined, { month: "short", day: "numeric" })}
            </text>
            {validData.length > 2 && (
              <text
                x={paddingLeft + chartWidth / 2}
                y={viewBoxHeight - 10}
                fill="#64748b"
                fontSize="10"
                fontWeight="500"
                textAnchor="middle"
              >
                {validData[Math.floor(validData.length / 2)].label ||
                  new Date(validData[Math.floor(validData.length / 2)].timestamp).toLocaleDateString(undefined, { month: "short", day: "numeric" })}
              </text>
            )}
            {validData.length > 1 && (
              <text
                x={viewBoxWidth - paddingRight}
                y={viewBoxHeight - 10}
                fill="#64748b"
                fontSize="10"
                fontWeight="500"
                textAnchor="end"
              >
                {validData[validData.length - 1].label ||
                  new Date(validData[validData.length - 1].timestamp).toLocaleDateString(undefined, { month: "short", day: "numeric" })}
              </text>
            )}
          </>
        )}
      </svg>

      {/* Tooltip on hover */}
      {hoveredIndex !== null && validData[hoveredIndex] && (
        <div
          style={{
            position: "absolute",
            top: `${(getY(validData[hoveredIndex].rssi as number) / viewBoxHeight) * 100}%`,
            left: `${(getX(hoveredIndex) / viewBoxWidth) * 100}%`,
            transform: "translate(-50%, -125%)",
            backgroundColor: "#1e2a45",
            border: "1px solid #3b82f6",
            borderRadius: "6px",
            padding: "6px 10px",
            fontSize: "12px",
            color: "#ffffff",
            pointerEvents: "none",
            boxShadow: "0 4px 12px rgba(0,0,0,0.4)",
            whiteSpace: "nowrap",
            zIndex: 10,
          }}
        >
          <div style={{ fontWeight: 700, color: (validData[hoveredIndex].rssi as number) <= -80 ? "#ef4444" : "#60a5fa" }}>
            {validData[hoveredIndex].rssi} dBm
          </div>
          <div style={{ fontSize: "10px", color: "#94a3b8" }}>
            {new Date(validData[hoveredIndex].timestamp).toLocaleString()}
          </div>
        </div>
      )}
    </div>
  );
}
