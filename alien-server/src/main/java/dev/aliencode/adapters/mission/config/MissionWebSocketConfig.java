package dev.aliencode.adapters.mission.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import dev.aliencode.adapters.mission.websocket.handler.MissionWebSocketHandler;

@Configuration
@EnableWebSocket
public class MissionWebSocketConfig implements WebSocketConfigurer {

    private final MissionWebSocketHandler handler;

    public MissionWebSocketConfig(MissionWebSocketHandler handler) {
        this.handler = handler;
    }

    /** O servidor só escuta em 127.0.0.1; a origem é liberada para o Vite em desenvolvimento. */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(this.handler, "/ws/missions/*").setAllowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*");
    }
}
