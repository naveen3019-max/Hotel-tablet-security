# Gmail Setup for Hotel Security Notifications

Quick guide to set up Gmail to send security alerts.

---

## Step 1: Enable 2-Factor Authentication

1. Go to your Google Account: https://myaccount.google.com/
2. Click **"Security"** in the left sidebar
3. Find **"2-Step Verification"**
4. Click **"Get Started"**
5. Follow the setup wizard (use phone number or authenticator app)
6. Complete verification

---

## Step 2: Create App Password

1. Go to **App Passwords**: https://myaccount.google.com/apppasswords
   - Or: Google Account → Security → App passwords

2. You may need to sign in again

3. Select configuration:
   - **Select app:** Mail
   - **Select device:** Other (Custom name)
   - Type: `Hotel Security System`

4. Click **"Generate"**

5. **Copy the 16-character password** (example: `abcd efgh ijkl mnop`)
   - Remove spaces when adding to .env file
   - You won't see this password again!

---

## Step 3: Configure Backend

Edit `backend-api/.env`:

```env
# Gmail Configuration
SMTP_ENABLED=true
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=yourhotel@gmail.com
SMTP_PASSWORD=abcdefghijklmnop
SMTP_FROM_EMAIL=security@yourhotel.com
ALERT_EMAIL_RECIPIENTS=owner@email.com,manager@email.com,security@email.com
```

**Important:**
- `SMTP_PASSWORD` = The 16-char app password (no spaces)
- `SMTP_USERNAME` = Your full Gmail address
- `SMTP_FROM_EMAIL` = Can be any email (display name in emails)
- `ALERT_EMAIL_RECIPIENTS` = Comma-separated list of who receives alerts

---

## Step 4: Test Email

Restart backend:
```powershell
docker-compose restart backend
```

Send test breach alert:
```powershell
# Get device token
$response = Invoke-RestMethod -Method POST `
  -Uri "http://localhost:8080/api/devices/register" `
  -ContentType "application/json" `
  -Body '{"deviceId":"TEST-EMAIL","roomId":"101"}'

$token = $response.token

# Trigger breach (sends email)
Invoke-RestMethod -Method POST `
  -Uri "http://localhost:8080/api/alert/breach" `
  -Headers @{Authorization="Bearer $token"} `
  -ContentType "application/json" `
  -Body '{"deviceId":"TEST-EMAIL","roomId":"101","rssi":-95}'
```

**Check your inbox!** You should receive:
```
Subject: 🚨 SECURITY BREACH: Device TEST-EMAIL (Room 101)

SECURITY BREACH DETECTED

Device: TEST-EMAIL
Room: 101
Signal Strength: -95 dBm
Time: 2025-12-21 12:00:00 UTC

The tablet has been moved out of the designated room.
Please investigate immediately.
```

---

## Email Templates

### Breach Alert Email
```
Subject: 🚨 SECURITY BREACH: Device TAB-101 (Room 101)
Priority: HIGH

Device removed from room.
Immediate investigation required.
```

### Battery Alert Email
```
Subject: 🔋 LOW BATTERY: Device TAB-101 (15%)
Priority: MEDIUM

Please charge device to avoid service interruption.
```

### Offline Alert Email
```
Subject: 📵 DEVICE OFFLINE: TAB-101
Priority: MEDIUM

Device has not responded in 5+ minutes.
Check device connection.
```

---

## Troubleshooting

### "Username and Password not accepted"
- **Solution:** Create App Password (not your regular Gmail password)
- Go to: https://myaccount.google.com/apppasswords
- Enable 2FA first if not already enabled

### "Less secure app access"
- **Solution:** Use App Password (recommended)
- Or enable "Less secure apps": https://myaccount.google.com/lesssecureapps
  - ⚠️ Not recommended for security

### Emails going to Spam
- **Solution:** Add sender to contacts
- Or check SPF/DKIM if using custom domain

### Wrong recipient
- Check `ALERT_EMAIL_RECIPIENTS` in .env
- Multiple emails: `email1@test.com,email2@test.com` (no spaces)
- Restart backend after changes

### Check logs
```powershell
docker-compose logs -f backend | Select-String "email"
```

Look for:
```
Email sent successfully to 2 recipients
```

Or errors:
```
Failed to send email: Authentication failed
```

---

## Alternative: Outlook/Hotmail

If using Outlook instead of Gmail:

```env
SMTP_ENABLED=true
SMTP_HOST=smtp-mail.outlook.com
SMTP_PORT=587
SMTP_USERNAME=yourhotel@outlook.com
SMTP_PASSWORD=your-outlook-password
```

No app password needed for Outlook, use regular password.

---

## Custom SMTP Server

If your hotel has its own email server:

```env
SMTP_ENABLED=true
SMTP_HOST=mail.yourhotel.com
SMTP_PORT=587
SMTP_USERNAME=security@yourhotel.com
SMTP_PASSWORD=your-smtp-password
```

Contact your IT department for SMTP details.

---

## Security Tips

✅ **Use App Passwords** - More secure than regular password
✅ **Different email for notifications** - Separate from personal email
✅ **Monitor sent emails** - Check Gmail sent folder regularly
✅ **Rotate passwords** - Change app password every 6 months
✅ **Backup recipients** - Add multiple emails in case one fails

---

**Emails configured! You'll now receive instant breach alerts. 📧**
