import sys

# 1. Update Requests.kt
requests_path = "android-agent/app/src/main/java/com/example/hotel/data/Requests.kt"
with open(requests_path, "r", encoding="utf-8") as f:
    req_content = f.read()

target_req = """data class RegisterRequest(
    val deviceId: String,
    val roomId: String
)"""
replace_req = """data class RegisterRequest(
    val deviceId: String,
    val roomId: String,
    val staffUsername: String? = null,
    val staffName: String? = null
)"""
req_content = req_content.replace(target_req, replace_req)

with open(requests_path, "w", encoding="utf-8") as f:
    f.write(req_content)

# 2. Update ProvisioningActivity.kt
prov_path = "android-agent/app/src/main/java/com/example/hotel/admin/ProvisioningActivity.kt"
with open(prov_path, "r", encoding="utf-8") as f:
    prov_content = f.read()

# Update fields
target_fields = """    private lateinit var deviceIdInput: EditText
    private lateinit var roomIdInput: EditText
    private lateinit var backendUrlInput: EditText"""
replace_fields = """    private lateinit var deviceIdInput: EditText
    private lateinit var roomIdInput: EditText
    private lateinit var hotelUsernameInput: EditText
    private lateinit var staffNameInput: EditText
    private lateinit var backendUrlInput: EditText"""
prov_content = prov_content.replace(target_fields, replace_fields)

# Update layout
target_layout = """        roomIdInput = EditText(this).apply {
            hint = "e.g., 101, 102, 103..."
            setPadding(16, 16, 16, 16)
        }
        layout.addView(roomIdInput)
        
        // Status"""
replace_layout = """        roomIdInput = EditText(this).apply {
            hint = "e.g., 101, 102, 103..."
            setPadding(16, 16, 16, 16)
        }
        layout.addView(roomIdInput)
        
        // Hotel Username
        val hotelUsernameLabel = TextView(this).apply {
            text = "Hotel Username:"
            setPadding(0, 16, 0, 8)
        }
        layout.addView(hotelUsernameLabel)
        
        hotelUsernameInput = EditText(this).apply {
            hint = "e.g. hilton_admin"
            setPadding(16, 16, 16, 16)
        }
        layout.addView(hotelUsernameInput)

        // Staff Name
        val staffNameLabel = TextView(this).apply {
            text = "Staff Name:"
            setPadding(0, 16, 0, 8)
        }
        layout.addView(staffNameLabel)
        
        staffNameInput = EditText(this).apply {
            hint = "e.g. John Doe"
            setPadding(16, 16, 16, 16)
        }
        layout.addView(staffNameInput)
        
        // Status"""
prov_content = prov_content.replace(target_layout, replace_layout)

# Update register method start
target_reg = """    private fun registerDevice() {
        val deviceId = deviceIdInput.text.toString().trim()
        val roomId = roomIdInput.text.toString().trim()
        val backendUrl = backendUrlInput.text.toString().trim()
        
        if (deviceId.isEmpty() || roomId.isEmpty() || backendUrl.isEmpty()) {"""
replace_reg = """    private fun registerDevice() {
        val deviceId = deviceIdInput.text.toString().trim()
        val roomId = roomIdInput.text.toString().trim()
        val hotelUsername = hotelUsernameInput.text.toString().trim()
        val staffName = staffNameInput.text.toString().trim()
        val backendUrl = backendUrlInput.text.toString().trim()
        
        if (deviceId.isEmpty() || roomId.isEmpty() || hotelUsername.isEmpty() || staffName.isEmpty() || backendUrl.isEmpty()) {"""
prov_content = prov_content.replace(target_reg, replace_reg)

# Update API call
target_api = "val registerResponse = repo.register(tempAuth, RegisterRequest(deviceId, roomId))"
replace_api = "val registerResponse = repo.register(tempAuth, RegisterRequest(deviceId, roomId, hotelUsername, staffName))"
prov_content = prov_content.replace(target_api, replace_api)

# Update SharedPrefs
target_prefs = """                prefs.edit()
                    .putString("device_id", deviceId)
                    .putString("room_id", roomId)
                    .putString("jwt_token", jwtToken)"""
replace_prefs = """                prefs.edit()
                    .putString("device_id", deviceId)
                    .putString("room_id", roomId)
                    .putString("hotel_username", hotelUsername)
                    .putString("staff_name", staffName)
                    .putString("jwt_token", jwtToken)"""
prov_content = prov_content.replace(target_prefs, replace_prefs)


with open(prov_path, "w", encoding="utf-8") as f:
    f.write(prov_content)

print("Android files updated successfully.")
