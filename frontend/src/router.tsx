import { createBrowserRouter } from "react-router-dom";
import { Layout } from "@/components/layout/Layout";
import { GamePage } from "@/pages/GamePage";
import { RankingPage } from "@/pages/RankingPage";
import { LoginPage } from "@/pages/LoginPage";
import { MyPage } from "@/pages/MyPage";
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
