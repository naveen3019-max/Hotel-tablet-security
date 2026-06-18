from fastapi import WebSocket
from typing import List, Optional
import json
import logging

logger = logging.getLogger(__name__)

class ConnectionManager:
    """Manages all active WebSocket connections from dashboard clients."""

    def __init__(self):
        # Store connections as dict: websocket -> hotel_id
        self.active_connections: dict[WebSocket, str] = {}

    async def connect(self, websocket: WebSocket, hotel_id: str):
        """Accept a new WebSocket connection and register it."""
        await websocket.accept()
        self.active_connections[websocket] = hotel_id
        logger.info(
            f"📡 WS client connected (hotel: {hotel_id}). Total connections: {len(self.active_connections)}"
        )

    def disconnect(self, websocket: WebSocket):
        """Remove a WebSocket from the active list when it closes."""
        if websocket in self.active_connections:
            del self.active_connections[websocket]
        logger.info(
            f"📡 WS client disconnected. Total connections: {len(self.active_connections)}"
        )

    async def broadcast(self, message: dict, hotel_id: Optional[str] = None):
        """
        Send a JSON message to connected dashboard clients.
        If hotel_id is provided, only send to that hotel and super_admins (ALL).
        """
        if not self.active_connections:
            return

        payload = json.dumps(message)
        dead_connections: List[WebSocket] = []
        
        # ← FIXED: Track successful broadcasts and handle dead connections without crashing
        success_count = 0
        for connection, conn_hotel_id in list(self.active_connections.items()):
            # Filter by hotel_id
            if hotel_id and conn_hotel_id != "ALL" and conn_hotel_id != hotel_id:
                continue

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
        print(f"📡 Broadcast to {success_count} WebSocket clients (target: {hotel_id})", flush=True)

manager = ConnectionManager()
