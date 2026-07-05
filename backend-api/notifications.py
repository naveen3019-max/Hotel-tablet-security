import aiosmtplib
import httpx
from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart
from config import settings
import logging
from datetime import datetime

# ← REMOVED: _celery_available global flag (Celery not supported on Render free tier)
# ← REMOVED: All Celery imports (tasks, process_breach_alert_task, etc.)
# ← KEPT: aiosmtplib, httpx, email, logging — these are safe

logger = logging.getLogger(__name__)


class NotificationService:
    """
    Unified notification service for Email and Slack alerts.
    ← FIXED: Celery removed entirely — Render free tier does NOT run Redis/Celery broker.
    All notifications now go direct via SMTP/Slack. Exceptions are caught internally
    so they NEVER propagate to the breach endpoint and cause a 500.
    """

    @staticmethod
    async def send_breach_alert(device_id: str, room_id: str, rssi: int):
        """
        ← FIXED: Removed Celery completely.
        Direct async notification — no broker dependency.
        """
        try:
            subject = (
                f"🚨 SECURITY BREACH: "
                f"Device {device_id} "
                f"(Room {room_id})"
            )
            message = (
                f"SECURITY BREACH DETECTED\n\n"
                f"Device: {device_id}\n"
                f"Room: {room_id}\n"
                f"Signal Strength: {rssi} dBm\n"
                f"Time: {datetime.utcnow().strftime('%Y-%m-%d %H:%M:%S UTC')}\n\n"
                f"The tablet has been moved out of the designated room.\n"
                f"Please investigate immediately."
            )
            await NotificationService._send_notifications(subject, message, "danger")
        except Exception as e:
            # ← Non-critical: log and swallow — NEVER crash the breach endpoint
            logger.warning(f"Breach notification failed (non-critical): {e}")

    @staticmethod
    async def send_battery_alert(device_id: str, level: int):
        """
        ← FIXED: No Celery. Direct async notification.
        """
        try:
            subject = (
                f"🔋 LOW BATTERY: "
                f"Device {device_id} ({level}%)"
            )
            message = (
                f"LOW BATTERY ALERT\n\n"
                f"Device: {device_id}\n"
                f"Battery Level: {level}%\n"
                f"Time: {datetime.utcnow().strftime('%Y-%m-%d %H:%M:%S UTC')}\n\n"
                f"Please charge the device soon to avoid service interruption."
            )
            await NotificationService._send_notifications(subject, message, "warning")
        except Exception as e:
            logger.warning(f"Battery notification failed (non-critical): {e}")

    @staticmethod
    async def send_device_offline_alert(device_id: str, last_seen: str):
        """Send device offline alert — no Celery."""
        try:
            subject = f"📵 DEVICE OFFLINE: {device_id}"
            message = (
                f"DEVICE OFFLINE ALERT\n\n"
                f"Device: {device_id}\n"
                f"Last Seen: {last_seen}\n"
                f"Time: {datetime.utcnow().strftime('%Y-%m-%d %H:%M:%S UTC')}\n\n"
                f"The device has not sent a heartbeat in over 5 minutes."
            )
            await NotificationService._send_notifications(subject, message, "danger")
        except Exception as e:
            logger.warning(f"Offline notification failed (non-critical): {e}")

    @staticmethod
    async def _send_notifications(subject: str, message: str, severity: str = "info"):
        """Send notifications via all enabled channels concurrently."""
        import asyncio
        tasks = []

        if settings.smtp_enabled:
            tasks.append(NotificationService._send_email(subject, message))

        if settings.slack_enabled:
            tasks.append(NotificationService._send_slack(message, settings.slack_webhook_url))

        if tasks:
            # return_exceptions=True ensures one channel failure doesn't kill others
            await asyncio.gather(*tasks, return_exceptions=True)

    @staticmethod
    async def _send_email(subject: str, body: str, to_email: str = None):
        """Send email via SMTP."""
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
        """Send Slack notification."""
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
