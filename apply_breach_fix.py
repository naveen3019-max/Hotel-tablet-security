import os
import re

main_path = r"c:\Users\navee\Downloads\Hotel-tablet-security-master\Hotel-tablet-security-master\WEDDING-CARD-cc895524abaddd4e0e79cc06099f9f102c0f16c7\backend-api\main.py"

with open(main_path, "r", encoding="utf-8") as f:
    content = f.read()

# The original function text from @app.post("/api/alert/breach") to return {"ok": True}
old_func = r'''@app\.post\("/api/alert/breach"\)
async def alert_breach\(b: Breach, device=Depends\(get_current_device\)\):
    """Record breach alert \(JWT protected\)"""
    # ← Validate and correct RSSI
    rssi = b\.rssi
    if rssi > -10:
        rssi = -127
        logger\.warning\(f"Invalid RSSI corrected to -127 for \{b\.deviceId\}"\)
    
    # ← Use provided breach timestamp if valid
    # This preserves WHEN breach happened even if POST arrives later
    if b\.breachTimestamp:
        breach_age_ms = int\(time\.time\(\) \* 1000\) - b\.breachTimestamp
        breach_age_s = breach_age_ms / 1000
        
        if breach_age_s < 600:
            # Less than 10 minutes old — use it
            breach_time = datetime\.fromtimestamp\(b\.breachTimestamp / 1000, tz=pytz\.utc\)\.replace\(tzinfo=None\)
            logger\.info\(f"Using device breach timestamp: \{breach_age_s:\.0f\}s ago"\)
        else:
            # Too old — use current time
            breach_time = get_utc_naive\(\)
    else:
        breach_time = get_utc_naive\(\)
    
    # ← Deduplication check \(30 seconds\)
    recent_cutoff = datetime\.now\(pytz\.utc\)\.replace\(tzinfo=None\) - timedelta\(seconds=30\)
    
    existing = await alerts_collection\.find_one\(\{
        "deviceId": b\.deviceId,
        "type": "breach",
        "ts": \{"\$gte": recent_cutoff\}
    \}\)
    
    if existing:
        logger\.info\(f"Duplicate breach skipped: \{b\.deviceId\}"\)
        return \{"ok": True, "duplicate": True\}
    
    # ← Store breach with correct timestamp
    alert_doc = \{
        "deviceId": b\.deviceId,
        "roomId": b\.roomId,
        "type": "breach",
        "severity": "critical",
        "message": "WiFi disabled on device",
        "rssi": rssi,
        "ts": breach_time,
        "acknowledged": False,
        "hotel_id": device\.get\("hotel_id", "default"\)
    \}
    
    await alerts_collection\.insert_one\(alert_doc\)
    
    # ← Update device status
    await devices_collection\.update_one\(
        \{"_id": b\.deviceId\},
        \{"\$set": \{
            "status": StatusEnum\.breach,
            "rssi": rssi,
            "last_seen": get_utc_naive\(\)
        \}\}
    \)
    
    # ← Broadcast to hotel's WebSocket only
    device_hotel_id = device\.get\("hotel_id", "default"\)
    
    await broadcast_event\("alert", \{
        "type": "breach",
        "deviceId": b\.deviceId,
        "roomId": b\.roomId,
        "rssi": rssi,
        "message": "WiFi disabled on device",
        "timestamp": to_ist_isoformat\(breach_time\)
    \}, hotel_id=device_hotel_id\)
    
    return \{"ok": True\}'''

new_func = '''@app.post("/api/alert/breach")
async def alert_breach(
    b: Breach,
    device=Depends(get_current_device)
):
    """Record breach alert (JWT protected)"""
    try:
        # ← Validate RSSI
        rssi = b.rssi
        if rssi > -10:
            rssi = -127
            logger.warning(
                f"Invalid RSSI corrected: "
                f"{b.deviceId}")

        # ← Safe timestamp handling
        breach_time = get_utc_naive()
        try:
            if b.breachTimestamp and \
               b.breachTimestamp > 0:
                breach_age_s = (
                    int(time.time() * 1000) -
                    b.breachTimestamp
                ) / 1000
                if 0 < breach_age_s < 600:
                    breach_time = \
                        datetime.fromtimestamp(
                        b.breachTimestamp / 1000
                    ).replace(tzinfo=None)
        except Exception as ts_err:
            logger.warning(
                f"Timestamp parse failed: {ts_err}")
            breach_time = get_utc_naive()

        # ← Deduplication check
        try:
            recent_cutoff = datetime.now(
                pytz.utc
            ).replace(tzinfo=None) - timedelta(
                seconds=30)
            existing = await alerts_collection\\
                .find_one({
                "deviceId": b.deviceId,
                "type": "breach",
                "ts": {"$gte": recent_cutoff}
            })
            if existing:
                logger.info(
                    f"Duplicate breach skipped: "
                    f"{b.deviceId}")
                return {
                    "ok": True, 
                    "duplicate": True}
        except Exception as dedup_err:
            logger.error(
                f"Dedup check failed: {dedup_err}")
            # Continue even if dedup fails

        # ← Get hotel_id safely
        hotel_id = "default"
        try:
            if device and isinstance(device, dict):
                hotel_id = device.get(
                    "hotel_id", "default") or \
                    "default"
        except Exception as hotel_err:
            logger.error(
                f"hotel_id error: {hotel_err}")

        # ← Insert alert
        try:
            alert_doc = {
                "deviceId": b.deviceId,
                "roomId": b.roomId,
                "type": "breach",
                "severity": "critical",
                "message": "WiFi disabled on device",
                "rssi": rssi,
                "ts": breach_time,
                "acknowledged": False,
                "hotel_id": hotel_id
            }
            result = await alerts_collection\\
                .insert_one(alert_doc)
            logger.info(
                f"✅ Breach alert stored: "
                f"{b.deviceId} "
                f"id={result.inserted_id}")
        except Exception as insert_err:
            logger.error(
                f"Alert insert failed: {insert_err}")
            raise HTTPException(
                500,
                f"DB insert failed: {insert_err}")

        # ← Update device status
        try:
            await devices_collection.update_one(
                {"_id": b.deviceId},
                {"$set": {
                    "status": StatusEnum.breach,
                    "rssi": rssi,
                    "last_seen": get_utc_naive()
                }}
            )
        except Exception as update_err:
            logger.error(
                f"Device update failed: {update_err}")
            # Non-critical — continue

        # ← Broadcast via WebSocket
        try:
            ts_str = ""
            try:
                ts_str = to_ist_isoformat(
                    breach_time) or ""
            except Exception:
                ts_str = breach_time.isoformat()

            await broadcast_event("alert", {
                "type": "breach",
                "deviceId": b.deviceId,
                "roomId": b.roomId,
                "rssi": rssi,
                "message": "WiFi disabled",
                "timestamp": ts_str
            }, hotel_id=hotel_id)

            logger.info(
                f"🚨 BREACH broadcast: "
                f"{b.deviceId} hotel={hotel_id}")
        except Exception as ws_err:
            logger.error(
                f"WebSocket broadcast failed: "
                f"{ws_err}")
            # Non-critical — alert already saved

        # ← Push notification (non-critical)
        try:
            from notifications import NotificationService
            await NotificationService.send_breach_alert(
                device_id=b.deviceId,
                room_id=b.roomId,
                rssi=rssi
            )
        except Exception as push_err:
            logger.warning(
                f"Push notification failed "
                f"(non-critical): {push_err}")

        return {"ok": True}

    except HTTPException:
        raise
    except Exception as e:
        # ← Log the EXACT error so we can see it
        logger.error(
            f"❌ BREACH ENDPOINT CRASH: {e}",
            exc_info=True)
        raise HTTPException(
            500,
            f"Breach endpoint error: {str(e)}")'''

new_content = re.sub(old_func, new_func, content)
with open(main_path, "w", encoding="utf-8") as f:
    f.write(new_content)

print("Done")
