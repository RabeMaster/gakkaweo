import { useNavigate } from "react-router-dom";
import { Card } from "@/shared/ui/Card";
import { Button } from "@/shared/ui/Button";

export function NotFoundPage() {
  const navigate = useNavigate();

  return (
    <div className="max-w-md mx-auto space-y-6 text-center">
      <h1 className="text-4xl md:text-6xl font-black">404</h1>
      <Card>
        <p className="text-lg font-medium mb-6">페이지를 찾을 수 없습니다</p>
        <Button variant="primary" onClick={() => navigate("/")}>
          게임으로 돌아가기
        </Button>
      </Card>
    </div>
  );
}
