"use client";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "../../hooks/useAuth";

const API = process.env.NEXT_PUBLIC_API_URL || "https://hotel-backend-zqc1.onrender.com";


type Session = {
  session_id: string;
  hotel_id: string;
  username: string;
  device_info: string;
  ip_address: string;
  logged_in_at: string;
  last_active: string;
  expires_at: string;
};

type Hotel = {
  hotel_id: string;
  hotel_name: string;
  username: string;
  subscription_active: boolean;
  device_count: number;
  active_breaches: number;
  max_dashboard_logins?: number;
  active_sessions?: number;
};

export default function AdminPage() {
  const { isAuthenticated, checking, user, logout } = useAuth();
  const router = useRouter();

  const [hotels, setHotels] = useState<Hotel[]>([]);
  const [sessions, setSessions] = useState<Session[]>([]); // ← NEW
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Form state
  const [hotelId, setHotelId] = useState("");
  const [hotelName, setHotelName] = useState("");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [maxLogins, setMaxLogins] = useState(2); // ← NEW
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);
  const [createSuccess, setCreateSuccess] = useState(false);
  const [success, setSuccess] = useState<string | null>(null);

  const [showDeleteModal, setShowDeleteModal] = useState(false)
  const [hotelToDelete, setHotelToDelete] = useState<Hotel | null>(null)
  const [deleteStats, setDeleteStats] = useState<{
      device_count: number
      alert_count: number
      session_count: number
  } | null>(null)
  const [deleteConfirmText, setDeleteConfirmText] = useState("")
  const [deleteLoading, setDeleteLoading] = useState(false)

  useEffect(() => {
    if (checking) return;
    if (!isAuthenticated) {
      router.replace('/login');
      return;
    }
    if (user?.role !== 'super_admin') {
      router.replace('/');
      return;
    }
    if (user?.role === "super_admin") {
      fetchHotels();
      fetchSessions();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [checking, isAuthenticated, router, user]);

  
  const fetchSessions = async () => {
    try {
      const res = await fetch(`${API}/api/admin/sessions`, {
        headers: { "Authorization": `Bearer ${user?.token}` }
      });
      if (res.ok) {
        setSessions(await res.json());
      }
    } catch {
      // ignore error
    }
  };
  
  const handleForceLogout = async (sessionId: string) => {
    try {
      await fetch(`${API}/api/admin/sessions/${sessionId}`, {
        method: "DELETE",
        headers: { "Authorization": `Bearer ${user?.token}` }
      });
      fetchSessions();
      fetchHotels();
      fetchSessions();
    } catch {
      // ignore error
    }
  };

  const fetchHotels = async () => {
    try {
      const res = await fetch(`${API}/api/admin/hotels`, {
        headers: { "Authorization": `Bearer ${user?.token}` }
      });
      if (!res.ok) throw new Error("Failed to fetch hotels");
      const data = await res.json();
      setHotels(data);
    } catch (err: unknown) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  };

  const handleCreateHotel = async (e: React.FormEvent) => {
    e.preventDefault();
    setCreating(true);
    setCreateError(null);
    setCreateSuccess(false);

    try {
      const res = await fetch(`${API}/api/admin/create-hotel`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${user?.token}`
        },
        body: JSON.stringify({
          hotel_id: hotelId,
          hotel_name: hotelName,
          username,
          password,
          max_dashboard_logins: maxLogins
        })
      });

      if (!res.ok) {
        const data = await res.json();
        throw new Error(data.detail || "Failed to create hotel");
      }

      setCreateSuccess(true);
      setHotelId("");
      setHotelName("");
      setUsername("");
      setPassword("");
      fetchHotels();
      fetchSessions(); // Refresh list
    } catch (err: unknown) {
      setCreateError((err as Error).message);
    } finally {
      setCreating(false);
    }
  };

  const handleToggleStatus = async (hotelId: string) => {
    try {
      const res = await fetch(`${API}/api/admin/hotels/${hotelId}/toggle-status`, {
        method: "POST",
        headers: { "Authorization": `Bearer ${user?.token}` }
      });
      if (!res.ok) throw new Error("Failed to toggle status");
      
      setHotels(hotels.map(h => {
        if (h.hotel_id === hotelId) {
          return { ...h, subscription_active: !h.subscription_active };
        }
        return h;
      }));
    } catch (err) {
      alert("Error updating user status: " + (err as Error).message);
    }
  };

  const handleDeleteClick = async (hotel: Hotel) => {
      setHotelToDelete(hotel)
      setDeleteConfirmText("")
      setError("")
      
      // Fetch stats for confirmation dialog
      try {
          const res = await fetch(
              `${API}/api/admin/hotels/${hotel.hotel_id}/stats`,
              { headers: { "Authorization": `Bearer ${user?.token}` } }
          )
          const stats = await res.json()
          setDeleteStats(stats)
      } catch {
          setDeleteStats(null)
      }
      
      setShowDeleteModal(true)
  }

  const handleDeleteConfirm = async () => {
      if (!hotelToDelete) return
      
      // Verify typed hotel name matches
      if (deleteConfirmText !== hotelToDelete.hotel_name) {
          setError(
              "Hotel name does not match. " +
              "Please type the exact hotel name.")
          return
      }
      
      setDeleteLoading(true)
      setError("")
      
      try {
          const res = await fetch(
              `${API}/api/admin/hotels/${hotelToDelete.hotel_id}`,
              {
                  method: "DELETE",
                  headers: { "Authorization": `Bearer ${user?.token}` }
              }
          )
          
          if (!res.ok) {
              const err = await res.json()
              throw new Error(err.detail || "Delete failed")
          }
          
          const result = await res.json()
          
          setSuccess(
              `✅ Hotel "${hotelToDelete.hotel_name}" `+
              `deleted successfully! `+
              `Removed ${result.deleted.devices} `+
              `devices and ${result.deleted.alerts} `+
              `alerts.`
          )
          setShowDeleteModal(false)
          setHotelToDelete(null)
          setDeleteStats(null)
          
          // Refresh hotels list
          fetchHotels()
          
      } catch (e: unknown) {
          setError(e instanceof Error ? e.message : "Failed to delete hotel")
      } finally {
          setDeleteLoading(false)
      }
  }

  if (checking || loading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="animate-spin w-8 h-8 border-4 border-purple-500 border-t-transparent rounded-full" />
      </div>
    );
  }

  if (user?.role !== 'super_admin') return null;

  return (
    <main className="min-h-screen bg-slate-50 p-4 sm:p-6 font-sans text-slate-900">
      <div className="max-w-7xl mx-auto space-y-6">
        <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-3">
          <div>
            <h1 className="text-2xl sm:text-3xl font-bold tracking-tight text-slate-900">Super Admin Panel</h1>
            <p className="text-slate-500 text-sm mt-1">Manage tenant subscriptions and view fleet health.</p>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={() => router.push('/')}
              className="text-xs font-semibold text-blue-600 hover:text-blue-800 border border-blue-200 hover:border-blue-300 bg-blue-50 px-3 py-1 rounded-full transition-colors"
            >
              Back to Dashboard
            </button>
            <button
              onClick={logout}
              className="text-xs text-slate-500 hover:text-red-500 border border-slate-200 hover:border-red-300 px-3 py-1 rounded-full transition-colors"
            >
              Sign Out
            </button>
          </div>
        </div>

        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg text-sm font-medium">
            Error: {error}
          </div>
        )}
        {success && (
          <div className="bg-green-50 border border-green-200 text-green-700 px-4 py-3 rounded-lg text-sm font-medium">
            {success}
          </div>
        )}

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          
          {/* Create Hotel Form */}
          <div className="lg:col-span-1">
            <div className="bg-white rounded-xl shadow-sm border border-slate-200 p-6 sticky top-6">
              <h2 className="text-lg font-bold text-slate-800 mb-4">Create New Hotel</h2>
              
              <form onSubmit={handleCreateHotel} className="space-y-4">
                <div>
                  <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">Hotel ID</label>
                  <input
                    type="text"
                    required
                    value={hotelId}
                    onChange={e => setHotelId(e.target.value)}
                    className="w-full px-3 py-2 bg-slate-50 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-purple-500/50 focus:border-purple-500 transition-colors"
                    placeholder="e.g. hilton-nyc"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">Hotel Name</label>
                  <input
                    type="text"
                    required
                    value={hotelName}
                    onChange={e => setHotelName(e.target.value)}
                    className="w-full px-3 py-2 bg-slate-50 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-purple-500/50 focus:border-purple-500 transition-colors"
                    placeholder="e.g. Hilton New York"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">Admin Username</label>
                  <input
                    type="text"
                    required
                    value={username}
                    onChange={e => setUsername(e.target.value)}
                    className="w-full px-3 py-2 bg-slate-50 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-purple-500/50 focus:border-purple-500 transition-colors"
                    placeholder="e.g. hilton_admin"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">Admin Password</label>
                  <input
                    type="password"
                    required
                    value={password}
                    onChange={e => setPassword(e.target.value)}
                    className="w-full px-3 py-2 bg-slate-50 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-purple-500/50 focus:border-purple-500 transition-colors"
                    placeholder="••••••••"
                  />
                </div>

                
                {/* ← NEW */}
                <div>
                  <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">Max Dashboard Logins *</label>
                  <select
                    value={maxLogins}
                    onChange={e => setMaxLogins(parseInt(e.target.value))}
                    className="w-full px-3 py-2 bg-slate-50 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-purple-500/50 focus:border-purple-500 transition-colors"
                  >
                    <option value={1}>1 login (Basic)</option>
                    <option value={2}>2 logins (Standard)</option>
                    <option value={5}>5 logins (Premium)</option>
                    <option value={10}>10 logins (Enterprise)</option>
                    <option value={25}>25 logins (Custom)</option>
                  </select>
                  <p className="text-[10px] text-slate-400 mt-1">How many devices can login to dashboard at the same time</p>
                </div>

                {createError && (
                  <div className="text-xs text-red-600 bg-red-50 p-2 rounded border border-red-100 font-medium">
                    {createError}
                  </div>
                )}
                {createSuccess && (
                  <div className="text-xs text-green-600 bg-green-50 p-2 rounded border border-green-100 font-medium">
                    Hotel created successfully!
                  </div>
                )}

                <button
                  type="submit"
                  disabled={creating}
                  className="w-full py-2.5 px-4 bg-gradient-to-r from-purple-600 to-indigo-600 hover:from-purple-700 hover:to-indigo-700 text-white rounded-lg text-sm font-semibold shadow-sm shadow-purple-500/30 transition-all disabled:opacity-70 disabled:cursor-not-allowed"
                >
                  {creating ? "Creating..." : "Create Hotel Tenant"}
                </button>
              </form>
            </div>
          </div>

          {/* Tenants List */}
          <div className="lg:col-span-2">
            <div className="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
              <div className="px-6 py-4 border-b border-slate-100 bg-slate-50/50">
                <h2 className="text-lg font-bold text-slate-800">Active Tenants</h2>
              </div>
              <div className="divide-y divide-slate-100">
                {hotels.length === 0 ? (
                  <div className="p-6 text-center text-slate-500 text-sm">
                    No hotel tenants found.
                  </div>
                ) : (
                  hotels.map((h) => (
                    <div key={h.hotel_id} className="p-6 hover:bg-slate-50/50 transition-colors flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
                      <div>
                        <div className="flex items-center gap-2 mb-1">
                          <h3 className="font-bold text-slate-900 text-lg">{h.hotel_name}</h3>
                          {h.subscription_active ? (
                            <span className="bg-green-100 text-green-800 text-[10px] uppercase font-bold px-2 py-0.5 rounded tracking-wide">Active</span>
                          ) : (
                            <span className="bg-red-100 text-red-800 text-[10px] uppercase font-bold px-2 py-0.5 rounded tracking-wide">Inactive</span>
                          )}
                        </div>
                        <div className="text-sm text-slate-500 font-medium">
                          <span className="text-slate-400">ID:</span> {h.hotel_id} <span className="mx-2 text-slate-300">|</span> 
                          <span className="text-slate-400">User:</span> {h.username}
                        </div>
                      </div>
                      <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-4 sm:gap-6 w-full sm:w-auto justify-between sm:justify-end">
                        <div className="flex gap-6 bg-slate-50 px-4 py-2 rounded-lg border border-slate-100 justify-center">
                          <div className="text-center">
                            <div className="text-xs text-slate-500 uppercase font-bold tracking-wider mb-0.5">Devices</div>
                            <div className="font-semibold text-slate-900">{h.device_count}</div>
                          </div>
                          <div className="text-center">
                            <div className="text-xs text-slate-500 uppercase font-bold tracking-wider mb-0.5">Breaches</div>
                            <div className={`font-bold ${h.active_breaches > 0 ? 'text-red-600' : 'text-slate-900'}`}>{h.active_breaches}</div>
                          </div>

                          {/* ← NEW */}
                          <div className="text-center border-l border-slate-200 pl-4 ml-2">
                            <div className="text-xs text-slate-500 uppercase font-bold tracking-wider mb-0.5">Logins</div>
                            <div className="font-semibold text-slate-900">{h.active_sessions || 0}/{h.max_dashboard_logins || 2}</div>
                          </div>
                        </div>
                        <button
                          onClick={() => handleToggleStatus(h.hotel_id)}
                          className={`text-xs px-4 py-2 rounded-lg font-bold transition-all whitespace-nowrap ${
                            h.subscription_active 
                              ? 'bg-red-50 text-red-600 hover:bg-red-100 border border-red-200' 
                              : 'bg-green-50 text-green-600 hover:bg-green-100 border border-green-200'
                          }`}
                        >
                          {h.subscription_active ? 'Block User' : 'Unblock User'}
                        </button>
                        <button
                            onClick={() => handleDeleteClick(h)}
                            className="text-xs px-4 py-2 rounded-lg font-bold transition-all whitespace-nowrap bg-red-50 text-red-600 hover:bg-red-100 hover:text-red-700 border border-red-200 flex items-center justify-center gap-1.5"
                        >
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                <path d="M3 6h18M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/>
                            </svg>
                            Delete Tenant
                        </button>
                      </div>
                    </div>
                  ))
                )}
              </div>
            </div>
          </div>
        </div>
      </div>
    
        {/* ← NEW: Active Sessions Table */}
        <div className="mt-8 bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
          <div className="px-6 py-4 border-b border-slate-100 bg-slate-50/50">
            <h2 className="text-lg font-bold text-slate-800">Active Dashboard Sessions</h2>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm text-slate-600">
              <thead className="bg-slate-50 text-xs uppercase text-slate-500 font-semibold">
                <tr>
                  <th className="px-6 py-3">Hotel ID</th>
                  <th className="px-6 py-3">User</th>
                  <th className="px-6 py-3">Device Info</th>
                  <th className="px-6 py-3">IP Address</th>
                  <th className="px-6 py-3">Logged In</th>
                  <th className="px-6 py-3 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {sessions.map(s => (
                  <tr key={s.session_id} className="hover:bg-slate-50/50">
                    <td className="px-6 py-4 font-medium text-slate-900">{s.hotel_id}</td>
                    <td className="px-6 py-4">{s.username}</td>
                    <td className="px-6 py-4">{s.device_info}</td>
                    <td className="px-6 py-4 font-mono text-xs">{s.ip_address}</td>
                    <td className="px-6 py-4">{new Date(s.logged_in_at).toLocaleString()}</td>
                    <td className="px-6 py-4 text-right">
                      <button
                        onClick={() => handleForceLogout(s.session_id)}
                        className="text-xs bg-red-50 text-red-600 hover:bg-red-100 border border-red-200 px-3 py-1.5 rounded-lg font-semibold transition-colors"
                      >
                        Force Logout
                      </button>
                    </td>
                  </tr>
                ))}
                {sessions.length === 0 && (
                  <tr>
                    <td colSpan={6} className="px-6 py-8 text-center text-slate-500">No active dashboard sessions.</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>

      {showDeleteModal && hotelToDelete && (
        <DeleteHotelModal
            hotel={hotelToDelete}
            stats={deleteStats}
            confirmText={deleteConfirmText}
            onConfirmTextChange={setDeleteConfirmText}
            onConfirm={handleDeleteConfirm}
            onClose={() => {
                setShowDeleteModal(false)
                setHotelToDelete(null)
                setDeleteStats(null)
                setDeleteConfirmText("")
                setError("")
            }}
            loading={deleteLoading}
            error={error || ""}
        />
      )}
    </main>
  );
}

function DeleteHotelModal({
    hotel,
    stats,
    confirmText,
    onConfirmTextChange,
    onConfirm,
    onClose,
    loading,
    error
}: {
    hotel: Hotel
    stats: {
        device_count: number
        alert_count: number
        session_count: number
    } | null
    confirmText: string
    onConfirmTextChange: (v: string) => void
    onConfirm: () => void
    onClose: () => void
    loading: boolean
    error: string
}) {
    const isConfirmed = confirmText === hotel.hotel_name
    
    return (
        <div className="fixed inset-0 bg-black/80 flex items-center justify-center z-50 p-4">
            <div className="bg-gray-900 border border-red-800/50 rounded-2xl p-6 w-full max-w-md shadow-2xl shadow-red-900/20">
                
                {/* Warning Header */}
                <div className="flex items-center gap-3 mb-4">
                    <div className="w-12 h-12 bg-red-900/30 rounded-full flex items-center justify-center text-2xl flex-shrink-0">
                        ⚠️
                    </div>
                    <div>
                        <h2 className="text-xl font-bold text-red-400">
                            Delete Hotel
                        </h2>
                        <p className="text-gray-400 text-sm">
                            This action cannot be undone
                        </p>
                    </div>
                </div>
                
                {/* Hotel Info */}
                <div className="bg-gray-800 rounded-xl p-4 mb-4">
                    <div className="font-semibold text-white mb-1">
                        {hotel.hotel_name}
                    </div>
                    <div className="text-gray-400 text-sm">
                        @{hotel.username}
                    </div>
                </div>
                
                {/* What will be deleted */}
                <div className="bg-red-900/10 border border-red-800/30 rounded-xl p-4 mb-5">
                    <div className="text-red-400 text-xs font-semibold uppercase tracking-wider mb-3">
                        This will permanently delete:
                    </div>
                    <div className="space-y-2">
                        <div className="flex items-center gap-2 text-sm text-gray-300">
                            <span className="text-red-400">•</span>
                            Hotel account and login credentials
                        </div>
                        <div className="flex items-center gap-2 text-sm text-gray-300">
                            <span className="text-red-400">•</span>
                            <span className="font-semibold text-white">
                                {stats?.device_count ?? "?"}
                            </span>
                            &nbsp;registered tablet devices
                        </div>
                        <div className="flex items-center gap-2 text-sm text-gray-300">
                            <span className="text-red-400">•</span>
                            <span className="font-semibold text-white">
                                {stats?.alert_count ?? "?"}
                            </span>
                            &nbsp;security alert records
                        </div>
                        <div className="flex items-center gap-2 text-sm text-gray-300">
                            <span className="text-red-400">•</span>
                            <span className="font-semibold text-white">
                                {stats?.session_count ?? "?"}
                            </span>
                            &nbsp;active login sessions
                        </div>
                    </div>
                </div>
                
                {/* Error message */}
                {error && (
                    <div className="mb-4 p-3 bg-red-900/30 border border-red-700 rounded-lg text-red-400 text-sm">
                        {error}
                    </div>
                )}
                
                {/* Type to confirm */}
                <div className="mb-5">
                    <label className="text-xs text-gray-400 mb-2 block">
                        Type{" "}
                        <span className="text-white font-semibold font-mono bg-gray-800 px-1.5 py-0.5 rounded">
                            {hotel.hotel_name}
                        </span>
                        {" "}to confirm deletion:
                    </label>
                    <input
                        className="w-full bg-gray-800 border border-gray-700 rounded-lg px-4 py-2.5 text-white text-sm focus:border-red-500 outline-none placeholder-gray-600"
                        placeholder={hotel.hotel_name}
                        value={confirmText}
                        onChange={e => onConfirmTextChange(e.target.value)}
                        disabled={loading}
                    />
                </div>
                
                {/* Action Buttons */}
                <div className="flex gap-3">
                    <button
                        onClick={onClose}
                        disabled={loading}
                        className="flex-1 py-2.5 bg-gray-800 hover:bg-gray-700 disabled:opacity-50 rounded-lg text-sm transition"
                    >
                        Cancel
                    </button>
                    <button
                        onClick={onConfirm}
                        disabled={!isConfirmed || loading}
                        className={`flex-1 py-2.5 rounded-lg text-sm font-semibold transition
                            ${isConfirmed && !loading
                                ? "bg-red-600 hover:bg-red-700 text-white"
                                : "bg-gray-700 text-gray-500 cursor-not-allowed"
                            }`}
                    >
                        {loading ? (
                            <span className="flex items-center justify-center gap-2">
                                <span className="animate-spin">⟳</span>
                                Deleting...
                            </span>
                        ) : (
                            <span className="flex items-center justify-center gap-2">
                                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                    <path d="M3 6h18M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/>
                                </svg>
                                Delete Hotel
                            </span>
                        )}
                    </button>
                </div>
            </div>
        </div>
    )
}
