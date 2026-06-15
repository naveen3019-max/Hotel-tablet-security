# Multi-Tenant SaaS Conversion Plan

This document outlines the architectural changes required to convert the Hotel Tablet Security system from a single-tenant application into a multi-tenant SaaS platform. 

## Proposed Changes

---

### Backend (FastAPI)

#### [MODIFY] `backend-api/db.py`
- Modify the `Hotel` model to include fields: `username`, `password`, `role`, `subscription_plan`, `subscription_active`, `max_devices`, `created_at`.
- Ensure indexes exist for querying hotels by username.

#### [MODIFY] `backend-api/auth.py`
- Enhance `AuthService.create_user_token` to accept and encode `role`, `hotel_id`, and `hotel_name` into the JWT payload.
- Update `get_current_user` to decode the JWT and extract `role` and `hotel_id` for use in filtering API responses.

#### [MODIFY] `backend-api/main.py`
- **POST `/api/auth/user-token`**: Implement dynamic DB check. If username/pass == admin/admin, issue super_admin token. Else check `hotels` collection and issue hotel_admin token.
- **POST `/api/admin/create-hotel`**: Super_admin only endpoint to insert new hotels into the DB.
- **GET `/api/admin/hotels`**: Super_admin only endpoint to list all hotels and their device/alert counts.
- **POST `/api/devices/register`**: Update the `DeviceRegister` pydantic model to expect `staffUsername` and `staffName`. Lookup the `hotel_id` based on `staffUsername` and save it to the device document along with `registered_by` and `staff_name`.
- **GET `/api/devices` & GET `/api/alerts/recent`**: Extract `hotel_id` and `role` from the JWT token. If `role == "hotel_admin"`, append `{"hotel_id": JWT.hotel_id}` to the MongoDB queries.

#### [MODIFY] `backend-api/websocket_manager.py`
- **`broadcast_event`**: When broadcasting a breach or update, the WebSocket connections need to be filtered so hotel admins only receive alerts for their own hotel. 
- *Note: We will either track `hotel_id` upon WebSocket connection or verify the device's `hotel_id` matches the connection's `hotel_id` before sending the message.*

---

### Frontend Dashboard (Next.js)

#### [MODIFY] `dashboard/src/app/login/page.tsx`
- Replace hardcoded credentials with a real API call to `POST /api/auth/user-token`.
- On success, parse the response and save `dashboard_token`, `dashboard_role`, `dashboard_hotel_id`, `dashboard_hotel_name`, and `dashboard_name` into `localStorage`.

#### [MODIFY] `dashboard/src/hooks/useAuth.ts`
- Enhance the hook to return the `role`, `hotel_name`, and `token` from `localStorage` so the UI can adapt dynamically.

#### [MODIFY] `dashboard/src/app/page.tsx` & `enhanced-page.tsx`
- **API Fetching**: Update `fetchDevices` and `fetchAlerts` to include `Authorization: Bearer ${token}` headers.
- **UI Logic**: 
  - Change the header to show `"🔑 SUPER ADMIN — All Hotels"` or `"🏨 [Hotel Name] — Your Devices"`.
  - Display `Registered by: [Name]` on device cards.
  - If `role === 'super_admin'`, show a button linking to the Super Admin panel and show hotel name badges on individual devices.

#### [NEW] `dashboard/src/app/admin/page.tsx`
- Create a dedicated super admin panel to list all subscribed hotels and a form to register new hotels by calling `POST /api/admin/create-hotel`.

---

### Android Agent (Kotlin)

#### [MODIFY] `android-agent/app/src/main/java/com/example/hotel/admin/ProvisioningActivity.kt`
- Add two new `EditText` fields to the layout: **Hotel Username** and **Staff Member Name**.
- Ensure both are required before allowing registration.

#### [MODIFY] `android-agent/app/src/main/java/com/example/hotel/data/AgentRepository.kt`
- Update the `DeviceRequest` data class and the Retrofit API call to pass `staffUsername` and `staffName` in the JSON body to the backend.

## User Review Required
> [!IMPORTANT]
> - Do you want the Android `ProvisioningActivity` layout to be updated via modifying the actual XML layout file, or should we dynamically add the fields programmatically in Kotlin to avoid disrupting your existing XML?
> - For WebSockets, currently, the frontend connects to `/ws/dashboard`. We will update this endpoint to require the JWT token as a query parameter (e.g., `?token=...`) so the server knows the `hotel_id` of the connection. Does this sound good?

## Verification Plan
1. **API Testing**: I will manually trigger `curl` requests against the backend to verify the filtering works for different roles.
2. **Dashboard Testing**: I will log in using hardcoded super admin credentials, create a hotel, and verify the admin panel functions.
3. **Android Build**: The changes to the Android app will be compiled and verified for syntax correctness.
