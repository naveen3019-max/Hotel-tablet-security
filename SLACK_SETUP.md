# Slack Setup for Real-Time Security Alerts

Get instant breach alerts in Slack with custom notifications.

---

## Step 1: Create Slack App

1. **Go to Slack API:** https://api.slack.com/apps
2. Click **"Create New App"**
3. Choose **"From scratch"**
4. **App Name:** `Hotel Security Bot`
5. **Pick a workspace:** Select your hotel's Slack workspace
6. Click **"Create App"**

---

## Step 2: Enable Incoming Webhooks

1. In the app settings page, find **"Features"** section
2. Click **"Incoming Webhooks"**
3. Toggle **"Activate Incoming Webhooks"** to **ON**
4. Scroll down and click **"Add New Webhook to Workspace"**
5. **Choose a channel:**
   - Create a new channel: `#security-alerts` (recommended)
   - Or use existing: `#general`, `#operations`, etc.
6. Click **"Allow"**
7. **Copy the Webhook URL** (you'll need this!)

Example webhook URL:
```
https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXX
```

⚠️ **Keep this URL secret!** Anyone with it can post to your Slack.

---

## Step 3: Configure Backend

Edit `backend-api/.env`:

```env
# Slack Configuration
SLACK_ENABLED=true
SLACK_WEBHOOK_URL=https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXX
```

Restart backend:
```powershell
docker-compose restart backend
```

---

## Step 4: Test Slack Alerts

Send test breach alert:
```powershell
# Register device and get token
$response = Invoke-RestMethod -Method POST `
  -Uri "http://localhost:8080/api/devices/register" `
  -ContentType "application/json" `
  -Body '{"deviceId":"TEST-SLACK","roomId":"101"}'

$token = $response.token

# Trigger breach (sends to Slack)
Invoke-RestMethod -Method POST `
  -Uri "http://localhost:8080/api/alert/breach" `
  -Headers @{Authorization="Bearer $token"} `
  -ContentType "application/json" `
  -Body '{"deviceId":"TEST-SLACK","roomId":"101","rssi":-95}'
```

**Check your Slack channel!** You should see:
```
🚨 SECURITY BREACH
Room: 101
Device: TEST-SLACK
RSSI: -95 dBm
```

---

## Slack Message Examples

### Breach Alert
```
🚨 *SECURITY BREACH*
Room: 101
Device: TAB-101
RSSI: -95 dBm
Time: 2025-12-21 12:00:00 UTC

Tablet removed from room. Investigate immediately!
```

### Battery Alert
```
🔋 *Low Battery Warning*
Device: TAB-101
Battery Level: 15%
Time: 2025-12-21 12:00:00 UTC

Please charge device soon to avoid service interruption.
```

### Device Offline
```
📵 *Device Offline*
Device: TAB-101
Last Seen: 2025-12-21 11:55:00 UTC
Time: 2025-12-21 12:00:00 UTC

Device has not sent heartbeat in over 5 minutes.
```

---

## Customize Slack Alerts

### Add Custom Icon

1. Go to your app settings: https://api.slack.com/apps
2. Click your app → **"Basic Information"**
3. Scroll to **"Display Information"**
4. **App icon:** Upload a lock/security icon (512x512 pixels)
5. **Short description:** "Hotel Tablet Security System"
6. Click **"Save Changes"**

### Change Bot Name

Edit `backend-api/notifications.py`:

```python
payload = {
    "text": message,
    "username": "Security Bot",  # Change this
    "icon_emoji": ":lock:"        # Change this
}
```

Available emojis:
- `:lock:` 🔒
- `:rotating_light:` 🚨
- `:warning:` ⚠️
- `:shield:` 🛡️
- `:bell:` 🔔

---

## Multiple Channels

To send different alerts to different channels:

### Option 1: Multiple Webhooks (Recommended)

Create multiple webhooks in Slack:
- `#critical-alerts` - For breaches
- `#maintenance` - For battery alerts
- `#monitoring` - For offline devices

Update `.env`:
```env
SLACK_WEBHOOK_URL=https://hooks.slack.com/services/T00000000/B11111111/XXXXXXXXXXXXXXXXXXXX
SLACK_WEBHOOK_MAINTENANCE=https://hooks.slack.com/services/T00000000/B22222222/XXXXXXXXXXXXXXXXXXXX
```

### Option 2: Mentions

Add mentions in messages:

```python
message = f"<!channel> 🚨 *SECURITY BREACH*\nRoom: {room_id}\n<@U12345678> Please investigate!"
```

Mention specific users:
- `<!channel>` - Mentions @channel
- `<!here>` - Mentions @here
- `<@U12345678>` - Mentions specific user (get User ID from Slack profile)

---

## Alert Priority Levels

Configure different alert types:

### Critical (Breach)
```python
{
    "attachments": [{
        "color": "danger",  # Red
        "text": "🚨 SECURITY BREACH - Room 101"
    }]
}
```

### Warning (Low Battery)
```python
{
    "attachments": [{
        "color": "warning",  # Orange
        "text": "🔋 Low Battery - TAB-101 (15%)"
    }]
}
```

### Info (Offline)
```python
{
    "attachments": [{
        "color": "#808080",  # Gray
        "text": "📵 Device Offline - TAB-101"
    }]
}
```

---

## Advanced: Interactive Buttons

Add buttons to acknowledge alerts directly from Slack:

```python
{
    "text": "🚨 SECURITY BREACH - Room 101",
    "attachments": [{
        "text": "Device TAB-101 removed from room",
        "callback_id": "breach_alert",
        "actions": [{
            "name": "acknowledge",
            "text": "Acknowledge",
            "type": "button",
            "value": "ack_breach_101"
        }, {
            "name": "investigate",
            "text": "Start Investigation",
            "type": "button",
            "value": "investigate_101",
            "style": "danger"
        }]
    }]
}
```

Requires Slack app OAuth setup (see Slack API docs).

---

## Troubleshooting

### Messages not appearing in Slack
```
# Check webhook URL is correct
# Test webhook manually:
curl -X POST -H 'Content-type: application/json' \
  --data '{"text":"Test message from curl"}' \
  YOUR_WEBHOOK_URL
```

### "Invalid token" error
- Webhook URL expired or revoked
- Regenerate webhook in Slack app settings

### Messages going to wrong channel
- Check which channel webhook is connected to
- Recreate webhook for correct channel

### Rate limiting
- Slack allows ~1 message per second
- Backend queues messages via Celery (handles this automatically)

### Check backend logs
```powershell
docker-compose logs -f backend | Select-String "slack"
```

Look for:
```
Slack notification sent successfully
```

Or errors:
```
Failed to send Slack notification: invalid_token
```

---

## Best Practices

✅ **Create dedicated channel** - `#security-alerts` separate from general chat
✅ **Set channel notifications** - Configure "All messages" in channel settings
✅ **Add team members** - Invite security staff, managers, front desk
✅ **Pin important info** - Pin channel description with alert meanings
✅ **Archive history** - Review alerts weekly to identify patterns
✅ **Test regularly** - Send test alerts to verify system working

---

## Slack vs Email

| Feature | Slack | Email |
|---------|-------|-------|
| Speed | Instant push | 1-2 minute delay |
| Mobile | Native app | Email app |
| Acknowledgment | Thread replies | Reply-all |
| History | Searchable | Inbox clutter |
| Team visibility | Shared channel | Individual inboxes |
| Cost | Free tier available | Free |

**Recommendation:** Use **both** - Slack for real-time team awareness, Email for audit trail and management reports.

---

## Integration with Dashboard

When alert is acknowledged in dashboard, it can also update Slack thread:

```
✅ Alert acknowledged by: John (Manager)
Notes: False alarm - housekeeping
Time: 2025-12-21 12:05:00 UTC
```

(Requires additional webhook configuration)

---

**Slack configured! Your team will now get instant breach alerts. 🔔**
