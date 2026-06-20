import { useState, useEffect, useRef } from 'react';

type ConnectionStatus = 'connected' | 'disconnected' | 'connecting';

export interface WebSocketMessage {
  type?: string;
  data?: Record<string, unknown> | null | string;
  [key: string]: unknown;
}

const getWsUrl = (httpUrl: string, token: string = ''): string => {
    if (!httpUrl) {
        console.error('No API URL configured!')
        return ''
    }
    const wsUrl = httpUrl
        .replace(/^https:\/\//i, 'wss://')
        .replace(/^http:\/\//i, 'ws://')
    
    // ← ADD: Ensure correct path
    const finalUrl = wsUrl.endsWith('/ws/dashboard')
        ? wsUrl
        : `${wsUrl}/ws/dashboard`
    
    // ← FIXED: Pass JWT token as query param so backend knows which hotel this connection belongs to
    const urlWithToken = token ? `${finalUrl}?token=${encodeURIComponent(token)}` : finalUrl;
    console.log('[WS] Connecting to:', urlWithToken)
    return urlWithToken
}

export function useWebSocket(url: string, token: string = '') {
  const [status, setStatus] = useState<ConnectionStatus>('disconnected');
  const [lastMessage, setLastMessage] = useState<WebSocketMessage | null>(null);
  const [isPollingMode, setIsPollingMode] = useState<boolean>(true); 
  
  const wsRef = useRef<WebSocket | null>(null);
  const pingTimerRef = useRef<NodeJS.Timeout | null>(null);
  const healthCheckRef = useRef<NodeJS.Timeout | null>(null);
  const lastMessageTime = useRef<number>(0);
  const reconnectAttempts = useRef<number>(0);

  useEffect(() => {
    lastMessageTime.current = Date.now();
    
    let isMounted = true;

    function connect() {
      if (!isMounted) return;
      if (wsRef.current?.readyState === WebSocket.OPEN) return;

      setStatus('connecting');
      const wsUrl = getWsUrl(url, token);
      if (!wsUrl) return;
      const ws = new WebSocket(wsUrl);

      ws.onopen = () => {
        if (!isMounted) return;
        lastMessageTime.current = Date.now(); // ← FIXED: Reset on connect
        setStatus('connected');
        setIsPollingMode(false); 
        reconnectAttempts.current = 0;
        console.log("✅ [WS] Connected to hotel security"); // ← FIXED: Added connection logging
        
        if (pingTimerRef.current) clearInterval(pingTimerRef.current);
        pingTimerRef.current = setInterval(() => {
          if (ws.readyState === WebSocket.OPEN) {
            ws.send("ping"); // ← FIXED: Send plain string ping instead of JSON
          }
        }, 20000); // ← FIXED: Ping every 20 seconds
      };

      ws.onmessage = (event) => {
        if (!isMounted) return;
        try {
          const msg = JSON.parse(event.data) as WebSocketMessage;
          
          // ← FIXED: ignore pong — just update time
          if (msg.type === 'pong' || msg.type === 'connected') {
            lastMessageTime.current = Date.now();
            return; // Don't pass to UI
          }
          
          // Real message → update UI
          lastMessageTime.current = Date.now();
          setIsPollingMode(false);
          setLastMessage(msg);
          
        } catch (e) {
          // Handle plain text responses
          if (event.data === 'pong') {
              lastMessageTime.current = Date.now();
              return;
          }
          console.error('WS Parse Error:', e);
        }
      };

      ws.onclose = (event) => {
        if (!isMounted) return;
        console.log(`🔴 [WS] Closed. Code: ${event.code}`); // ← FIXED: Added close logging
        setStatus('disconnected');
        setIsPollingMode(true); 
        if (pingTimerRef.current) clearInterval(pingTimerRef.current);
        
        const timeout = Math.min(1000 * Math.pow(2, reconnectAttempts.current), 30000);
        reconnectAttempts.current += 1;
        setTimeout(() => {
            if (isMounted) connect();
        }, timeout);
      };

      ws.onerror = () => {
        console.log("❌ [WS] Error — closing"); // ← FIXED: Added error logging
        ws.close();
      };
      
      wsRef.current = ws;
    }

    connect();

    healthCheckRef.current = setInterval(() => {
      if (!isMounted) return;
      const silent = Date.now() - lastMessageTime.current;
      if (silent > 60000 && wsRef.current?.readyState === WebSocket.OPEN) { // ← FIXED: 60 seconds threshold
        console.log("💀 God Mode: Silent drop detected. Nuking connection...");
        setIsPollingMode(true);
        wsRef.current.close();
      }
    }, 5000);

    return () => {
      isMounted = false;
      if (wsRef.current) wsRef.current.close();
      if (pingTimerRef.current) clearInterval(pingTimerRef.current);
      if (healthCheckRef.current) clearInterval(healthCheckRef.current);
    };
  }, [url, token]);

  return { status, lastMessage, isPollingMode };
}
