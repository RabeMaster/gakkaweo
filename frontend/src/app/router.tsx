import { createBrowserRouter } from "react-router-dom";
import { Layout } from "@/app/layout/Layout";
import { RequireAuth, RedirectIfAuth } from "@/app/guards";
import { GamePage } from "@/features/game/GamePage";
import { RankingPage } from "@/features/ranking/RankingPage";
import { LoginPage } from "@/features/auth/LoginPage";
import { MyPage } from "@/features/auth/MyPage";
import { NotFoundPage } from "@/pages/NotFoundPage";

export const router = createBrowserRouter([
  {
    element: <Layout />,
    children: [
      { index: true, element: <GamePage /> },
      { path: "ranking", element: <RankingPage /> },
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
      { path: "*", element: <NotFoundPage /> },
    ],
  },
]);
