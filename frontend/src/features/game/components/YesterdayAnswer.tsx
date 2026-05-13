interface YesterdayAnswerProps {
  sentence: string;
  date: string;
}

export function YesterdayAnswer({ sentence, date }: YesterdayAnswerProps) {
  return (
    <div className="border-4 border-black dark:border-white shadow-brutal bg-indigo-50 dark:bg-gray-800 p-4">
      <p className="text-sm font-medium text-gray-600 dark:text-gray-400">어제의 정답 ({date})</p>
      <p className="text-lg font-extrabold mt-1 break-words">{sentence}</p>
    </div>
  );
}
