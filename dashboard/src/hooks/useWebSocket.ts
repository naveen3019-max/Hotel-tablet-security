import { useState, useEffect, useRef, useCallback } from 'react';

type ConnectionStatus = 'connected' | 'disconnected' | 'connecting';

export function useWebSocket(url: string) {
  const [status, setStatus] = useState<ConnectionStatus>('disconnected');
  const [lastMessage, setLastMessage] = useState<any>(null);
  const wsRef = useRef<WebSocket | null>(null);
  const reconnectTimerRef = useRef<NodeJS.Timeout>();
  const lastMessageTime = useRef<number>(Date.now());
  const healthCheckIntervalRef = useRef<NodeJS.Timeout>();

  const connect = useCallback(() => {
    if (wsRef.current?.readyState === WebSocket.OPEN) return;

    setStatus('connecting');
    const ws = new WebSocket(url);

    ws.onopen = () => {
      setStatus('connected');
      lastMessageTime.current = Date.now();
      console.log('WebSocket connected');
      
      if (reconnectTimerRef.current) clearInterval(reconnectTimerRef.current);
      reconnectTimerRef.current = setInterval(() => {
        if (ws.readyState === WebSocket.OPEN) {
          ws.send(JSON.stringify({ type: 'ping' }));
        }
      }, 30000);
    };

    ws.onmessage = (event) => {
      lastMessageTime.current = Date.now();
      try {
        const data = JSON.parse(event.data);
        if (data.type === 'pong') {
          return;
        }
        setLastMessage(data);
      } catch (e) {
        console.error('WebSocket parse error:', e);
      }
    };

    ws.onclose = () => {
      setStatus('disconnected');
      if (reconnectTimerRef.current) clearInterval(reconnectTimerRef.current);
    };

    ws.onerror = (error) => {
      console.error('WebSocket error:', error);
      ws.close();
    };

    wsRef.current = ws;
  }, [url]);

  const forceReconnect = useCallback(() => {
    console.log('🔄 Force reconnecting...');
    setStatus('disconnected');
    if (wsRef.current) {
      wsRef.current.close();
      wsRef.current = null;
    }
    setTimeout(() => {
      connect();
    }, 1000);
  }, [connect]);

  useEffect(() => {
    connect();

    healthCheckIntervalRef.current = setInterval(() => {
      const timeSinceLastMessage = Date.now() - lastMessageTime.current;
      if (timeSinceLastMessage > 60000) {
        console.warn('Silent disconnect detected! No message in 60s.');
        forceReconnect();
      }
    }, 5000);

    return () => {
      if (wsRef.current) wsRef.current.close();
      if (reconnectTimerRef.current) clearInterval(reconnectTimerRef.current);
      if (healthCheckIntervalRef.current) clearInterval(healthCheckIntervalRef.current);
    };
  }, [connect, forceReconnect]);

  return { status, lastMessage };
}
