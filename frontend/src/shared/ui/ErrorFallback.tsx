import { useRouteError } from "react-router-dom";
import { Card } from "@/shared/ui/Card";

interface ErrorFallbackProps {
  error: unknown;
  resetError?: () => void;
}

function getErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }
  return String(error);
}

function getErrorStack(error: unknown): string | undefined {
  if (error instanceof Error) {
    return error.stack;
  }
  return undefined;
}

export function ErrorFallback({ error, resetError }: ErrorFallbackProps) {
  const message = getErrorMessage(error);
  const stack = getErrorStack(error);

  return (
    <div className="max-w-md mx-auto space-y-6 text-center">
      <h1 className="text-6xl font-black">오류</h1>
      <Card className="space-y-4">
        <p className="text-lg font-medium">오류가 발생했습니다</p>

        {import.meta.env.DEV && (
          <details className="text-left border-2 border-black dark:border-white bg-gray-50 dark:bg-gray-800">
            <summary className="px-3 py-2 text-sm font-bold cursor-pointer select-none">{message}</summary>
            {stack && (
              <pre className="px-3 py-2 text-xs text-gray-600 dark:text-gray-400 overflow-x-auto border-t-2 border-black dark:border-white whitespace-pre-wrap break-words">
                {stack}
              </pre>
            )}
          </details>
        )}

        <div className="flex gap-3">
          {resetError && (
            <button
              type="button"
              onClick={resetError}
              className="flex-1 border-4 border-black dark:border-white bg-yellow-300 text-black font-bold px-6 py-3 shadow-brutal transition-all duration-100 hover:shadow-brutal-hover hover:translate-x-1 hover:translate-y-1 active:shadow-none active:translate-x-1.5 active:translate-y-1.5"
            >
              다시 시도
            </button>
          )}
          <a
            href="/"
            className="flex-1 border-4 border-black dark:border-white bg-white dark:bg-gray-900 text-black dark:text-white font-bold px-6 py-3 text-center shadow-brutal transition-all duration-100 hover:shadow-brutal-hover hover:translate-x-1 hover:translate-y-1 active:shadow-none active:translate-x-1.5 active:translate-y-1.5"
          >
            홈으로 돌아가기
          </a>
        </div>
      </Card>
    </div>
  );
}

export function RouteErrorFallback() {
  const error = useRouteError();
  return <ErrorFallback error={error} />;
}
