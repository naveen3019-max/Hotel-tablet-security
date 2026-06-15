"use client";
import { useRouter } from "next/navigation";
import { useState, useEffect } from "react";

export interface User {
  token: string;
  role: string;
  hotelId: string;
  hotelName: string;
  name: string;
  username: string;
}

export function useAuth() {
  const router = useRouter();
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [checking, setChecking] = useState(true);
  const [user, setUser] = useState<User | null>(null);

  useEffect(() => {
    const timer = setTimeout(() => {
      const token = localStorage.getItem('dashboard_token');
      if (token) {
        setIsAuthenticated(true);
        setUser({
          token,
          role: localStorage.getItem('dashboard_role') || 'hotel_admin',
          hotelId: localStorage.getItem('dashboard_hotel_id') || 'default',
          hotelName: localStorage.getItem('dashboard_hotel_name') || 'Default Hotel',
          name: localStorage.getItem('dashboard_name') || 'Admin',
          username: localStorage.getItem('dashboard_username') || 'admin'
        });
      } else {
        router.replace('/login');
      }
      setChecking(false);
    }, 0);
    return () => clearTimeout(timer);
  }, [router]);

  const logout = () => {
    localStorage.removeItem('hotel_auth'); // legacy
    localStorage.removeItem('dashboard_token');
    localStorage.removeItem('dashboard_role');
    localStorage.removeItem('dashboard_hotel_id');
    localStorage.removeItem('dashboard_hotel_name');
    localStorage.removeItem('dashboard_name');
    localStorage.removeItem('dashboard_username');
    router.replace('/login');
  };

  return { isAuthenticated, checking, logout, user };
}
