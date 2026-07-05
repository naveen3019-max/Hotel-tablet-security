import os
import re

base_dir = r"c:\Users\navee\Downloads\Hotel-tablet-security-master\Hotel-tablet-security-master\WEDDING-CARD-cc895524abaddd4e0e79cc06099f9f102c0f16c7"
kiosk_service_path = os.path.join(base_dir, r"android-agent\app\src\main\java\com\example\hotel\service\KioskService.kt")
main_py_path = os.path.join(base_dir, r"backend-api\main.py")

# KioskService.kt
with open(kiosk_service_path, "r", encoding="utf-8") as f:
    kiosk = f.read()
kiosk = kiosk.replace("delay(60_000L)", "delay(30_000L) // ← every 30s\n                    // Render needs ping every 30s\n                    // to guarantee it stays awake\n                    // 60s gaps allow partial sleep state")
with open(kiosk_service_path, "w", encoding="utf-8") as f:
    f.write(kiosk)

# main.py
with open(main_py_path, "r", encoding="utf-8") as f:
    main_py = f.read()
main_py = main_py.replace("await asyncio.sleep(60)", "await asyncio.sleep(30)  # ← every 30 seconds\n        # 30s ensures Render NEVER starts sleeping\n        # Even if one ping fails next is 30s away")
with open(main_py_path, "w", encoding="utf-8") as f:
    f.write(main_py)

print("Done")
