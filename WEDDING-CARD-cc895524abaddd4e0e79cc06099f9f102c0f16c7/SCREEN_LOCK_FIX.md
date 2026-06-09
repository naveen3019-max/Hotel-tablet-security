# SCREEN LOCK FALSE BREACH FIX

## Problem
When the screen is locked, Android's power-saving features temporarily disconnect WiFi, and the app was detecting this as a security breach and showing it in the dashboard.

## Root Cause
- Android automatically disconnects WiFi when screen locks (power-saving feature)
- WifiFence was treating ALL WiFi disconnections as breaches
- No distinction between:
  - **Normal**: WiFi disconnect during screen lock (power-saving)
  - **Breach**: WiFi turned OFF or device moved while screen is unlocked

## Solution Implemented

### 1. Created ScreenStateReceiver (`ScreenStateReceiver.kt`)
- Detects when screen is locked/unlocked
- Tracks screen state globally via static property
- Listens to: `ACTION_SCREEN_OFF`, `ACTION_SCREEN_ON`, `ACTION_USER_PRESENT`

### 2. Modified KioskService
- Registers ScreenStateReceiver on service start
- **DOUBLE PROTECTION**: Checks screen state before processing breach callback
- If screen is locked → Ignores breach (WiFi disconnect is normal)
- If screen is unlocked → Processes breach normally

### 3. Modified WifiFence
- **TRIPLE PROTECTION**: Checks screen state before triggering breach
- Resets breach counter if screen is locked
- Prevents false breach accumulation during screen lock
- Only triggers breach if:
  - Screen is UNLOCKED **AND**
  - WiFi is OFF or disconnected **AND**
  - Grace period expired

## Key Changes

### ScreenStateReceiver.kt (NEW FILE)
```kotlin
companion object {
    @Volatile
    var isScreenOn: Boolean = true
        private set
    
    fun getIsScreenLocked(): Boolean = !isScreenOn
}
```

### KioskService.kt
```kotlin
// Check if screen is locked - WiFi disconnects during screen lock are NORMAL
if (ScreenStateReceiver.getIsScreenLocked()) {
    Log.w("KioskService", "🌙 Screen is LOCKED - WiFi disconnect is normal (power-saving), ignoring breach")
    return@WifiFence
}
```

### WifiFence.kt
```kotlin
// CRITICAL: Don't trigger breach if screen is locked
if (ScreenStateReceiver.getIsScreenLocked()) {
    Log.w("WifiFence", "🌙 Screen is LOCKED - WiFi disconnect is normal (power-saving), resetting breach counter")
    breachCounter = 0
    return
}
```

## Testing Instructions

1. Install updated APK on tablet
2. Verify WiFi breach detection works when screen is UNLOCKED:
   - Turn WiFi OFF → Should show orange screen ✅
   - Dashboard should show breach alert ✅
3. Verify NO false alerts when screen is LOCKED:
   - Lock the screen (press power button) → Should NOT show orange screen ✅
   - Dashboard should NOT show breach ✅
   - Unlock screen → Normal operation resumes ✅

## Log Messages
- **Screen locked**: "🌙 Screen is LOCKED - WiFi disconnect is normal (power-saving), ignoring breach"
- **Screen unlocked**: "☀️ Screen UNLOCKED/ON - WiFi monitoring ACTIVE"
- **Breach detected**: "🚨🚨🚨 WiFi FENCE BREACH!" (only when screen is unlocked)

## Impact
- ✅ Eliminates false breach alerts during screen lock
- ✅ Maintains real security breach detection when screen is unlocked
- ✅ Preserves battery life (Android power-saving works normally)
- ✅ Cleaner dashboard (no false positives)

## Date Fixed
February 13, 2026 - 5:15 PM IST
