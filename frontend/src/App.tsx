import { useEffect, useState, type ReactNode } from "react";
import { BrowserRouter, Routes, Route, Navigate, useLocation } from "react-router-dom";
import LoginPage from "./pages/LoginPage";
import DashboardPage from "./pages/DashboardPage";
import WorkspaceMembersPage from './pages/WorkspaceMembersPage';
import { API_URLS } from "./constants/apiUrls";

function ProtectedRoute({ children }: { children: ReactNode }) {
  const location = useLocation();
  const [authState, setAuthState] = useState<'checking' | 'authenticated' | 'unauthenticated'>('checking');

  useEffect(() => {
    let active = true;
    const url = new URL(window.location.href);
    const handoffCode = url.searchParams.get('code');
    if (url.searchParams.has('code') || url.searchParams.has('token')
      || url.searchParams.has('userId') || url.searchParams.has('username')
      || url.searchParams.has('role') || url.searchParams.has('workspaceId')) {
      for (const key of ['code', 'token', 'userId', 'username', 'role', 'workspaceId']) {
        url.searchParams.delete(key);
      }
      window.history.replaceState(null, '', `${url.pathname}${url.search}${url.hash}`);
    }

    const authenticate = async () => {
      try {
        if (handoffCode) {
          localStorage.removeItem('accessToken');
          const exchangeResponse = await fetch(API_URLS.AUTH.EXCHANGE, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ code: handoffCode }),
          });
          if (!exchangeResponse.ok) {
            throw new Error(`Authentication code exchange failed (${exchangeResponse.status})`);
          }
          const { access_token: accessToken } = await exchangeResponse.json() as { access_token: string };
          localStorage.setItem('accessToken', accessToken);
        }

        const accessToken = localStorage.getItem('accessToken');
        if (!accessToken) {
          throw new Error('No access token');
        }
        const response = await fetch(API_URLS.AUTH.ME, {
          headers: { Authorization: `Bearer ${accessToken}` },
        });
        if (!response.ok) {
          localStorage.removeItem('accessToken');
          throw new Error(`Authentication validation failed (${response.status})`);
        }

        localStorage.setItem('isLoggedIn', 'true');
        for (const key of ['userId', 'username', 'role', 'workspaceId', 'token']) {
          localStorage.removeItem(key);
        }
        if (active) setAuthState('authenticated');
      } catch (error) {
        console.error('Authentication failed:', error);
        if (active) setAuthState('unauthenticated');
      }
    };

    void authenticate();
    return () => {
      active = false;
    };
  }, [location.key]);

  if (authState === 'checking') {
    return <div className="flex min-h-screen items-center justify-center bg-[#0d1117] text-sm text-gray-400">Validating session...</div>;
  }
  if (authState === 'unauthenticated') {
    return <Navigate to="/login" replace />;
  }

  return <>{children}</>;
}

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route
          path="/workspace-members"
          element={
            <ProtectedRoute>
              <WorkspaceMembersPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/workspace-users"
          element={
            <ProtectedRoute>
              <WorkspaceMembersPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/dashboard"
          element={
            <ProtectedRoute>
              <DashboardPage />
            </ProtectedRoute>
          }
        />

        {/* Ana sayfa dashboard'a yönlendirir, oturum yoksa ProtectedRoute login'e atar */}
        <Route
          path="/"
          element={
            <ProtectedRoute>
              <DashboardPage />
            </ProtectedRoute>
          }
        />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
