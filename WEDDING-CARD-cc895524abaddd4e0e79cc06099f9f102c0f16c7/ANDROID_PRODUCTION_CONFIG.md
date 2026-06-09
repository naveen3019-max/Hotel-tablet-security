# 📱 Android App - Production Configuration Guide

This guide explains how to configure the Android app to connect to your deployed backend.

---

## 🎯 Understanding the Configuration

The Android app stores the backend URL in **SharedPreferences** with a default value. You can:

1. **Option A**: Change default URL in code (recommended for production)
2. **Option B**: Add settings screen for users to change URL (flexible)
3. **Option C**: Hardcode production URL (simple)

---

## ✅ Option A: Change Default URL (Recommended)

### 1. Update Default Backend URL

**File**: `android-agent/app/src/main/java/com/example/hotel/data/AgentRepository.kt`

```kotlin
// Line 12 - Change default URL
private val baseUrl = prefs.getString(
    "backend_url", 
    "https://hotel-backend.onrender.com"  // ← Change this to your deployed backend URL
) ?: "https://hotel-backend.onrender.com"  // ← Change this too
```

### 2. Update API Token (if using)

Search for where API token is used and update it:

```powershell
# Find API token usage
Get-ChildItem -Path android-agent\app\src -Recurse -Filter "*.kt" | Select-String "api_token\|apiToken\|API_TOKEN" -List
```

Update the token to match your backend's `API_TOKEN`.

### 3. Rebuild APK

```powershell
cd android-agent
.\gradlew clean
.\gradlew assembleRelease
```

The APK will be at: `app/build/outputs/apk/release/app-release.apk`

---

## ✅ Option B: Add Settings Screen (Flexible)

Create a settings screen where users can change the backend URL.

### 1. Create Settings Activity

**File**: `android-agent/app/src/main/java/com/example/hotel/ui/SettingsActivity.kt`

```kotlin
package com.example.hotel.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.hotel.R

class SettingsActivity : AppCompatActivity() {
    private lateinit var urlInput: EditText
    private lateinit var tokenInput: EditText
    private lateinit var saveButton: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        val prefs = getSharedPreferences("agent", MODE_PRIVATE)
        
        urlInput = findViewById(R.id.backend_url_input)
        tokenInput = findViewById(R.id.api_token_input)
        saveButton = findViewById(R.id.save_button)
        
        // Load current values
        urlInput.setText(prefs.getString("backend_url", "https://hotel-backend.onrender.com"))
        tokenInput.setText(prefs.getString("api_token", ""))
        
        saveButton.setOnClickListener {
            val url = urlInput.text.toString().trim()
            val token = tokenInput.text.toString().trim()
            
            // Validate URL
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                Toast.makeText(this, "URL must start with http:// or https://", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Save to SharedPreferences
            prefs.edit()
                .putString("backend_url", url)
                .putString("api_token", token)
                .apply()
            
            Toast.makeText(this, "Settings saved! Restart app to apply.", Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
```

### 2. Create Settings Layout

**File**: `android-agent/app/src/main/res/layout/activity_settings.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Backend Settings"
        android:textSize="24sp"
        android:textStyle="bold"
        android:layout_marginBottom="24dp" />

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Backend URL"
        android:textSize="16sp"
        android:layout_marginBottom="8dp" />

    <EditText
        android:id="@+id/backend_url_input"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="https://hotel-backend.onrender.com"
        android:inputType="textUri"
        android:layout_marginBottom="16dp" />

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="API Token"
        android:textSize="16sp"
        android:layout_marginBottom="8dp" />

    <EditText
        android:id="@+id/api_token_input"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="Enter API token"
        android:inputType="textPassword"
        android:layout_marginBottom="24dp" />

    <Button
        android:id="@+id/save_button"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Save Settings" />

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Note: App must be restarted for changes to take effect"
        android:textSize="12sp"
        android:textColor="#666"
        android:layout_marginTop="16dp" />

</LinearLayout>
```

### 3. Add to AndroidManifest.xml

```xml
<activity
    android:name=".ui.SettingsActivity"
    android:label="Settings"
    android:parentActivityName=".MainActivity" />
```

### 4. Add Settings Button to Main Activity

Add a menu button or settings icon that opens SettingsActivity:

```kotlin
// In your MainActivity
val settingsButton = findViewById<Button>(R.id.settings_button)
settingsButton.setOnClickListener {
    startActivity(Intent(this, SettingsActivity::class.java))
}
```

---

## ✅ Option C: Hardcode URL (Simple)

If you don't want users to change the URL:

### Update AgentRepository.kt

```kotlin
package com.example.hotel.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import okhttp3.OkHttpClient
import android.content.Context

class AgentRepository(context: Context) {
    // Remove SharedPreferences and hardcode URL
    private val baseUrl = "https://hotel-backend.onrender.com"  // ← Your backend URL
    
    companion object {
        @Volatile
        private var instance: AgentRepository? = null
        
        fun default(context: Context): AgentRepository {
            return instance ?: synchronized(this) {
                instance ?: AgentRepository(context).also { instance = it }
            }
        }
        
        private val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }
    
    private val retrofitAlerts = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val alerts: AlertApi = retrofitAlerts.create(AlertApi::class.java)
}
```

---

## 🔧 Testing Connection

### 1. Check Backend is Running

```powershell
# Test backend health
curl https://hotel-backend.onrender.com/health
```

Should return: `{"status":"ok"}`

### 2. Install Updated APK

```powershell
# Build release APK
cd android-agent
.\gradlew assembleRelease

# Install on connected device
adb install -r app\build\outputs\apk\release\app-release.apk
```

### 3. Test Registration

1. Open app on device
2. Register a new device
3. Check dashboard for new device

---

## 🔐 Security Notes

### HTTPS Only in Production

- Always use `https://` for production
- Never use `http://` unless testing locally
- Render, Railway, Fly.io all provide free SSL

### API Token

- Match the token in your backend `.env`
- Keep it secret
- Don't commit tokens to Git

### Certificate Pinning (Advanced)

For extra security, consider adding certificate pinning:

```kotlin
val certificatePinner = CertificatePinner.Builder()
    .add("hotel-backend.onrender.com", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
    .build()

val client = OkHttpClient.Builder()
    .certificatePinner(certificatePinner)
    .build()

private val retrofitAlerts = Retrofit.Builder()
    .baseUrl(baseUrl)
    .client(client)
    .addConverterFactory(MoshiConverterFactory.create(moshi))
    .build()
```

---

## 📦 Distribution

### APK Signing (Production)

For production apps, sign your APK:

1. **Generate keystore**:
```powershell
keytool -genkey -v -keystore hotel-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias hotel-key
```

2. **Update `build.gradle.kts`**:
```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("hotel-release-key.jks")
            storePassword = "your-keystore-password"
            keyAlias = "hotel-key"
            keyPassword = "your-key-password"
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

3. **Build signed APK**:
```powershell
.\gradlew assembleRelease
```

### Distribution Options

1. **Direct APK** - Share file via email/drive
2. **Firebase App Distribution** - Beta testing
3. **Google Play Store** - Public release
4. **Enterprise MDM** - For hotel-managed devices

---

## 🧪 Testing Checklist

- [ ] Backend URL updated
- [ ] API token configured
- [ ] APK built successfully
- [ ] App installs without errors
- [ ] Can register device
- [ ] Device appears in dashboard
- [ ] Can send test alert
- [ ] Alert appears in dashboard
- [ ] Network connectivity alerts work

---

## 🆘 Troubleshooting

### "Unable to connect to backend"

- Check backend URL is correct
- Verify backend is running (visit `/health` endpoint)
- Check device has internet connection
- Verify SSL certificate is valid (if using HTTPS)

### "Authentication failed"

- Check API token matches backend
- Verify token is saved correctly
- Check logs in backend for more details

### "Network error"

- Check firewall settings
- Verify DNS resolves correctly
- Check CORS settings in backend

---

## 📝 Quick Reference

| Environment | Backend URL | Notes |
|-------------|-------------|-------|
| Local Dev | `http://10.0.2.2:8080` | Android emulator |
| Local Dev | `http://YOUR_LOCAL_IP:8080` | Physical device |
| Production | `https://hotel-backend.onrender.com` | Replace with your URL |

---

**Next**: See [DEPLOY_ONLINE.md](DEPLOY_ONLINE.md) for full deployment guide
