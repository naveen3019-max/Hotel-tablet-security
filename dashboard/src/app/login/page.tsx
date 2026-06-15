"use client";
import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";

export default function LoginPage() {
  const router = useRouter();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
    if (localStorage.getItem('hotel_auth') === 'authenticated') {
      router.replace('/');
    }
  }, [router]);

  const handleLogin = (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');

    const validUsername = process.env.NEXT_PUBLIC_ADMIN_USER || 'hotel_admin';
    const validPassword = process.env.NEXT_PUBLIC_ADMIN_PASSWORD || 'secure@hotel2024';

    setTimeout(() => {
      if (username === validUsername && password === validPassword) {
        localStorage.setItem('hotel_auth', 'authenticated');
        router.push('/');
      } else {
        setError('Invalid username or password. Please try again.');
        setLoading(false);
      }
    }, 600); // Fake delay for smooth UX
  };

  if (!mounted) return null;

  return (
    <div className="min-h-screen relative flex items-center justify-center overflow-hidden bg-slate-950 font-sans">
      {/* Ambient Animated Background */}
      <div className="absolute inset-0 z-0">
        <div className="absolute top-[20%] left-[20%] w-96 h-96 bg-blue-600/20 rounded-full blur-3xl mix-blend-screen animate-blob" />
        <div className="absolute top-[30%] right-[20%] w-96 h-96 bg-indigo-600/20 rounded-full blur-3xl mix-blend-screen animate-blob animation-delay-2000" />
        <div className="absolute bottom-[20%] left-[30%] w-96 h-96 bg-cyan-600/20 rounded-full blur-3xl mix-blend-screen animate-blob animation-delay-4000" />
        <div className="absolute inset-0 bg-slate-950/60 backdrop-blur-3xl" />
      </div>

      <div className="relative z-10 w-full max-w-md px-6">
        <div className="bg-slate-900/80 backdrop-blur-xl border border-slate-800 rounded-3xl shadow-[0_0_50px_-12px_rgba(0,0,0,0.5)] shadow-blue-900/10 overflow-hidden p-8 sm:p-10 transition-all duration-500 hover:border-slate-700/80">
          
          {/* Logo / Header */}
          <div className="text-center mb-10">
            <div className="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-gradient-to-tr from-blue-600 to-cyan-500 shadow-lg shadow-blue-500/20 mb-6 transform transition-transform duration-500 hover:scale-105 hover:rotate-3">
              <svg className="w-8 h-8 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
              </svg>
            </div>
            <h1 className="text-2xl sm:text-3xl font-bold tracking-tight text-white mb-2">
              Nexus Security
            </h1>
            <p className="text-slate-400 text-sm font-medium tracking-wide">
              ENTERPRISE FLEET MANAGEMENT
            </p>
          </div>

          {/* Form */}
          <form onSubmit={handleLogin} className="space-y-5">
            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-slate-400 uppercase tracking-widest ml-1">Username</label>
              <div className="relative group">
                <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-slate-500 group-focus-within:text-blue-400 transition-colors">
                  <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                  </svg>
                </div>
                <input
                  type="text"
                  required
                  value={username}
                  onChange={e => setUsername(e.target.value)}
                  className="w-full pl-11 pr-4 py-3.5 bg-slate-950/50 border border-slate-800 text-white rounded-xl focus:outline-none focus:ring-2 focus:ring-blue-500/50 focus:border-blue-500 transition-all placeholder-slate-600 text-sm"
                  placeholder="Enter your username"
                />
              </div>
            </div>

            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-slate-400 uppercase tracking-widest ml-1">Password</label>
              <div className="relative group">
                <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-slate-500 group-focus-within:text-blue-400 transition-colors">
                  <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
                  </svg>
                </div>
                <input
                  type="password"
                  required
                  value={password}
                  onChange={e => setPassword(e.target.value)}
                  className="w-full pl-11 pr-4 py-3.5 bg-slate-950/50 border border-slate-800 text-white rounded-xl focus:outline-none focus:ring-2 focus:ring-blue-500/50 focus:border-blue-500 transition-all placeholder-slate-600 text-sm"
                  placeholder="••••••••"
                />
              </div>
            </div>

            {/* Error Message */}
            <div className={`transition-all duration-300 overflow-hidden ${error ? 'max-h-12 opacity-100 mt-5' : 'max-h-0 opacity-0 mt-0'}`}>
              <div className="flex items-center gap-2 text-red-400 bg-red-500/10 border border-red-500/20 px-4 py-3 rounded-xl text-xs font-medium">
                <svg className="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                {error}
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="relative w-full overflow-hidden bg-gradient-to-r from-blue-600 to-indigo-600 text-white rounded-xl text-sm font-semibold hover:from-blue-500 hover:to-indigo-500 disabled:opacity-70 disabled:cursor-not-allowed transition-all duration-300 py-4 shadow-lg shadow-blue-900/20 group mt-6"
            >
              <div className={`absolute inset-0 flex items-center justify-center transition-transform duration-300 ${loading ? 'translate-y-0 opacity-100' : '-translate-y-full opacity-0'}`}>
                <div className="w-5 h-5 border-2 border-white/30 border-t-white rounded-full animate-spin" />
              </div>
              <span className={`block transition-all duration-300 ${loading ? 'translate-y-full opacity-0' : 'translate-y-0 opacity-100'}`}>
                Sign In
              </span>
            </button>
          </form>
          
        </div>
        
        {/* Footer */}
        <p className="text-center text-slate-600 text-xs mt-8 font-medium tracking-wide">
          &copy; {new Date().getFullYear()} Hotel Security Systems. All rights reserved.
        </p>
      </div>
      
      {/* Global CSS for Background Animations */}
      <style dangerouslySetInnerHTML={{__html: `
        @keyframes blob {
          0% { transform: translate(0px, 0px) scale(1); }
          33% { transform: translate(30px, -50px) scale(1.1); }
          66% { transform: translate(-20px, 20px) scale(0.9); }
          100% { transform: translate(0px, 0px) scale(1); }
        }
        .animate-blob {
          animation: blob 10s infinite;
        }
        .animation-delay-2000 {
          animation-delay: 2s;
        }
        .animation-delay-4000 {
          animation-delay: 4s;
        }
      `}} />
    </div>
  );
}
