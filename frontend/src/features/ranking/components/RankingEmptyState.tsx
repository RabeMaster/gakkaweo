export function RankingEmptyState() {
  return (
    <div className="flex flex-col items-center justify-center py-8 text-center">
      <svg
        xmlns="http://www.w3.org/2000/svg"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth={1.5}
        strokeLinecap="round"
        strokeLinejoin="round"
        className="w-10 h-10 text-gray-400 dark:text-gray-600 mb-3"
      >
        {/* 컵 몸체 */}
        <path d="M7 4h10v7a5 5 0 0 1-10 0V4z" />
        {/* 왼쪽 손잡이 */}
        <path d="M7 6H5a2 2 0 0 0 0 4h2" />
        {/* 오른쪽 손잡이 */}
        <path d="M17 6h2a2 2 0 0 1 0 4h-2" />
        {/* 기둥 */}
        <path d="M12 16v2" />
        {/* 받침대 */}
        <path d="M8 20h8" />
      </svg>
      <p className="text-sm font-bold text-gray-500 dark:text-gray-400">아직 랭킹이 없습니다</p>
      <p className="text-xs text-gray-400 dark:text-gray-500 mt-1">첫 번째 도전자가 되어보세요!</p>
    </div>
  );
}
