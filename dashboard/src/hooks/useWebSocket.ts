import { useState, useEffect, useRef, useCallback } from 'react';

type ConnectionStatus = 'connected' | 'disconnected' | 'connecting';

export function useWebSocket(url: string) {
  const [status, setStatus] = useState<ConnectionStatus>('disconnected');
  const [lastMessage, setLastMessage] = useState<any>(null);
  const [isPollingMode, setIsPollingMode] = useState<boolean>(true); 
  
  const wsRef = useRef<WebSocket | null>(null);
  const pingTimerRef = useRef<NodeJS.Timeout>();
  const healthCheckRef = useRef<NodeJS.Timeout>();
  const lastMessageTime = useRef<number>(Date.now());
  const reconnectAttempts = useRef<number>(0);
  const lastSuccessfulConnection = useRef<number>(0); 

  const connect = useCallback(() => {
    if (wsRef.current?.readyState === WebSocket.OPEN) return;

    setStatus('connecting');
    const ws = new WebSocket(url);

    ws.onopen = () => {
      setStatus('connected');
      setIsPollingMode(false); 
      reconnectAttempts.current = 0;
      lastMessageTime.current = Date.now();
      lastSuccessfulConnection.current = Date.now();
      console.log('⚡ God Mode: WebSocket Linked');
      
      if (pingTimerRef.current) clearInterval(pingTimerRef.current);
      pingTimerRef.current = setInterval(() => {
        if (ws.readyState === WebSocket.OPEN) {
          ws.send(JSON.stringify({ type: 'ping' })); 
        }
      }, 15000); 
    };

    ws.onmessage = (event) => {
      lastMessageTime.current = Date.now();
      setIsPollingMode(false);
      try {
        const data = JSON.parse(event.data);
        if (data.type === 'pong') return;
        setLastMessage(data); 
      } catch (e) {
        console.error('WS Parse Error:', e);
      }
    };

    ws.onclose = () => {
      setStatus('disconnected');
      setIsPollingMode(true); 
      if (pingTimerRef.current) clearInterval(pingTimerRef.current);
      
      const timeout = Math.min(1000 * Math.pow(2, reconnectAttempts.current), 30000);
      reconnectAttempts.current += 1;
      setTimeout(() => connect(), timeout);
    };

    ws.onerror = () => ws.close();
    wsRef.current = ws;
  }, [url]);

  useEffect(() => {
    connect();

    healthCheckRef.current = setInterval(() => {
      const silent = Date.now() - lastMessageTime.current;
      if (silent > 45000 && wsRef.current?.readyState === WebSocket.OPEN) {
        console.log("💀 God Mode: Silent drop detected. Nuking connection...");
        setIsPollingMode(true);
        wsRef.current.close();
      }
    }, 5000);

    return () => {
      if (wsRef.current) wsRef.current.close();
      if (pingTimerRef.current) clearInterval(pingTimerRef.current);
      if (healthCheckRef.current) clearInterval(healthCheckRef.current);
    };
  }, [connect]);

  return { status, lastMessage, isPollingMode, lastSuccessfulConnection: lastSuccessfulConnection.current };
}
