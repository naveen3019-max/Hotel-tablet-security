from fastapi import WebSocket
from typing import List, Dict, Optional
import json
import logging

logger = logging.getLogger(__name__)

class ConnectionManager:
    """Manages all active WebSocket connections from dashboard clients."""

    def __init__(self):
        # ← FIXED: Track hotel_id per connection
        # Instead of flat list, use dict mapping connection -> hotel_id
        self.active_connections: Dict[WebSocket, str] = {}

    async def connect(self, websocket: WebSocket, hotel_id: str = "unknown"):
        """Accept connection and tag with hotel_id"""
        await websocket.accept()
        # ← FIXED: Store hotel_id with connection
        self.active_connections[websocket] = hotel_id
        logger.info(
            f"📡 WS client connected for hotel={hotel_id}. Total: {len(self.active_connections)}"
        )

    def disconnect(self, websocket: WebSocket):
        """Remove connection"""
        if websocket in self.active_connections:
            del self.active_connections[websocket]
        logger.info(
            f"📡 WS client disconnected. Total: {len(self.active_connections)}"
        )

    async def broadcast(self, message: dict, target_hotel_id: Optional[str] = None):
        """
        ← FIXED: Broadcast ONLY to clients belonging to target_hotel_id.
        If target_hotel_id is None, broadcasts to super_admin connections only (hotel_id="ALL")
        plus the specific hotel if provided.
        """
        if not self.active_connections:
            return

        payload = json.dumps(message)
        dead_connections: List[WebSocket] = []
        
        # ← FIXED: Track successful broadcasts and handle dead connections without crashing
        success_count = 0
        
        for connection, conn_hotel_id in list(self.active_connections.items()):
            # ← FIXED: Only send if:
            # 1. Connection is super_admin (hotel_id="ALL")
            # 2. OR connection's hotel matches target
            should_send = (
                conn_hotel_id == "ALL" or
                conn_hotel_id == target_hotel_id
            )
            
            if not should_send:
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
        print(f"📡 Broadcast to {success_count} clients for hotel={target_hotel_id}", flush=True)

manager = ConnectionManager()
