from fastapi import WebSocket
from typing import List
import json
import logging

logger = logging.getLogger(__name__)

class ConnectionManager:
    """Manages all active WebSocket connections from dashboard clients."""

    def __init__(self):
        self.active_connections: List[WebSocket] = []

    async def connect(self, websocket: WebSocket):
        """Accept a new WebSocket connection and register it."""
        await websocket.accept()
        self.active_connections.append(websocket)
        logger.info(
            f"📡 WS client connected. Total connections: {len(self.active_connections)}"
        )

    def disconnect(self, websocket: WebSocket):
        """Remove a WebSocket from the active list when it closes."""
        if websocket in self.active_connections:
            self.active_connections.remove(websocket)
        logger.info(
            f"📡 WS client disconnected. Total connections: {len(self.active_connections)}"
        )

    async def broadcast(self, message: dict):
        """
        Send a JSON message to ALL currently connected dashboard clients.
        Handles dead connections gracefully — removes them without crashing.
        """
        if not self.active_connections:
            return

        payload = json.dumps(message)
        dead_connections: List[WebSocket] = []
        
        # ← FIXED: Track successful broadcasts and handle dead connections without crashing
        success_count = 0
        for connection in list(self.active_connections):
            try:
                # ← FIXED: Try/except around EACH websocket.send_text()
                await connection.send_text(payload)
                success_count += 1
            except Exception as e:
                logger.warning(f"WS send failed, removing dead connection: {e}")
                dead_connections.append(connection)

        # ← FIXED: Remove dead connections from active_connections
        for dead in dead_connections:
            self.disconnect(dead)
            
        # ← FIXED: Log how many clients received the broadcast
        print(f"📡 Broadcast to {success_count} WebSocket clients", flush=True)

manager = ConnectionManager()
