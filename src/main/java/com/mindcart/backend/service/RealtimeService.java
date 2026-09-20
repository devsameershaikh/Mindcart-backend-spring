package com.mindcart.backend.service;

import com.corundumstudio.socketio.SocketIOServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RealtimeService {

    private static final Logger log = LoggerFactory.getLogger(RealtimeService.class);

    private final SocketIOServer server;

    public RealtimeService(SocketIOServer server) {
        this.server = server;
    }

    public void emitToList(String listId, String event, Object payload) {
        emit("list:" + listId, event, payload);
    }

    public void emitToUser(String userId, String event, Object payload) {
        emit("user:" + userId, event, payload);
    }

    private void emit(String room, String event, Object payload) {
        try {
            server.getRoomOperations(room).sendEvent(event, payload);
        } catch (Exception e) {
            // A realtime notification failing to send must never fail the
            // HTTP request that triggered it -- the write to the database
            // already succeeded, so we log and move on instead of throwing.
            log.warn("Failed to emit '{}' to room '{}': {}", event, room, e.toString());
        }
    }
}
