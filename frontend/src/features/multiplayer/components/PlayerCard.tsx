import type { PlayerInfo } from "../types";

interface PlayerCardProps {
  player: PlayerInfo;
  isCurrentUser: boolean;
  isHost: boolean;
  onKick?: () => void;
  onDelegate?: () => void;
}

export function PlayerCard({ player, isCurrentUser, isHost, onKick, onDelegate }: PlayerCardProps) {
  return (
    <div
      className={[
        "border-4 border-black dark:border-white shadow-brutal-sm p-4 flex flex-col items-center gap-2 relative group",
        player.ready ? "bg-green-100 dark:bg-green-900/30" : "bg-white dark:bg-gray-900",
      ].join(" ")}
    >
      {player.isHost && (
        <span className="absolute -top-3 -left-1 text-lg" title="방장">
          &#x1F451;
        </span>
      )}

      <div className="w-14 h-14 border-4 border-black dark:border-white bg-gray-200 dark:bg-gray-700 flex items-center justify-center text-2xl font-black">
        {player.nickname.charAt(0)}
      </div>

      <span className="text-sm font-black truncate max-w-full">{player.nickname}</span>

      {player.ready ? (
        <span className="px-2 py-0.5 text-xs font-black border-2 border-black dark:border-white bg-green-400 text-black">
          준비
        </span>
      ) : (
        <span className="px-2 py-0.5 text-xs font-black border-2 border-black dark:border-white bg-gray-200 dark:bg-gray-700">
          대기
        </span>
      )}

      {isHost && !isCurrentUser && !player.isHost && (
        <div className="absolute top-1 right-1 flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
          {onDelegate && (
            <button
              type="button"
              onClick={onDelegate}
              className="text-xs font-black text-indigo-600 dark:text-indigo-400 hover:underline"
              title="방장 위임"
            >
              위임
            </button>
          )}
          {onKick && (
            <button
              type="button"
              onClick={onKick}
              className="text-xs font-black text-red-500 hover:underline"
              title="강퇴"
            >
              강퇴
            </button>
          )}
        </div>
      )}
    </div>
  );
}

export function EmptyPlayerSlot() {
  return (
    <div className="border-4 border-dashed border-gray-300 dark:border-gray-700 p-4 flex flex-col items-center gap-2">
      <div className="w-14 h-14 border-4 border-dashed border-gray-300 dark:border-gray-700 flex items-center justify-center text-gray-300 dark:text-gray-600 text-2xl">
        ?
      </div>
      <span className="text-sm font-bold text-gray-300 dark:text-gray-600">빈 자리</span>
      <span className="px-2 py-0.5 text-xs font-black border-2 border-transparent invisible">-</span>
    </div>
  );
}
