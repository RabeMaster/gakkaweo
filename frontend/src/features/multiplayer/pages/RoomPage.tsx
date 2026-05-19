import { useCallback, useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useToastStore } from "@/shared/stores/useToastStore";
import { apiFetch } from "@/shared/api/client";
import type { RoomSnapshot, WsNotification } from "../types";
import { useRoomStore } from "../stores/useRoomStore";
import { useRoomSubscription } from "../hooks/useRoomSubscription";
import { useRoomActions } from "../hooks/useRoomActions";
import { useNotifications } from "../hooks/useNotifications";
import { WaitingRoom } from "../components/WaitingRoom";
import { CountdownOverlay } from "../components/CountdownOverlay";
import { CreateRoomDialog } from "../components/CreateRoomDialog";

export function RoomPage() {
  const { roomId } = useParams<{ roomId: string }>();
  const navigate = useNavigate();
  const addToast = useToastStore((s) => s.addToast);
  const { toggleReady, startGame, leaveRoom, kickPlayer, delegateHost, updateSettings } = useRoomActions();
  const { settings, status, hostPublicId, players, setRoom, reset } = useRoomStore();
  const [showSettingsDialog, setShowSettingsDialog] = useState(false);

  useRoomSubscription(roomId);

  useEffect(() => {
    if (!roomId) {
      return;
    }
    apiFetch<RoomSnapshot>(`/multi/rooms/${roomId}`)
      .then((snapshot) => {
        if (snapshot) {
          setRoom(snapshot);
        }
      })
      .catch(() => {
        navigate("/play", { replace: true });
      });
  }, [roomId, setRoom, navigate]);

  const handleNotification = useCallback(
    (notification: WsNotification) => {
      if (notification.type === "ROOM_STATE") {
        setRoom(notification.payload as RoomSnapshot);
      }
      if (notification.type === "KICKED") {
        addToast("방에서 강퇴되었습니다", "error");
        reset();
        navigate("/play");
      }
      if (notification.type === "ERROR") {
        const err = notification.payload as { message: string };
        addToast(err.message, "error");
      }
    },
    [setRoom, reset, navigate, addToast],
  );

  useNotifications(handleNotification);

  useEffect(() => () => reset(), [reset]);

  if (!roomId || !settings || !status || !hostPublicId) {
    return (
      <div className="flex flex-1 items-center justify-center">
        <p className="text-lg font-bold text-gray-500">방에 연결 중...</p>
      </div>
    );
  }

  if (status === "COUNTDOWN") {
    return (
      <>
        <WaitingRoom
          roomId={roomId}
          settings={settings}
          hostPublicId={hostPublicId}
          players={players}
          onReady={() => {}}
          onStart={() => {}}
          onLeave={() => leaveRoom(roomId)}
          onKick={(target) => kickPlayer(roomId, target)}
          onDelegate={(target) => delegateHost(roomId, target)}
          onEditSettings={() => {}}
        />
        <CountdownOverlay />
      </>
    );
  }

  if (status === "PLAYING") {
    return (
      <div className="flex flex-1 items-center justify-center">
        <div className="text-center space-y-4">
          <h1 className="text-3xl font-black">게임 진행 중</h1>
          <p className="text-gray-500 dark:text-gray-400 font-bold">게임 로직은 Phase 6-3에서 구현됩니다</p>
        </div>
      </div>
    );
  }

  return (
    <>
      <WaitingRoom
        roomId={roomId}
        settings={settings}
        hostPublicId={hostPublicId}
        players={players}
        onReady={() => toggleReady(roomId)}
        onStart={() => startGame(roomId)}
        onLeave={() => {
          leaveRoom(roomId);
          reset();
          navigate("/play");
        }}
        onKick={(target) => kickPlayer(roomId, target)}
        onDelegate={(target) => delegateHost(roomId, target)}
        onEditSettings={() => setShowSettingsDialog(true)}
      />
      {showSettingsDialog && (
        <CreateRoomDialog
          onClose={() => setShowSettingsDialog(false)}
          onCreate={(params) => {
            updateSettings(roomId, params);
            setShowSettingsDialog(false);
          }}
        />
      )}
    </>
  );
}
