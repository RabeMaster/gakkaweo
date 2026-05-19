import { Button } from "@/shared/ui/Button";
import { useAuthStore } from "@/shared/stores/useAuthStore";
import type { PlayerInfo, RoomSettings } from "../types";
import { PlayerCard, EmptyPlayerSlot } from "./PlayerCard";
import { RoomSettingsPanel } from "./RoomSettingsPanel";

interface WaitingRoomProps {
  roomId: string;
  settings: RoomSettings;
  hostPublicId: string;
  players: PlayerInfo[];
  onReady: () => void;
  onStart: () => void;
  onLeave: () => void;
  onKick: (targetPublicId: string) => void;
  onDelegate: (targetPublicId: string) => void;
  onEditSettings: () => void;
}

export function WaitingRoom({
  roomId,
  settings,
  hostPublicId,
  players,
  onReady,
  onStart,
  onLeave,
  onKick,
  onDelegate,
  onEditSettings,
}: WaitingRoomProps) {
  const user = useAuthStore((s) => s.user);
  const isHost = user?.publicId === hostPublicId;
  const allOthersReady = players.filter((p) => p.publicId !== hostPublicId).every((p) => p.ready);
  const canStart = isHost && allOthersReady && players.length >= 2;
  const currentPlayer = players.find((p) => p.publicId === user?.publicId);
  const emptySlots = settings.maxPlayers - players.length;

  return (
    <div className="max-w-3xl mx-auto space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-black">{settings.title}</h1>
          <p className="text-sm text-gray-500 dark:text-gray-400 font-bold">
            방 코드: <span className="text-black dark:text-white tabular-nums">{roomId}</span>
          </p>
        </div>
        <Button variant="secondary" size="sm" onClick={onLeave}>
          나가기
        </Button>
      </div>

      <div className="flex gap-6">
        <div className="flex-1">
          <div className="grid grid-cols-3 gap-4">
            {players.map((player) => (
              <PlayerCard
                key={player.publicId}
                player={player}
                isCurrentUser={player.publicId === user?.publicId}
                isHost={isHost}
                onKick={() => onKick(player.publicId)}
                onDelegate={() => onDelegate(player.publicId)}
              />
            ))}
            {Array.from({ length: emptySlots }, (_, i) => (
              <EmptyPlayerSlot key={`empty-${i}`} />
            ))}
          </div>
        </div>
        <div className="w-56 shrink-0">
          <RoomSettingsPanel settings={settings} isHost={isHost} onEdit={onEditSettings} />
        </div>
      </div>

      <div className="flex justify-center gap-4">
        {isHost ? (
          <Button size="md" onClick={onStart} disabled={!canStart}>
            {canStart ? "게임 시작" : "전원 준비 대기"}
          </Button>
        ) : (
          <Button size="md" variant={currentPlayer?.ready ? "secondary" : "primary"} onClick={onReady}>
            {currentPlayer?.ready ? "준비 취소" : "준비"}
          </Button>
        )}
      </div>
    </div>
  );
}
