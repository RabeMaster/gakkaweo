export function RankingEmptyState() {
  return (
    <div className="flex flex-col items-center justify-center py-8 text-center">
      <svg
        xmlns="http://www.w3.org/2000/svg"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth={2}
        className="w-10 h-10 text-gray-400 dark:text-gray-600 mb-3"
      >
        <path strokeLinecap="round" strokeLinejoin="round" d="M16 18l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2M3 7h4l3-3 3 3h4" />
        <path strokeLinecap="round" strokeLinejoin="round" d="M3 7v4a9 9 0 0 0 6 8.5M21 7v4a9 9 0 0 1-3.5 7.1" />
      </svg>
      <p className="text-sm font-bold text-gray-500 dark:text-gray-400">아직 랭킹이 없습니다</p>
      <p className="text-xs text-gray-400 dark:text-gray-500 mt-1">첫 번째 도전자가 되어보세요!</p>
    </div>
  );
}
