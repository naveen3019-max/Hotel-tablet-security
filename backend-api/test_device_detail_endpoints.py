import asyncio
import json
from httpx import AsyncClient, ASGITransport
import sys
import os

# Ensure backend directory is in sys.path
sys.path.insert(0, os.path.dirname(__file__))

from main import app, devices_collection, alerts_collection

async def run_tests():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        # 1. Fetch any existing device or create a dummy device for testing
        devices = await devices_collection.find({}).to_list(10)
        if devices:
            test_device_id = str(devices[0]["_id"])
        else:
            test_device_id = "TAB-TEST1234"
            await devices_collection.insert_one({
                "_id": test_device_id,
                "room_id": "101",
                "status": "ok",
                "battery": 85,
                "rssi": -65,
                "staff_name": "Test Staff"
            })

        print(f"Testing with device ID: {test_device_id}")

        # Insert dummy breach and battery alert if needed
        await alerts_collection.insert_one({
            "deviceId": test_device_id,
            "roomId": "101",
            "type": "breach",
            "severity": "high",
            "message": "Security boundary breach detected (RSSI: -88 dBm)",
            "rssi": -88,
            "ts": "2026-09-10T10:00:00Z",
            "source": "rssi_threshold"
        })
        await alerts_collection.insert_one({
            "deviceId": test_device_id,
            "roomId": "101",
            "type": "battery_low",
            "severity": "medium",
            "message": "Battery low: 15%",
            "battery": 15,
            "ts": "2026-09-11T14:30:00Z"
        })

        # Test GET /api/devices/{device_id}/history
        res_history = await client.get(f"/api/devices/{test_device_id}/history")
        print("GET History Status:", res_history.status_code)
        assert res_history.status_code == 200, f"History failed: {res_history.text}"
        history_json = res_history.json()
        print("History Output Keys:", list(history_json.keys()))
        print("Breach Events Count:", len(history_json.get("breach_events", [])))
        print("Low Battery Events Count:", len(history_json.get("low_battery_events", [])))

        # Test GET /api/devices/{device_id}/stats
        res_stats = await client.get(f"/api/devices/{test_device_id}/stats")
        print("GET Stats Status:", res_stats.status_code)
        assert res_stats.status_code == 200, f"Stats failed: {res_stats.text}"
        stats_json = res_stats.json()
        print("Stats Output:", json.dumps(stats_json, indent=2))

        # Test GET /api/devices/{device_id}/report.pdf
        res_pdf = await client.get(f"/api/devices/{test_device_id}/report.pdf")
        print("GET Report PDF Status:", res_pdf.status_code)
        assert res_pdf.status_code == 200, f"PDF Report failed: {res_pdf.text}"
        print("Content-Type:", res_pdf.headers.get("content-type"))
        print("Content-Disposition:", res_pdf.headers.get("content-disposition"))
        pdf_bytes = res_pdf.content
        print("PDF Bytes Length:", len(pdf_bytes))
        assert pdf_bytes.startswith(b"%PDF"), "PDF binary does not start with %PDF header"
        print("✅ ALL BACKEND ENDPOINTS TESTED AND WORKING PERFECTLY!")

if __name__ == "__main__":
    asyncio.run(run_tests())
