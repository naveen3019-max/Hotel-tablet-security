import { useState, useEffect, useRef } from 'react';

type ConnectionStatus = 'connected' | 'disconnected' | 'connecting';

export interface WebSocketMessage {
  type?: string;
  data?: Record<string, unknown> | null | string;
  [key: string]: unknown;
}

export function useWebSocket(url: string) {
  const [status, setStatus] = useState<ConnectionStatus>('disconnected');
  const [lastMessage, setLastMessage] = useState<WebSocketMessage | null>(null);
  const [isPollingMode, setIsPollingMode] = useState<boolean>(true); 
  const [lastSuccessfulConnection, setLastSuccessfulConnection] = useState<number>(0);
  
  const wsRef = useRef<WebSocket | null>(null);
  const pingTimerRef = useRef<NodeJS.Timeout | null>(null);
  const healthCheckRef = useRef<NodeJS.Timeout | null>(null);
  const lastMessageTime = useRef<number>(0);
  const reconnectAttempts = useRef<number>(0);

  useEffect(() => {
    lastMessageTime.current = Date.now();
    setLastSuccessfulConnection(Date.now());
    
    let isMounted = true;

    function connect() {
      if (!isMounted) return;
      if (wsRef.current?.readyState === WebSocket.OPEN) return;

      setStatus('connecting');
      const ws = new WebSocket(url);

      ws.onopen = () => {
        if (!isMounted) return;
        setStatus('connected');
        setIsPollingMode(false); 
        reconnectAttempts.current = 0;
        lastMessageTime.current = Date.now();
        setLastSuccessfulConnection(Date.now());
        console.log('⚡ God Mode: WebSocket Linked');
        
        if (pingTimerRef.current) clearInterval(pingTimerRef.current);
        pingTimerRef.current = setInterval(() => {
          if (ws.readyState === WebSocket.OPEN) {
            ws.send(JSON.stringify({ type: 'ping' })); 
          }
        }, 15000); 
      };

      ws.onmessage = (event) => {
        if (!isMounted) return;
        lastMessageTime.current = Date.now();
        setIsPollingMode(false);
        try {
          const data = JSON.parse(event.data) as WebSocketMessage;
          if (data.type === 'pong') return;
          setLastMessage(data); 
        } catch (e) {
          console.error('WS Parse Error:', e);
        }
      };

      ws.onclose = () => {
        if (!isMounted) return;
        setStatus('disconnected');
        setIsPollingMode(true); 
        if (pingTimerRef.current) clearInterval(pingTimerRef.current);
        
        const timeout = Math.min(1000 * Math.pow(2, reconnectAttempts.current), 30000);
        reconnectAttempts.current += 1;
        setTimeout(() => {
            if (isMounted) connect();
        }, timeout);
      };

      ws.onerror = () => ws.close();
      wsRef.current = ws;
    }

    connect();

    healthCheckRef.current = setInterval(() => {
      if (!isMounted) return;
      const silent = Date.now() - lastMessageTime.current;
      if (silent > 45000 && wsRef.current?.readyState === WebSocket.OPEN) {
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
  }, [url]);

  return { status, lastMessage, isPollingMode, lastSuccessfulConnection };
}
