import type { ReactNode } from "react";
import { BrowserRouter, Routes, Route, Navigate, useLocation } from "react-router-dom";
import LoginPage from "./pages/LoginPage";
import DashboardPage from "./pages/DashboardPage";
import WorkspaceMembersPage from './pages/WorkspaceMembersPage';

function ProtectedRoute({ children }: { children: ReactNode }) {
  const location = useLocation();
  const searchParams = new URLSearchParams(location.search);

  // 1. URL'de OAuth redirect parametreleri var mı kontrol et
  const urlToken = searchParams.get("token");
  const urlUserId = searchParams.get("userId");
  const urlUsername = searchParams.get("username");
  const urlRole = searchParams.get("role");
  const urlWorkspaceId = searchParams.get("workspaceId");

  // 2. URL'de token veya userId varsa senkronize olarak kaydet
  if (urlToken) {
    localStorage.setItem("accessToken", urlToken);
    localStorage.setItem("token", urlToken);
    localStorage.setItem("isLoggedIn", "true");
  }

  if (urlUserId) {
    localStorage.setItem("userId", urlUserId);
    localStorage.setItem("isLoggedIn", "true");
    if (urlUsername) localStorage.setItem("username", urlUsername);
    if (urlRole) localStorage.setItem("role", urlRole);
    if (urlWorkspaceId) localStorage.setItem("workspaceId", urlWorkspaceId);
  }

  // 3. LocalStorage oturum durumunu kontrol et
  const isLoggedIn =
    localStorage.getItem("isLoggedIn") === "true" ||
    !!localStorage.getItem("accessToken") ||
    !!localStorage.getItem("userId") ||
    !!urlUserId ||
    !!urlToken;

  // 4. Oturum yoksa Login'e yönlendir
  if (!isLoggedIn) {
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
