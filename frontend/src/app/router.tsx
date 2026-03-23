import { createBrowserRouter } from "react-router-dom";
import { Layout } from "@/app/layout/Layout";
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
      { path: "login", element: <LoginPage /> },
      { path: "mypage", element: <MyPage /> },
      { path: "*", element: <NotFoundPage /> },
    ],
  },
]);
