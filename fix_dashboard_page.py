import sys

with open("dashboard/src/app/page.tsx", "r", encoding="utf-8") as f:
    content = f.read()

# 1. Update useAuth call
target_useAuth = "const { isAuthenticated, checking, logout } = useAuth();"
replace_useAuth = "const { isAuthenticated, checking, logout, user } = useAuth();\n  const router = useRouter();"
content = content.replace(target_useAuth, replace_useAuth)

# Add useRouter import
target_import = 'import { useEffect, useState, useCallback, useRef } from "react";'
replace_import = 'import { useEffect, useState, useCallback, useRef } from "react";\nimport { useRouter } from "next/navigation";'
content = content.replace(target_import, replace_import)

# 2. Update useWebSocket call
target_ws = "const { lastMessage, status: connectionStatus } = useWebSocket(API);"
replace_ws = "const { lastMessage, status: connectionStatus } = useWebSocket(API, user?.token || '');"
content = content.replace(target_ws, replace_ws)

# 3. Update fetch devices/alerts
target_fetch = '''        const [devicesRes, alertsRes] = await Promise.all([
          fetch(`${API}/api/devices`),
          fetch(`${API}/api/alerts/recent?limit=100`),
        ]);'''
replace_fetch = '''        const headers = { "Authorization": `Bearer ${user?.token}` };
        const [devicesRes, alertsRes] = await Promise.all([
          fetch(`${API}/api/devices`, { headers }),
          fetch(`${API}/api/alerts/recent?limit=100`, { headers }),
        ]);'''
content = content.replace(target_fetch, replace_fetch)

# 4. Update handleDeleteDevice
target_delete = "await fetch(`${API}/api/devices/${deviceId}`, { method: 'DELETE' });"
replace_delete = "await fetch(`${API}/api/devices/${deviceId}`, { method: 'DELETE', headers: { 'Authorization': `Bearer ${user?.token}` } });"
content = content.replace(target_delete, replace_delete)

# 5. Update acknowledgeAlert
target_ack = '''      await fetch(`${API}/api/alerts/acknowledge`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },'''
replace_ack = '''      await fetch(`${API}/api/alerts/acknowledge`, {
        method: "POST",
        headers: { "Content-Type": "application/json", "Authorization": `Bearer ${user?.token}` },'''
content = content.replace(target_ack, replace_ack)

# 6. Update acknowledgeAll
target_ack_all = "await fetch(`${API}/api/alerts/acknowledge-all`, { method: 'POST' });"
replace_ack_all = "await fetch(`${API}/api/alerts/acknowledge-all`, { method: 'POST', headers: { 'Authorization': `Bearer ${user?.token}` } });"
content = content.replace(target_ack_all, replace_ack_all)

# 7. Update Header title & Role & Admin link
target_title = '''          <div>
            <h1 className="text-2xl sm:text-3xl font-bold text-gray-900">Hotel Tablet Security</h1>
            <div className="flex flex-wrap items-center gap-4 text-sm mt-1">'''
replace_title = '''          <div>
            <h1 className="text-2xl sm:text-3xl font-bold text-gray-900">{user?.hotelName || "Hotel"} Tablet Security</h1>
            <div className="flex flex-wrap items-center gap-4 text-sm mt-1">
              <span className="bg-blue-100 text-blue-800 px-2 py-0.5 rounded text-xs font-bold uppercase tracking-wider">
                {user?.role === 'super_admin' ? 'Super Admin' : 'Hotel Admin'}
              </span>'''
content = content.replace(target_title, replace_title)

target_admin_link = '''              <button
                onClick={logout}
                className="text-xs text-gray-500 hover:text-red-500 border border-gray-200 hover:border-red-300 px-3 py-1 rounded-full transition-colors"
              >
                Sign Out
              </button>'''
replace_admin_link = '''              {user?.role === 'super_admin' && (
                <button
                  onClick={() => router.push('/admin')}
                  className="text-xs text-purple-600 hover:text-purple-800 border border-purple-200 hover:border-purple-300 bg-purple-50 px-3 py-1 rounded-full transition-colors font-semibold"
                >
                  Admin Panel
                </button>
              )}
              <button
                onClick={logout}
                className="text-xs text-gray-500 hover:text-red-500 border border-gray-200 hover:border-red-300 px-3 py-1 rounded-full transition-colors"
              >
                Sign Out
              </button>'''
content = content.replace(target_admin_link, replace_admin_link)

with open("dashboard/src/app/page.tsx", "w", encoding="utf-8") as f:
    f.write(content)

print("Updates to page.tsx applied successfully.")
