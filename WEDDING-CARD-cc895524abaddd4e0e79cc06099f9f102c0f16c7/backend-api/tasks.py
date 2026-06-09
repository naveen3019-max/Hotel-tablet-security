
from celery import Celery
from config import settings
import logging

logger = logging.getLogger(__name__)

# Initialize Celery app
# Only use Redis as broker/backend if enabled
broker_url = settings.redis_url if settings.celery_enabled else None
backend_url = settings.redis_url if settings.celery_enabled else None

celery_app = Celery(
    "hotel_security_tasks",
    broker=broker_url,
    backend=backend_url
)

celery_app.conf.update(
    task_serializer="json",
    accept_content=["json"],
    result_serializer="json",
    timezone="UTC",
    enable_utc=True,
    task_track_started=True,
    task_time_limit=300,  # 5 minutes
    task_soft_time_limit=240,  # 4 minutes
    worker_prefetch_multiplier=4,
    worker_max_tasks_per_child=1000,
    broker_connection_retry_on_startup=False,
    broker_connection_max_retries=1,
    broker_transport_options={'max_retries': 1},
)

@celery_app.task(name="send_email_alert", bind=True, max_retries=3)
def send_email_alert_task(self, subject: str, body: str, to_email: str):
    """Send email alert asynchronously"""
    try:
        import asyncio
        from notifications import NotificationService
        
        # Run async notification in sync context
        loop = asyncio.get_event_loop()
        if loop.is_running():
            # Create new event loop for Celery worker
            loop = asyncio.new_event_loop()
            asyncio.set_event_loop(loop)
        
        result = loop.run_until_complete(
            NotificationService._send_email(subject, body, to_email)
        )
        
        logger.info(f"Email sent successfully to {to_email}")
        return {"status": "sent", "to": to_email}
    except Exception as exc:
        logger.error(f"Email send failed: {exc}")
        raise self.retry(exc=exc, countdown=60 * (self.request.retries + 1))

@celery_app.task(name="send_slack_alert", bind=True, max_retries=3)
def send_slack_alert_task(self, message: str, webhook_url: str):
    """Send Slack alert asynchronously"""
    try:
        import asyncio
        from notifications import NotificationService
        
        loop = asyncio.get_event_loop()
        if loop.is_running():
            loop = asyncio.new_event_loop()
            asyncio.set_event_loop(loop)
        
        result = loop.run_until_complete(
            NotificationService._send_slack(message, webhook_url)
        )
        
        logger.info(f"Slack message sent successfully")
        return {"status": "sent"}
    except Exception as exc:
        logger.error(f"Slack send failed: {exc}")
        raise self.retry(exc=exc, countdown=60 * (self.request.retries + 1))

@celery_app.task(name="process_breach_alert")
def process_breach_alert_task(device_id: str, room_id: str, rssi: int):
    """Process breach alert with retries"""
    logger.info(f"Processing breach alert: {device_id} in {room_id}, RSSI: {rssi}")
    
    # Queue notification tasks
    send_email_alert_task.delay(
        subject=f"🚨 Security Breach - Room {room_id}",
        body=f"Device {device_id} removed from room {room_id}. RSSI: {rssi} dBm",
        to_email=settings.alert_email_recipients
    )
    
    if settings.slack_webhook_url:
        send_slack_alert_task.delay(
            message=f"🚨 *Security Breach*\nRoom: {room_id}\nDevice: {device_id}\nRSSI: {rssi} dBm",
            webhook_url=settings.slack_webhook_url
        )
    
    return {"status": "processed", "device_id": device_id}

@celery_app.task(name="process_battery_alert")
def process_battery_alert_task(device_id: str, battery_level: int):
    """Process battery alert with retries"""
    logger.info(f"Processing battery alert: {device_id} at {battery_level}%")
    
    send_email_alert_task.delay(
        subject=f"🔋 Low Battery - Device {device_id}",
        body=f"Device {device_id} battery at {battery_level}%",
        to_email=settings.alert_email_recipients
    )
    
    if settings.slack_webhook_url:
        send_slack_alert_task.delay(
            message=f"🔋 *Low Battery*\nDevice: {device_id}\nLevel: {battery_level}%",
            webhook_url=settings.slack_webhook_url
        )
    
    return {"status": "processed", "device_id": device_id}

@celery_app.task(name="check_heartbeat_status")
def check_heartbeat_status_task():
    """Check for stale heartbeats and mark devices as compromised"""
    import asyncio
    from db import devices_collection, StatusEnum
    from datetime import datetime, timedelta
    
    async def check():
        # cutoff = 40 seconds ago (grace period over 30s heartbeat)
        cutoff = datetime.utcnow() - timedelta(seconds=40)
        
        # Find devices that are OK but haven't been seen recently
        cursor = devices_collection.find({
            "status": StatusEnum.ok,
            "last_seen": {"$lt": cutoff}
        })
        
        stale_devices = await cursor.to_list(length=100)
        count = 0
        
        for device in stale_devices:
            count += 1
            device_id = device["_id"]
            logger.warning(f"Device {device_id} missed heartbeat. Marking OFFLINE.")
            
            # Update status
            await devices_collection.update_one(
                {"_id": device_id},
                {"$set": {"status": StatusEnum.offline}}
            )
            
            # Trigger Alert
            send_email_alert_task.delay(
                subject=f"🚨 DEVICE OFFLINE - {device_id}",
                body=f"Device {device_id} stopped sending heartbeats (Power Off / Force Stop / Disconnect).",
                to_email=settings.alert_email_recipients
            )
    
    loop = asyncio.get_event_loop()
    if loop.is_running():
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)
    
    loop.run_until_complete(check())
    return {"status": "checked"}

@celery_app.task(name="cleanup_old_alerts")
def cleanup_old_alerts_task(days: int = 90):
    """Periodic task to cleanup old alerts"""
    import asyncio
    from db import Session, Alert
    from sqlalchemy import select
    from datetime import datetime, timedelta, timezone
    
    async def cleanup():
        cutoff = datetime.now(timezone.utc) - timedelta(days=days)
        async with Session() as s:
            result = await s.execute(
                select(Alert).where(Alert.ts < cutoff)
            )
            old_alerts = result.scalars().all()
            
            count = len(old_alerts)
            for alert in old_alerts:
                await s.delete(alert)
            
            await s.commit()
            return count
    
    loop = asyncio.get_event_loop()
    if loop.is_running():
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)
    
    deleted_count = loop.run_until_complete(cleanup())
    logger.info(f"Deleted {deleted_count} old alerts")
    return {"deleted": deleted_count}

# Periodic task schedule
celery_app.conf.beat_schedule = {
    "cleanup-old-alerts-weekly": {
        "task": "cleanup_old_alerts",
        "schedule": 604800.0,  # 7 days in seconds
        "args": (90,)  # Keep 90 days of alerts
    },
    "check-heartbeat-status-30s": {
        "task": "check_heartbeat_status",
        "schedule": 30.0,
        "args": ()
    }
}
