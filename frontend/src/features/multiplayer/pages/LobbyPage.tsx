import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Button } from "@/shared/ui/Button";
import { useToastStore } from "@/shared/stores/useToastStore";
import type { GameMode, LobbyRoomInfo, RoomSnapshot, WsNotification } from "../types";
import { useLobbySubscription } from "../hooks/useLobbySubscription";
import { useRoomActions } from "../hooks/useRoomActions";
import { useNotifications } from "../hooks/useNotifications";
import { RoomList } from "../components/RoomList";
import { LobbyFilters } from "../components/LobbyFilters";
import { CreateRoomDialog } from "../components/CreateRoomDialog";
import { RoomPasswordDialog } from "../components/RoomPasswordDialog";

export function LobbyPage() {
  const navigate = useNavigate();
  const addToast = useToastStore((s) => s.addToast);
  const rooms = useLobbySubscription();
  const { createRoom, joinRoom, quickJoin } = useRoomActions();

  const [modeFilter, setModeFilter] = useState<GameMode | "ALL">("ALL");
  const [waitingOnly, setWaitingOnly] = useState(false);
  const [showCreateDialog, setShowCreateDialog] = useState(false);
  const [passwordTarget, setPasswordTarget] = useState<LobbyRoomInfo | null>(null);

  useNotifications((notification: WsNotification) => {
    if (notification.type === "ROOM_CREATED" || notification.type === "ROOM_STATE") {
      const snapshot = notification.payload as RoomSnapshot;
      navigate(`/play/${snapshot.roomId}`);
    }
    if (notification.type === "ERROR") {
      const err = notification.payload as { message: string };
      addToast(err.message, "error");
    }
  });

  const filteredRooms = useMemo(() => {
    let result = rooms;
    if (modeFilter !== "ALL") {
      result = result.filter((r) => r.mode === modeFilter);
    }
    if (waitingOnly) {
      result = result.filter((r) => r.status === "WAITING");
    }
    return result;
  }, [rooms, modeFilter, waitingOnly]);

  const handleJoin = (room: LobbyRoomInfo) => {
    if (room.hasPassword) {
      setPasswordTarget(room);
    } else {
      joinRoom(room.roomId, null);
    }
  };

  return (
    <div className="max-w-4xl mx-auto space-y-6 py-2">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-black md:text-4xl">멀티플레이</h1>
        <div className="flex gap-3">
          <Button variant="secondary" size="sm" onClick={() => quickJoin()}>
            빠른 입장
          </Button>
          <Button size="sm" onClick={() => setShowCreateDialog(true)}>
            방 만들기
          </Button>
        </div>
      </div>

      <LobbyFilters
        modeFilter={modeFilter}
        onModeChange={setModeFilter}
        waitingOnly={waitingOnly}
        onWaitingOnlyChange={setWaitingOnly}
      />

      <RoomList rooms={filteredRooms} onJoin={handleJoin} />

      {showCreateDialog && (
        <CreateRoomDialog
          onClose={() => setShowCreateDialog(false)}
          onCreate={(params) => {
            createRoom(params);
            setShowCreateDialog(false);
          }}
        />
      )}

      {passwordTarget && (
        <RoomPasswordDialog
          roomTitle={passwordTarget.title}
          onClose={() => setPasswordTarget(null)}
          onSubmit={(pw) => {
            joinRoom(passwordTarget.roomId, pw);
            setPasswordTarget(null);
          }}
        />
      )}
    </div>
  );
}
