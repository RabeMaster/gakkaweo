package com.gakkaweo.backend.multiplayer.room;

import com.gakkaweo.backend.auth.security.CustomUserDetails;
import com.gakkaweo.backend.auth.websocket.StompPrincipal;
import com.gakkaweo.backend.domain.member.repository.MemberRepository;
import com.gakkaweo.backend.multiplayer.room.dto.RoomCreateRequest;
import com.gakkaweo.backend.multiplayer.room.dto.RoomDelegateRequest;
import com.gakkaweo.backend.multiplayer.room.dto.RoomJoinRequest;
import com.gakkaweo.backend.multiplayer.room.dto.RoomKickRequest;
import com.gakkaweo.backend.multiplayer.room.dto.RoomSettingsRequest;
import com.gakkaweo.backend.multiplayer.room.dto.RoomSnapshot;
import com.gakkaweo.backend.multiplayer.room.dto.WsNotification;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Controller
@RequiredArgsConstructor
public class RoomController {

  private final RoomService roomService;
  private final MemberRepository memberRepository;

  @MessageMapping("/room/create")
  @SendToUser("/queue/notifications")
  public WsNotification createRoom(@Payload RoomCreateRequest request, Principal principal) {
    UUID publicId = extractPublicId(principal);
    String nickname = resolveNickname(publicId);
    RoomSnapshot snapshot = roomService.createRoom(request, publicId, nickname);
    return new WsNotification("ROOM_CREATED", snapshot);
  }

  @MessageMapping("/room/{roomId}/join")
  @SendToUser("/queue/notifications")
  public WsNotification joinRoom(
      @DestinationVariable String roomId, @Payload RoomJoinRequest request, Principal principal) {
    UUID publicId = extractPublicId(principal);
    String nickname = resolveNickname(publicId);
    RoomSnapshot snapshot = roomService.joinRoom(roomId, request, publicId, nickname);
    return new WsNotification("ROOM_STATE", snapshot);
  }

  @MessageMapping("/room/{roomId}/leave")
  public void leaveRoom(@DestinationVariable String roomId, Principal principal) {
    roomService.leaveRoom(roomId, extractPublicId(principal));
  }

  @MessageMapping("/room/{roomId}/ready")
  public void toggleReady(@DestinationVariable String roomId, Principal principal) {
    roomService.toggleReady(roomId, extractPublicId(principal));
  }

  @MessageMapping("/room/{roomId}/start")
  public void startGame(@DestinationVariable String roomId, Principal principal) {
    roomService.startGame(roomId, extractPublicId(principal));
  }

  @MessageMapping("/room/{roomId}/settings")
  public void updateSettings(
      @DestinationVariable String roomId,
      @Payload RoomSettingsRequest request,
      Principal principal) {
    roomService.updateSettings(roomId, request, extractPublicId(principal));
  }

  @MessageMapping("/room/{roomId}/kick")
  public void kickPlayer(
      @DestinationVariable String roomId, @Payload RoomKickRequest request, Principal principal) {
    roomService.kickPlayer(roomId, request, extractPublicId(principal));
  }

  @MessageMapping("/room/{roomId}/delegate")
  public void delegateHost(
      @DestinationVariable String roomId,
      @Payload RoomDelegateRequest request,
      Principal principal) {
    roomService.delegateHost(roomId, request, extractPublicId(principal));
  }

  @MessageMapping("/lobby/quick")
  @SendToUser("/queue/notifications")
  public WsNotification quickJoin(Principal principal) {
    UUID publicId = extractPublicId(principal);
    String nickname = resolveNickname(publicId);
    RoomSnapshot snapshot = roomService.quickJoin(publicId, nickname);
    return new WsNotification("ROOM_STATE", snapshot);
  }

  @MessageExceptionHandler(RoomException.class)
  @SendToUser("/queue/notifications")
  public WsNotification handleRoomException(RoomException e) {
    return new WsNotification(
        "ERROR", new ErrorPayload(e.getType().name(), e.getReason().name(), e.getMessage()));
  }

  @EventListener
  public void onSessionDisconnect(SessionDisconnectEvent event) {
    roomService.handleDisconnect(event);
  }

  private UUID extractPublicId(Principal principal) {
    if (principal instanceof StompPrincipal stomp) {
      return stomp.publicId();
    }
    if (principal instanceof AbstractAuthenticationToken auth
        && auth.getPrincipal() instanceof CustomUserDetails details) {
      return details.publicId();
    }
    return UUID.fromString(principal.getName());
  }

  private String resolveNickname(UUID publicId) {
    return memberRepository.findByPublicId(publicId).map(m -> m.getNickname()).orElse("Unknown");
  }

  private record ErrorPayload(String type, String reason, String message) {}
}
