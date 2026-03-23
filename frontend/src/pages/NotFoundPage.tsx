import { Link } from "react-router-dom";
import { Card } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";

export function NotFoundPage() {
  return (
    <div className="max-w-md mx-auto space-y-6 text-center">
      <h1 className="text-6xl font-black">404</h1>
      <Card>
        <p className="text-lg font-medium mb-6">페이지를 찾을 수 없습니다</p>
        <Link to="/">
          <Button variant="primary">게임으로 돌아가기</Button>
        </Link>
      </Card>
    </div>
  );
}
