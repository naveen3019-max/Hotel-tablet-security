"use client";
import { useEffect, useState } from "react";
import WhiteDashboard from "./WhiteDashboard";
import DarkDashboard from "./DarkDashboard";

export default function DashboardSwitcher() {
  const [isWebView, setIsWebView] = useState<boolean | null>(null);

  useEffect(() => {
    const ua = navigator.userAgent || navigator.vendor || (window as unknown as { opera?: string }).opera || "";
    const isWv = 
      (ua.indexOf('wv') > -1) || 
      (ua.indexOf('Android') > -1 && ua.indexOf('Version/') > -1);
    
    // Check local storage or URL query for testing override as well
    const urlParams = new URLSearchParams(window.location.search);
    const forceTheme = urlParams.get('theme');
    
    setTimeout(() => {
      if (forceTheme === 'light') {
        setIsWebView(true);
      } else if (forceTheme === 'dark') {
        setIsWebView(false);
      } else {
        setIsWebView(!!isWv);
      }
    }, 0);
  }, []);

  // Avoid hydration mismatch by not rendering until we know the platform
  if (isWebView === null) return null;

  return isWebView ? <WhiteDashboard /> : <DarkDashboard />;
}
