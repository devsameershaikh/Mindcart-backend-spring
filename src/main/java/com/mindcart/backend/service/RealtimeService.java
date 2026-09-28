package com.mindcart.backend.service;

import com.corundumstudio.socketio.SocketIOServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

@Service
public class RealtimeService {

    private static final Logger log = LoggerFactory.getLogger(RealtimeService.class);

    @Nullable
    private final SocketIOServer server;

    // required = false -- ItemService/ListService depend on RealtimeService
    // unconditionally, so this must still construct even when the
    // SocketIOServer bean doesn't exist (SOCKETIO_ENABLED=false).
    private final ObjectMapper objectMapper;

    public RealtimeService(@Autowired(required = false) SocketIOServer server, ObjectMapper objectMapper) {
        this.server = server;
        this.objectMapper = objectMapper;
    }

    public void emitToList(String listId, String event, Object payload) {
        emit("list:" + listId, event, payload);
    }

    public void emitToUser(String userId, String event, Object payload) {
        emit("user:" + userId, event, payload);
    }

    private void emit(String room, String event, Object payload) {
        if (server == null) return;
        try {
            int clientCount = server.getRoomOperations(room).getClients().size();
            log.info("emit '{}' to room '{}' -> {} client(s) connected", event, room, clientCount);
            // netty-socketio uses its OWN ObjectMapper without the java.time
            // module, so DTOs containing Instant fail to serialize ("Can't write
            // value ... java.time.Instant not supported"). Convert with Spring's
            // ObjectMapper first (ISO-8601 strings, same as the REST responses)
            // so the socket only ever sees plain maps/lists/strings.
            Object plain = objectMapper.convertValue(payload, Object.class);
            server.getRoomOperations(room).sendEvent(event, plain);
        } catch (Exception e) {
            log.warn("Failed to emit '{}' to room '{}': {}", event, room, e.toString());
        }
    }
}