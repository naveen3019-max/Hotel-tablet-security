"use client";
import { useRouter } from "next/navigation";
import { useState, useEffect } from "react";

export function useAuth() {
  const router = useRouter();
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [checking, setChecking] = useState(true);

  useEffect(() => {
    const timer = setTimeout(() => {
      const token = localStorage.getItem('hotel_auth');
      if (token === 'authenticated') {
        setIsAuthenticated(true);
      } else {
        router.replace('/login');
      }
      setChecking(false);
    }, 0);
    return () => clearTimeout(timer);
  }, [router]);

  const logout = () => {
    localStorage.removeItem('hotel_auth');
    router.replace('/login');
  };

  return { isAuthenticated, checking, logout };
}
