import type { LobbyRoomInfo } from "../types";

interface RoomListProps {
  rooms: LobbyRoomInfo[];
  onJoin: (room: LobbyRoomInfo) => void;
}

const MODE_LABELS: Record<string, string> = {
  SENTENCE: "문장",
  WORD: "단어",
};

const STATUS_LABELS: Record<string, { text: string; className: string }> = {
  WAITING: { text: "대기중", className: "bg-green-400 text-black" },
  COUNTDOWN: { text: "시작중", className: "bg-yellow-300 text-black" },
  PLAYING: { text: "게임중", className: "bg-red-400 text-white" },
};

export function RoomList({ rooms, onJoin }: RoomListProps) {
  if (rooms.length === 0) {
    return (
      <div className="border-4 border-black dark:border-white shadow-brutal bg-white dark:bg-gray-900 p-8 text-center">
        <p className="text-lg font-bold text-gray-500 dark:text-gray-400">생성된 방이 없습니다</p>
        <p className="text-sm text-gray-400 dark:text-gray-500 mt-1">새로운 방을 만들어보세요!</p>
      </div>
    );
  }

  return (
    <div className="border-4 border-black dark:border-white shadow-brutal overflow-hidden">
      <table className="w-full text-left">
        <thead>
          <tr className="border-b-4 border-black dark:border-white bg-gray-100 dark:bg-gray-800">
            <th className="px-4 py-3 text-sm font-black">방 제목</th>
            <th className="px-4 py-3 text-sm font-black w-20">모드</th>
            <th className="px-4 py-3 text-sm font-black w-24">인원</th>
            <th className="px-4 py-3 text-sm font-black w-20">상태</th>
            <th className="px-4 py-3 text-sm font-black w-12" />
          </tr>
        </thead>
        <tbody>
          {rooms.map((room) => {
            const statusInfo = STATUS_LABELS[room.status] ?? { text: room.status, className: "bg-gray-300" };
            const isFull = room.playerCount >= room.maxPlayers;
            const canJoin = room.status === "WAITING" && !isFull;

            return (
              <tr
                key={room.roomId}
                onClick={() => {
                  if (canJoin) {
                    onJoin(room);
                  }
                }}
                className={[
                  "border-b-2 border-black/20 dark:border-white/20 transition-colors",
                  canJoin ? "cursor-pointer hover:bg-yellow-50 dark:hover:bg-yellow-900/10" : "opacity-60",
                ].join(" ")}
              >
                <td className="px-4 py-3 font-bold">
                  <span className="flex items-center gap-2">
                    {room.hasPassword && <span className="text-xs">&#x1F512;</span>}
                    {room.title}
                  </span>
                  <span className="text-xs text-gray-500 dark:text-gray-400">{room.hostNickname}</span>
                </td>
                <td className="px-4 py-3 text-sm font-bold">{MODE_LABELS[room.mode] ?? room.mode}</td>
                <td className="px-4 py-3 text-sm font-bold tabular-nums">
                  {room.playerCount}/{room.maxPlayers}
                </td>
                <td className="px-4 py-3">
                  <span
                    className={[
                      "px-2 py-0.5 text-xs font-black border-2 border-black dark:border-white",
                      statusInfo.className,
                    ].join(" ")}
                  >
                    {statusInfo.text}
                  </span>
                </td>
                <td className="px-4 py-3">
                  {canJoin && <span className="text-xs font-black text-indigo-600 dark:text-indigo-400">입장</span>}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
