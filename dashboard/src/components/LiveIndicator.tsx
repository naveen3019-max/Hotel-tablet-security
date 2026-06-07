// NEW FILE: dashboard/src/components/LiveIndicator.tsx
// Visual badge showing the live WebSocket connection status.
// Green pulsing dot = connected, yellow = reconnecting, red = disconnected.

import React from "react"; // ← NEW: React import for JSX
import type { ConnectionStatus } from "../hooks/useWebSocket"; // ← NEW: Shared type

interface LiveIndicatorProps {
  status: ConnectionStatus; // ← NEW: Driven by useWebSocket hook
}

export default function LiveIndicator({ status }: LiveIndicatorProps) {
  // ← NEW: Map each status to its colour class and label text
  const config: Record<
    ConnectionStatus,
    { dot: string; label: string; text: string }
  > = {
    connected: {
      dot: "bg-green-500 animate-pulse",     // ← NEW: Pulsing green = fully live
      label: "text-green-700",
      text: "LIVE",
    },
    connecting: {
      dot: "bg-yellow-400 animate-pulse",    // ← NEW: Pulsing yellow = trying to reconnect
      label: "text-yellow-700",
      text: "Reconnecting...",
    },
    disconnected: {
      dot: "bg-red-500",                     // ← NEW: Solid red = no connection
      label: "text-red-700",
      text: "Disconnected",
    },
  };

  const { dot, label, text } = config[status];

  return (
    // ← NEW: Positioned top-right via parent flex layout in enhanced-page.tsx
    <div className="flex items-center gap-2 px-3 py-1 rounded-full bg-white shadow border border-gray-200">
      {/* ← NEW: The coloured dot */}
      <span className={`w-2.5 h-2.5 rounded-full ${dot}`} />
      {/* ← NEW: Status label */}
      <span className={`text-xs font-semibold ${label}`}>{text}</span>
    </div>
  );
}
