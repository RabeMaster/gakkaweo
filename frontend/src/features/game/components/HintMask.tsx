interface HintMaskProps {
  hintMask: string;
  charCounts: number[];
}

export function HintMask({ hintMask, charCounts }: HintMaskProps) {
  const words = hintMask.split(" ");

  return (
    <div className="flex flex-wrap gap-4 items-end">
      {words.map((word, i) => (
        <div key={i} className="flex flex-col items-center gap-1">
          <span className="text-2xl font-black tracking-widest">{word}</span>
          <span className="text-xs font-medium text-gray-500 dark:text-gray-400">{charCounts[i]}글자</span>
        </div>
      ))}
    </div>
  );
}
