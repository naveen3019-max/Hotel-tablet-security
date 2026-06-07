import aiosmtplib
import httpx
from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart
from config import settings
import logging
from datetime import datetime

logger = logging.getLogger(__name__)
 
# Global flag to track if Celery is actually reachable
_celery_available = True
 
class NotificationService:
    """
    Unified notification service for Email and Slack alerts
    Uses Celery for async task processing when available
    """
    
    @staticmethod
    async def send_breach_alert(device_id: str, room_id: str, rssi: int):
        """Send breach alert via all enabled channels"""
        global _celery_available
        if settings.celery_enabled and _celery_available:
            # Try to use Celery task if available
            try:
                from tasks import process_breach_alert_task
                import asyncio
                await asyncio.to_thread(process_breach_alert_task.delay, device_id, room_id, rssi)
                logger.info(f"Queued breach alert task for {device_id}")
                return
            except (ImportError, Exception) as e:
                _celery_available = False
                logger.warning(f"Celery connection failed, disabling background tasks: {e}. Falling back to direct notify.")
        
        # Fallback to direct sending
        subject = f"🚨 SECURITY BREACH: Device {device_id} (Room {room_id})"
        message = f"""
SECURITY BREACH DETECTED

Device: {device_id}
Room: {room_id}
Signal Strength: {rssi} dBm
Time: {datetime.utcnow().strftime('%Y-%m-%d %H:%M:%S UTC')}

The tablet has been moved out of the designated room.
Please investigate immediately.
        """
        
        await NotificationService._send_notifications(subject, message, "danger")
    
    @staticmethod
    async def send_battery_alert(device_id: str, level: int):
        """Send low battery alert"""
        global _celery_available
        if settings.celery_enabled and _celery_available:
            # Try to use Celery task if available
            try:
                from tasks import process_battery_alert_task
                import asyncio
                await asyncio.to_thread(process_battery_alert_task.delay, device_id, level)
                logger.info(f"Queued battery alert task for {device_id}")
                return
            except (ImportError, Exception) as e:
                _celery_available = False
                logger.warning(f"Celery connection failed, disabling background tasks: {e}. Falling back to direct notify.")
        
        # Fallback to direct sending
        subject = f"🔋 LOW BATTERY: Device {device_id} ({level}%)"
        message = f"""
LOW BATTERY ALERT

Device: {device_id}
Battery Level: {level}%
Time: {datetime.utcnow().strftime('%Y-%m-%d %H:%M:%S UTC')}

Please charge the device soon to avoid service interruption.
        """
        
        await NotificationService._send_notifications(subject, message, "warning")
    
    @staticmethod
    async def send_device_offline_alert(device_id: str, last_seen: str):
        """Send device offline alert"""
        subject = f"📵 DEVICE OFFLINE: {device_id}"
        message = f"""
DEVICE OFFLINE ALERT

Device: {device_id}
Last Seen: {last_seen}
Time: {datetime.utcnow().strftime('%Y-%m-%d %H:%M:%S UTC')}

The device has not sent a heartbeat in over 5 minutes.
        """
        
        await NotificationService._send_notifications(subject, message, "danger")
    
    @staticmethod
    async def _send_notifications(subject: str, message: str, severity: str = "info"):
        """Send notifications via all enabled channels"""
        tasks = []
        
        if settings.smtp_enabled:
            tasks.append(NotificationService._send_email(subject, message))
        
        if settings.slack_enabled:
            tasks.append(NotificationService._send_slack(message, settings.slack_webhook_url))
        
        # Execute all notifications concurrently
        import asyncio
        await asyncio.gather(*tasks, return_exceptions=True)
    
    @staticmethod
    async def _send_email(subject: str, body: str, to_email: str = None):
        """Send email via SMTP"""
        try:
            recipients = to_email.split(",") if to_email else [
                email.strip() 
                for email in settings.alert_email_recipients.split(",")
                if email.strip()
            ]
            
            if not recipients:
                logger.warning("No email recipients configured")
                return
            
            msg = MIMEMultipart()
            msg["From"] = settings.smtp_from_email
            msg["To"] = ", ".join(recipients)
            msg["Subject"] = subject
            msg.attach(MIMEText(body, "plain"))
            
            await aiosmtplib.send(
                msg,
                hostname=settings.smtp_host,
                port=settings.smtp_port,
                username=settings.smtp_username,
                password=settings.smtp_password,
                use_tls=True
            )
            
            logger.info(f"Email sent successfully to {len(recipients)} recipients")
        except Exception as e:
            logger.error(f"Failed to send email: {e}")
    
    @staticmethod
    async def _send_slack(message: str, webhook_url: str):
        """Send Slack notification"""
        try:
            payload = {
                "text": message,
                "username": "Hotel Security Bot",
                "icon_emoji": ":lock:"
            }
            
            async with httpx.AsyncClient(timeout=10.0) as client:
                response = await client.post(webhook_url, json=payload)
                response.raise_for_status()
                logger.info("Slack notification sent successfully")
        except Exception as e:
            logger.error(f"Failed to send Slack notification: {e}")
