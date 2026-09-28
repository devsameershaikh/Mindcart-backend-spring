package com.mindcart.backend.socket;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.annotation.OnConnect;
import com.corundumstudio.socketio.annotation.OnDisconnect;
import com.corundumstudio.socketio.annotation.OnEvent;
import com.mindcart.backend.entity.ListMember;
import com.mindcart.backend.repository.ListMemberRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Direct port of the Express app's:
 *
 *   io.on("connection", async (socket) => {
 *     socket.join(`user:${socket.userId}`);
 *     const memberships = await prisma.listMember.findMany(...);
 *     memberships.forEach((m) => socket.join(`list:${m.listId}`));
 *     socket.on("list:join", (listId) => socket.join(`list:${listId}`));
 *     socket.on("list:leave", (listId) => socket.leave(`list:${listId}`));
 *   });
 *
 * `userId` is populated by the AuthTokenListener in SocketIOConfig before
 * this fires, so it is guaranteed present and already verified here.
 */
@Component
// Matches the guard on the SocketIOServer bean -- without this, disabling
// the server via SOCKETIO_ENABLED=false would leave this @Component
// trying to @Autowired a bean that doesn't exist, and the app would fail
// to boot entirely.
@ConditionalOnProperty(prefix = "app.socketio", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SocketIOEventHandler {

    private static final Logger log = LoggerFactory.getLogger(SocketIOEventHandler.class);

    private final SocketIOServer server;
    private final ListMemberRepository listMemberRepository;

    public SocketIOEventHandler(SocketIOServer server, ListMemberRepository listMemberRepository) {
        this.server = server;
        this.listMemberRepository = listMemberRepository;
        server.addListeners(this);
    }

    @PostConstruct
    public void start() {
        server.start();
        log.info("Socket.IO server listening on port {}", server.getConfiguration().getPort());
    }

    // Room joins now happen in the auth-token listener (SocketIOConfig).
    // Do NOT disconnect when userId is null here: this callback can run
    // before the auth handshake finishes, and disconnecting caused the
    // connect/disconnect loop seen in the logs.
    @OnConnect
    public void onConnect(SocketIOClient client) {
        log.info("socket transport connected: sessionId={}", client.getSessionId());
    }

    @OnDisconnect
    public void onDisconnect(SocketIOClient client) {
        String userId = client.get("userId");
        log.info("socket disconnected: userId={} sessionId={}", userId, client.getSessionId());
    }
    // Client calls this right after accepting an invite so it starts
    // receiving live updates for the newly-shared list without reconnecting.
    @OnEvent("list:join")
    public void onListJoin(SocketIOClient client, String listId) {
        String userId = client.get("userId");
        if (userId == null || listId == null || listId.isBlank()) return;
        boolean isMember = listMemberRepository.findByListIdAndUserId(listId, userId).isPresent();
        log.info("list:join attempt: userId={} listId={} isMember={}", userId, listId, isMember);
        if (isMember) {
            client.joinRoom("list:" + listId);
        }
    }

    @OnEvent("list:leave")
    public void onListLeave(SocketIOClient client, String listId) {
        if (listId == null || listId.isBlank()) return;
        client.leaveRoom("list:" + listId);
    }
}