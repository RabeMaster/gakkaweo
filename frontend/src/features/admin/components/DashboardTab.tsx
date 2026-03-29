import { useTodayWidget, useTrends } from "@/features/admin/hooks/useAdminDashboard";
import type { DailyTrend } from "@/features/admin/types";

function WidgetCard({ label, value, sub }: { label: string; value: string | number; sub?: string }) {
  return (
    <div className="border-4 border-black dark:border-white bg-white dark:bg-gray-900 shadow-brutal-sm p-4">
      <p className="text-xs font-bold text-gray-500 dark:text-gray-400 uppercase tracking-wide">{label}</p>
      <p className="text-3xl font-black tabular-nums mt-1">{value}</p>
      {sub && <p className="text-xs font-medium text-gray-500 dark:text-gray-400 mt-1">{sub}</p>}
    </div>
  );
}

function TrendBar({ trend, maxParticipants }: { trend: DailyTrend; maxParticipants: number }) {
  const height = maxParticipants > 0 ? (trend.participants / maxParticipants) * 100 : 0;
  const dateLabel = trend.date.slice(5);

  return (
    <div className="flex flex-col items-center gap-1 flex-1 min-w-0">
      <div className="w-full h-32 flex items-end">
        <div
          className="w-full border-2 border-black dark:border-white bg-indigo-400 dark:bg-indigo-500 transition-all duration-300 relative group"
          style={{ height: `${Math.max(height, 2)}%` }}
        >
          <div className="absolute -top-8 left-1/2 -translate-x-1/2 hidden group-hover:block bg-black dark:bg-white text-white dark:text-black text-xs font-bold px-2 py-1 whitespace-nowrap z-10">
            {trend.participants}명 / {trend.clearRate.toFixed(0)}%
          </div>
        </div>
      </div>
      <span className="text-[10px] font-bold text-gray-500 dark:text-gray-400 tabular-nums">{dateLabel}</span>
    </div>
  );
}

function TrendChart({ trends }: { trends: DailyTrend[] }) {
  const maxParticipants = Math.max(...trends.map((t) => t.participants), 1);
  const recent = trends.slice(-14);

  return (
    <div className="border-4 border-black dark:border-white bg-white dark:bg-gray-900 shadow-brutal p-5">
      <h3 className="text-lg font-black mb-4">참여 추이 (최근 14일)</h3>
      <div className="flex gap-1 items-end">
        {recent.map((trend) => (
          <TrendBar key={trend.date} trend={trend} maxParticipants={maxParticipants} />
        ))}
      </div>
    </div>
  );
}

export function DashboardTab() {
  const { data: widget, isLoading: widgetLoading } = useTodayWidget();
  const { data: trends, isLoading: trendsLoading } = useTrends(30);

  if (widgetLoading) {
    return <p className="font-bold text-gray-400 animate-pulse py-8">로딩 중...</p>;
  }

  return (
    <div className="space-y-6">
      <h2 className="text-2xl font-extrabold">대시보드</h2>

      {widget && (
        <>
          <div className="grid grid-cols-4 gap-4">
            <WidgetCard label="오늘 참여자" value={widget.totalParticipants} />
            <WidgetCard label="클리어" value={widget.clearedCount} sub={`진행 중 ${widget.inProgressCount}`} />
            <WidgetCard
              label="평균 유사도"
              value={`${Number(widget.avgSimilarity).toFixed(1)}%`}
              sub={`평균 ${widget.avgAttemptCount.toFixed(1)}회`}
            />
            <WidgetCard
              label="미사용 문장"
              value={widget.unusedSentenceCount}
              sub={`현재 동시접속자: ${widget.sseConnectionCount}명`}
            />
          </div>

          <div className="border-4 border-black dark:border-white bg-indigo-50 dark:bg-gray-900 shadow-brutal-sm p-4">
            <p className="text-xs font-bold text-gray-500 dark:text-gray-400 uppercase tracking-wide">오늘 문장</p>
            <p className="text-lg font-black mt-1">{widget.sentence}</p>
          </div>
        </>
      )}

      {trendsLoading ? (
        <p className="font-bold text-gray-400 animate-pulse">추이 로딩 중...</p>
      ) : (
        trends && <TrendChart trends={trends.trends} />
      )}
    </div>
  );
}
