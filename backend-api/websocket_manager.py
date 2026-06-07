# NEW FILE: backend-api/websocket_manager.py
# Manages all active WebSocket dashboard connections and broadcasts events to all of them

from fastapi import WebSocket
from typing import List
import json
import logging

logger = logging.getLogger(__name__)


class ConnectionManager:
    """Manages all active WebSocket connections from dashboard clients."""

    def __init__(self):
        # ← NEW: List holding every currently-connected dashboard WebSocket
        self.active_connections: List[WebSocket] = []

    async def connect(self, websocket: WebSocket):
        """Accept a new WebSocket connection and register it."""
        await websocket.accept()  # ← NEW: Performs the WebSocket handshake
        self.active_connections.append(websocket)
        logger.info(
            f"📡 WS client connected. Total connections: {len(self.active_connections)}"
        )

    def disconnect(self, websocket: WebSocket):
        """Remove a WebSocket from the active list when it closes."""
        if websocket in self.active_connections:  # ← NEW: Guard against double-removal
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
            return  # ← NEW: Fast-path exit when nobody is listening

        # ← NEW: Encode once, send to everyone — avoids redundant serialisation
        payload = json.dumps(message)

        # ← NEW: Iterate over a copy so we can safely modify the list mid-loop
        dead_connections: List[WebSocket] = []
        for connection in list(self.active_connections):
            try:
                await connection.send_text(payload)
            except Exception as e:
                # ← NEW: Connection is dead (client closed tab, network drop, etc.)
                logger.warning(f"WS send failed, removing dead connection: {e}")
                dead_connections.append(connection)

        # ← NEW: Clean up all dead connections discovered during broadcast
        for dead in dead_connections:
            self.disconnect(dead)


# ← NEW: Single shared instance — imported by main.py so every endpoint shares state
manager = ConnectionManager()
