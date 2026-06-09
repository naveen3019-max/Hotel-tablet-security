import { useEffect, useRef, useState, useCallback } from "react";

export type ConnectionStatus = "connecting" | "connected" | "disconnected";

export interface WebSocketMessage {
  type: string;
  data?: Record<string, unknown>;
  timestamp?: string;
  message?: string;
}

interface UseWebSocketReturn {
  lastMessage: WebSocketMessage | null;
  connectionStatus: ConnectionStatus;
}

const BACKOFF_DELAYS = [1000, 2000, 4000, 8000, 16000, 30000];

export function useWebSocket(url: string): UseWebSocketReturn {
  const socketRef = useRef<WebSocket | null>(null);
  const retryCountRef = useRef<number>(0);
  const retryTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const pingIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  // ← FIXED: Clean isMounted ref to prevent setState on unmounted component
  const isMounted = useRef(true);

  const [lastMessage, setLastMessage] = useState<WebSocketMessage | null>(null);
  const [connectionStatus, setConnectionStatus] = useState<ConnectionStatus>("connecting");

  const getWsUrl = useCallback((httpUrl: string): string => {
    // ← FIXED: URL conversion with explicit regex for http and https
    return httpUrl.replace(/^https:\/\//i, "wss://").replace(/^http:\/\//i, "ws://") + "/ws/dashboard";
  }, []);

  const connect = useCallback(() => {
    if (!isMounted.current) return;
    
    if (
      socketRef.current &&
      (socketRef.current.readyState === WebSocket.OPEN ||
        socketRef.current.readyState === WebSocket.CONNECTING)
    ) {
      return;
    }

    const wsUrl = getWsUrl(url);
    if (isMounted.current) setConnectionStatus("connecting");
    console.log(`[WS] Connecting to ${wsUrl}...`);

    const ws = new WebSocket(wsUrl);
    socketRef.current = ws;

    ws.onopen = () => {
      console.log("[WS] Connected.");
      if (!isMounted.current) {
        ws.close();
        return;
      }
      
      // ← FIXED: Reset delay to 1s on successful connection
      retryCountRef.current = 0;
      setConnectionStatus("connected");

      // ← FIXED: Ping/Pong keepalive - send ping every 30 seconds
      if (pingIntervalRef.current) clearInterval(pingIntervalRef.current);
      pingIntervalRef.current = setInterval(() => {
        if (ws.readyState === WebSocket.OPEN) {
          ws.send("ping");
          console.log("[WS] Sent ping");
        }
      }, 30000);
    };

    ws.onmessage = (event: MessageEvent) => {
      if (!isMounted.current) return;
      
      try {
        const parsed: WebSocketMessage = JSON.parse(event.data as string);
        
        // ← FIXED: Ignore incoming "pong" messages silently
        if (parsed.type === "pong") {
          console.log("[WS] Received pong");
          return;
        }
        
        // ← FIXED: Log every message to console for debugging
        console.log("[WS] Received message:", parsed);
        setLastMessage(parsed);
      } catch {
        console.warn("[WS] Received non-JSON message:", event.data);
      }
    };

    ws.onerror = (err) => {
      console.error("[WS] Error:", err);
    };

    ws.onclose = () => {
      // ← FIXED: Stop ping interval when connection closes
      if (pingIntervalRef.current) {
        clearInterval(pingIntervalRef.current);
        pingIntervalRef.current = null;
      }

      if (!isMounted.current) return;
      
      console.warn("[WS] Disconnected.");
      setConnectionStatus("disconnected");

      // ← FIXED: Proper exponential backoff reconnect
      const delay = BACKOFF_DELAYS[Math.min(retryCountRef.current, BACKOFF_DELAYS.length - 1)];
      retryCountRef.current += 1;

      console.log(`[WS] Retrying in ${delay / 1000}s (attempt ${retryCountRef.current})...`);
      setConnectionStatus("connecting");

      if (retryTimerRef.current) clearTimeout(retryTimerRef.current);
      // ← FIXED: Never stop retrying while component is mounted
      retryTimerRef.current = setTimeout(() => {
        connect();
      }, delay);
    };
  }, [url, getWsUrl]);

  useEffect(() => {
    isMounted.current = true;
    connect();

    return () => {
      // ← FIXED: Clean isMounted ref set to false on unmount
      isMounted.current = false;
      if (pingIntervalRef.current) clearInterval(pingIntervalRef.current);
      if (retryTimerRef.current) clearTimeout(retryTimerRef.current);
      
      if (socketRef.current) {
        socketRef.current.onclose = null;
        socketRef.current.close();
      }
    };
  }, [connect]);

  return { lastMessage, connectionStatus };
}
