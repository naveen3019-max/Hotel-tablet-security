// NEW FILE: dashboard/src/hooks/useWebSocket.ts
// Custom React hook that maintains a persistent WebSocket connection to the backend.
// Handles auto-reconnect with exponential backoff so the dashboard is always live.

import { useEffect, useRef, useState, useCallback } from "react"; // ← NEW: Core React hooks

// ← NEW: Possible states the connection can be in, shown in LiveIndicator
export type ConnectionStatus = "connecting" | "connected" | "disconnected";

// ← NEW: Shape of every message the backend sends over WebSocket
export interface WebSocketMessage {
  type: string;          // "breach" | "heartbeat" | "device_update" | "alert" | "device_offline" | "connected"
  data?: Record<string, unknown>;
  timestamp?: string;
  message?: string;
}

interface UseWebSocketReturn {
  isConnected: boolean;           // ← NEW: True when socket is OPEN and ready
  lastMessage: WebSocketMessage | null; // ← NEW: Most recent parsed message from server
  connectionStatus: ConnectionStatus;   // ← NEW: Granular status for LiveIndicator
}

// ← NEW: Exponential backoff delays in milliseconds: 1s, 2s, 4s, 8s, 16s, 30s (capped)
const BACKOFF_DELAYS = [1000, 2000, 4000, 8000, 16000, 30000];

export function useWebSocket(url: string): UseWebSocketReturn {
  const socketRef = useRef<WebSocket | null>(null);       // ← NEW: Ref so closures always see latest socket
  const retryCountRef = useRef<number>(0);                // ← NEW: Tracks how many retries have happened
  const retryTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null); // ← NEW: Current retry timer

  const [isConnected, setIsConnected] = useState(false);
  const [lastMessage, setLastMessage] = useState<WebSocketMessage | null>(null);
  const [connectionStatus, setConnectionStatus] = useState<ConnectionStatus>("connecting");

  // ← NEW: Derived ws:// or wss:// URL from the http/https backend URL
  const getWsUrl = useCallback((httpUrl: string): string => {
    // ← FIXED: Root Cause 5: Explicitly replace http:// with ws:// and https:// with wss:// for production
    return httpUrl.replace(/^http:\/\//i, "ws://").replace(/^https:\/\//i, "wss://") + "/ws/dashboard";
  }, []);

  const connect = useCallback(() => {
    // ← NEW: Don't open a second socket if one is already open or connecting
    if (
      socketRef.current &&
      (socketRef.current.readyState === WebSocket.OPEN ||
        socketRef.current.readyState === WebSocket.CONNECTING)
    ) {
      return;
    }

    const wsUrl = getWsUrl(url);
    setConnectionStatus("connecting");
    console.log(`[WS] Connecting to ${wsUrl}...`);

    const ws = new WebSocket(wsUrl); // ← NEW: Native browser WebSocket — no library needed
    socketRef.current = ws;

    ws.onopen = () => {
      // ← NEW: Connection established — reset retry counter, update status
      console.log("[WS] Connected.");
      retryCountRef.current = 0;
      setIsConnected(true);
      setConnectionStatus("connected");
    };

    ws.onmessage = (event: MessageEvent) => {
      try {
        // ← NEW: Parse JSON and expose to the component via state
        const parsed: WebSocketMessage = JSON.parse(event.data as string);
        setLastMessage(parsed);
      } catch {
        console.warn("[WS] Received non-JSON message:", event.data);
      }
    };

    ws.onerror = (err) => {
      // ← NEW: Log but don't crash — onclose will fire next and trigger retry
      console.error("[WS] Error:", err);
    };

    ws.onclose = () => {
      // ← NEW: Connection dropped — schedule reconnect with exponential backoff
      console.warn("[WS] Disconnected.");
      setIsConnected(false);
      setConnectionStatus("disconnected");

      // ← NEW: Pick delay from backoff table, capped at last entry
      const delay =
        BACKOFF_DELAYS[Math.min(retryCountRef.current, BACKOFF_DELAYS.length - 1)];
      retryCountRef.current += 1;

      console.log(
        `[WS] Retrying in ${delay / 1000}s (attempt ${retryCountRef.current})...`
      );

      // ← NEW: Show "connecting" while waiting so LiveIndicator turns yellow
      setConnectionStatus("connecting");

      retryTimerRef.current = setTimeout(() => {
        connect(); // ← NEW: Recursive retry
      }, delay);
    };
  }, [url, getWsUrl]);

  useEffect(() => {
    connect(); // ← NEW: Kick off first connection attempt on mount

    // ← NEW: Also send a periodic ping every 20s to keep proxy connections alive
    const pingInterval = setInterval(() => {
      if (socketRef.current?.readyState === WebSocket.OPEN) {
        socketRef.current.send("ping");
      }
    }, 20000);

    // ← NEW: Full cleanup when the component unmounts (dashboard tab closes)
    return () => {
      clearInterval(pingInterval);
      if (retryTimerRef.current) clearTimeout(retryTimerRef.current);
      if (socketRef.current) {
        // ← NEW: Prevent onclose from triggering a reconnect during unmount
        socketRef.current.onclose = null;
        socketRef.current.close();
      }
    };
  }, [connect]);

  return { isConnected, lastMessage, connectionStatus };
}
