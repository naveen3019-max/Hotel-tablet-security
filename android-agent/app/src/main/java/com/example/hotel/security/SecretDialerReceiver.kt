package com.example.hotel.security

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.example.hotel.admin.AdminActivity

/**
 * SecretDialerReceiver — admin-only icon restore via secret dialer code.
 *
 * HOW IT WORKS
 * ───────────────────────────────────────────────────────────────────────────
 * After successful registration the app hides its launcher icon by disabling
 * the activity-alias (.MainActivityAlias).  This receiver re-enables that
 * alias so the icon reappears, then opens AdminActivity (which already
 * enforces PIN verification before showing any admin options).
 *
 * TWO TRIGGER PATHS
 * ───────────────────────────────────────────────────────────────────────────
 * 1. android.intent.action.DIAL  +  tel: URI
 *    The dialer broadcasts this before opening the dialer UI.
 *    We inspect the number; if it equals SECRET_CODE we intercept.
 *    Works on: Samsung, Lenovo, Redmi, stock Android.
 *
 * 2. android.provider.Telephony.SECRET_CODE  +  android_secret_code://7378423
 *    Fired when the user dials *#*#7378423#*#* on a device whose dialer
 *    supports the USSD secret-code pattern (most Android 7+ devices).
 *    This path fires INSTEAD of opening the dialer, so no call is placed.
 *
 * SECRET CODE
 * ───────────────────────────────────────────────────────────────────────────
 * Default: *#*#7378423#*#*  (spells "SERVICE" on a phone keypad)
 * The tel: path also matches the plain number "7378423" when typed and dialed.
 * Change SECRET_CODE below to use a different number.
 *
 * HOW TO ACCESS APP AFTER ICON IS HIDDEN
 * ───────────────────────────────────────────────────────────────────────────
 * Method 1 (primary)   : Dial *#*#7378423#*#* in the phone dialer
 * Method 2 (ADB/admin) : adb shell pm enable com.example.hotel/.MainActivityAlias
 * Method 3 (last resort): Factory reset via Recovery Mode
 *
 * ADB COMMANDS
 * ───────────────────────────────────────────────────────────────────────────
 * Show icon:
 *   adb shell pm enable com.example.hotel/.MainActivityAlias
 *
 * Hide icon:
 *   adb shell pm disable-user --user 0 com.example.hotel/.MainActivityAlias
 *
 * Check current state:
 *   adb shell pm list packages -d | grep hotel    (disabled = hidden)
 */
class SecretDialerReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SecretDialerReceiver"

        // ← Change this to your preferred numeric secret code.
        // Must be digits only (no * or #).
        private const val SECRET_CODE = "7378423"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {

            // ─── Path 1: tel: URI dialed ─────────────────────────────────────
            // The dialer broadcasts ACTION_DIAL with a tel: URI before any call
            // is placed.  We extract the number and compare to SECRET_CODE.
            Intent.ACTION_DIAL -> {
                val number = intent.data?.schemeSpecificPart ?: return
                Log.d(TAG, "DIAL broadcast received for number: $number")

                if (number == SECRET_CODE) {
                    Log.i(TAG, "✅ Secret code matched via DIAL — restoring icon")
                    showAppIcon(context)
                    openAdminActivity(context)
                }
            }

            // ─── Path 2: *#*#7378423#*#* USSD secret code ───────────────────
            // android.provider.Telephony.SECRET_CODE fires before the dialer
            // opens when the user types the *#*#<host>#*#* pattern.  The host
            // in the intent's data URI matches the <host> we declared in the
            // manifest, so no additional number check is needed here.
            "android.provider.Telephony.SECRET_CODE" -> {
                Log.i(TAG, "✅ Secret code matched via SECRET_CODE broadcast — restoring icon")
                showAppIcon(context)
                openAdminActivity(context)
            }

            else -> return // Ignore any other broadcasts routed here
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Re-enables the activity-alias so the launcher icon reappears.
     *
     * The alias (.MainActivityAlias) is the ONLY component that carries the
     * LAUNCHER intent-filter.  Enabling it makes every launcher add the icon
     * back to the home screen within ~5 seconds (no reboot required).
     *
     * MainActivity itself is never touched — it stays enabled at all times.
     */
    private fun showAppIcon(context: Context) {
        try {
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context, "${context.packageName}.MainActivityAlias"), // ← alias
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP  // ← running services are unaffected
            )
            Log.i(TAG, "✅ App icon VISIBLE — MainActivityAlias re-enabled")
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ Failed to show app icon: ${e.message}")
        }
    }

    /**
     * Launches AdminActivity which immediately shows a PIN dialog.
     *
     * AdminActivity already enforces PIN verification before revealing any
     * admin options, so there is no additional auth step needed here.
     *
     * FLAG_ACTIVITY_NEW_TASK is required when starting an Activity from a
     * BroadcastReceiver (no Activity back-stack is available at this point).
     */
    private fun openAdminActivity(context: Context) {
        try {
            val intent = Intent(context, AdminActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
            Log.i(TAG, "✅ AdminActivity launched (PIN gate will appear)")
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ Failed to open AdminActivity: ${e.message}")
        }
    }
}
