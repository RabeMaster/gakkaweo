import { createBrowserRouter, Navigate } from "react-router-dom";
import { Layout } from "@/app/layout/Layout";
import { RequireAuth, RequireAdminAuth, RedirectIfAuth } from "@/app/guards";
import { HomePage } from "@/pages/HomePage";
import { LoginPage } from "@/features/auth/LoginPage";
import { MyPage } from "@/features/auth/MyPage";
import { AdminPage } from "@/features/admin/AdminPage";
import { DashboardTab } from "@/features/admin/components/DashboardTab";
import { SentenceTab } from "@/features/admin/components/SentenceTab";
import { UserTab } from "@/features/admin/components/UserTab";
import { SystemTab } from "@/features/admin/components/SystemTab";
import { NotFoundPage } from "@/pages/NotFoundPage";
import { PrivacyPolicyPage } from "@/pages/PrivacyPolicyPage";
import { TermsOfServicePage } from "@/pages/TermsOfServicePage";

export const router = createBrowserRouter([
  {
    element: <Layout />,
    children: [
      { index: true, element: <HomePage /> },
      {
        path: "login",
        element: (
          <RedirectIfAuth>
            <LoginPage />
          </RedirectIfAuth>
        ),
      },
      {
        path: "mypage",
        element: (
          <RequireAuth>
            <MyPage />
          </RequireAuth>
        ),
      },
      {
        path: "admin",
        element: (
          <RequireAdminAuth>
            <AdminPage />
          </RequireAdminAuth>
        ),
        children: [
          { index: true, element: <Navigate to="dashboard" replace /> },
          { path: "dashboard", element: <DashboardTab /> },
          { path: "sentences", element: <SentenceTab /> },
          { path: "users", element: <UserTab /> },
          { path: "system", element: <SystemTab /> },
        ],
      },
      { path: "privacy", element: <PrivacyPolicyPage /> },
      { path: "terms", element: <TermsOfServicePage /> },
      { path: "*", element: <NotFoundPage /> },
    ],
  },
]);
