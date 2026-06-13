import { useState, useEffect, useRef, useCallback } from 'react';

type ConnectionStatus = 'connected' | 'disconnected' | 'connecting';

export function useWebSocket(url: string) {
  const [status, setStatus] = useState<ConnectionStatus>('disconnected');
  const [lastMessage, setLastMessage] = useState<any>(null);
  const [isLikelySilentDisconnect, setIsLikelySilentDisconnect] = useState<boolean>(false);
  
  const wsRef = useRef<WebSocket | null>(null);
  const reconnectTimerRef = useRef<NodeJS.Timeout>();
  const lastMessageTime = useRef<number>(Date.now());
  const healthCheckIntervalRef = useRef<NodeJS.Timeout>();
  const reconnectAttempts = useRef<number>(0);

  const connect = useCallback(() => {
    if (wsRef.current?.readyState === WebSocket.OPEN) return;

    setStatus('connecting');
    const ws = new WebSocket(url);

    ws.onopen = () => {
      setStatus('connected');
      setIsLikelySilentDisconnect(false);
      reconnectAttempts.current = 0;
      lastMessageTime.current = Date.now();
      console.log('[WS] Connected to dashboard stream');
      
      if (reconnectTimerRef.current) clearInterval(reconnectTimerRef.current);
      reconnectTimerRef.current = setInterval(() => {
        if (ws.readyState === WebSocket.OPEN) {
          ws.send(JSON.stringify({ type: 'ping' }));
        }
      }, 30000);
    };

    ws.onmessage = (event) => {
      lastMessageTime.current = Date.now();
      setIsLikelySilentDisconnect(false);
      try {
        const data = JSON.parse(event.data);
        if (data.type === 'pong') return;
        setLastMessage(data);
      } catch (e) {
        console.error('WebSocket parse error:', e);
      }
    };

    ws.onclose = () => {
      setStatus('disconnected');
      if (reconnectTimerRef.current) clearInterval(reconnectTimerRef.current);
      
      const timeout = Math.min(1000 * Math.pow(2, reconnectAttempts.current), 30000);
      reconnectAttempts.current += 1;
      setTimeout(() => connect(), timeout);
    };

    ws.onerror = (error) => {
      console.error('WebSocket error:', error);
      ws.close();
    };

    wsRef.current = ws;
  }, [url]);

  useEffect(() => {
    connect();

    healthCheckIntervalRef.current = setInterval(() => {
      const silent = Date.now() - lastMessageTime.current;
      if (silent > 45000 && wsRef.current?.readyState === WebSocket.OPEN) {
        console.log("🔄 Silent disconnect! Force reconnecting...");
        setIsLikelySilentDisconnect(true);
        wsRef.current.close();
      }
    }, 5000);

    return () => {
      if (wsRef.current) wsRef.current.close();
      if (reconnectTimerRef.current) clearInterval(reconnectTimerRef.current);
      if (healthCheckIntervalRef.current) clearInterval(healthCheckIntervalRef.current);
    };
  }, [connect]);

  return { status, lastMessage, isLikelySilentDisconnect };
}
