import os

def apply_auth_to_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    # Add import
    if 'import { useAuth } from "../hooks/useAuth";' not in content:
        content = content.replace('import { useWebSocket } from "../hooks/useWebSocket";',
                                  'import { useWebSocket } from "../hooks/useWebSocket";\nimport { useAuth } from "../hooks/useAuth";')

    # Add hooks
    if 'const { isAuthenticated, checking, logout } = useAuth();' not in content:
        content = content.replace('export default function EnhancedDashboard() {',
                                  'export default function EnhancedDashboard() {\n  const { isAuthenticated, checking, logout } = useAuth();')

    # Add guards
    guard_code = """
  if (checking) return (
    <div className="min-h-screen flex items-center justify-center">
      <div className="animate-spin w-8 h-8 border-4 border-blue-500 border-t-transparent rounded-full" />
    </div>
  );

  if (!isAuthenticated) return null;

  return (
"""
    if 'if (checking) return' not in content:
        content = content.replace('  return (\n    <main', guard_code + '    <main')

    # Add logout button
    logout_button = """
            <div className="flex items-center gap-2">
              <button
                onClick={logout}
                className="text-xs text-gray-500 hover:text-red-500 border border-gray-200 hover:border-red-300 px-3 py-1 rounded-full transition-colors"
              >
                Sign Out
              </button>
              <span className="text-xs text-gray-400 hidden sm:inline">{DASHBOARD_VERSION}</span>
              <LiveIndicator status={connectionStatus} />
            </div>
"""
    if 'Sign Out' not in content:
        old_header = """            <div className="flex items-center gap-2">
              <span className="text-xs text-gray-400 hidden sm:inline">{DASHBOARD_VERSION}</span>
              <LiveIndicator status={connectionStatus} />
            </div>"""
        content = content.replace(old_header, logout_button.strip('\n'))

    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)

apply_auth_to_file('dashboard/src/app/page.tsx')
apply_auth_to_file('dashboard/src/app/enhanced-page.tsx')

print("Auth modifications applied.")
