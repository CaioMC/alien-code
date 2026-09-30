package dev.aliencode.adapters.mission.websocket.handler;

import java.io.IOException;
import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.aliencode.adapters.mission.websocket.mapper.AlienEventMessageMapper;
import dev.aliencode.adapters.mission.websocket.message.ClientCommand;
import dev.aliencode.core.mission.domain.exception.MissionNotFoundException;
import dev.aliencode.core.mission.domain.model.AlienEvent;
import dev.aliencode.core.mission.domain.model.MissionId;
import dev.aliencode.core.mission.usecase.MissionWatch;
import dev.aliencode.core.mission.usecase.StopMissionUseCase;
import dev.aliencode.core.mission.usecase.WatchMissionUseCase;

import static java.util.Objects.nonNull;

/**
 * {@code WS /ws/missions/{id}?lastSeq=N}: envia os eventos com seq &gt; N já gravados e depois os
 * novos, ao vivo. A missão continua no servidor se o navegador fechar; ao reconectar com o último
 * seq recebido, o cliente recebe só o que perdeu.
 */
@Component
public class MissionWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(MissionWebSocketHandler.class);

    private static final String WATCH = "watch";
    private static final String MISSION = "mission";
    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int BUFFER_LIMIT_BYTES = 4 * 1024 * 1024;

    private final WatchMissionUseCase watch;
    private final StopMissionUseCase stop;

    private final AlienEventMessageMapper mapper;
    private final ObjectMapper json;

    public MissionWebSocketHandler(
            WatchMissionUseCase watch,
            StopMissionUseCase stop,
            AlienEventMessageMapper mapper,
            ObjectMapper json
    ) {
        this.watch = watch;
        this.stop = stop;
        this.mapper = mapper;
        this.json = json;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        URI uri = session.getUri();
        String path = uri.getPath();
        MissionId id;
        long lastSeq;

        try {
            id = new MissionId(path.substring(path.lastIndexOf('/') + 1));
            lastSeq = this.lastSeq(uri);
        } catch (IllegalArgumentException e) {
            session.close(CloseStatus.BAD_DATA.withReason(e.getMessage()));
            return;
        }

        WebSocketSession concurrent = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_LIMIT_BYTES);

        try {
            MissionWatch subscription = this.watch.watch(id, lastSeq, event -> this.send(concurrent, event));

            session.getAttributes().put(WATCH, subscription);
            session.getAttributes().put(MISSION, id);
        } catch (MissionNotFoundException e) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason(e.getMessage()));
        }
    }

    @Override
    protected void handleTextMessage(
            WebSocketSession session,
            TextMessage message
    ) throws IOException {
        ClientCommand command = this.json.readValue(message.getPayload(), ClientCommand.class);
        MissionId id = (MissionId) session.getAttributes().get(MISSION);

        if (ClientCommand.STOP.equals(command.type()) && nonNull(id)) {
            this.stop.stop(id);
        } else {
            log.debug("Comando de WebSocket ignorado: {}", message.getPayload());
        }
    }

    @Override
    public void afterConnectionClosed(
            WebSocketSession session,
            CloseStatus status
    ) {
        if (session.getAttributes().get(WATCH) instanceof MissionWatch subscription) {
            subscription.close();
        }
    }

    private void send(
            WebSocketSession session,
            AlienEvent event
    ) {
        if (!session.isOpen()) {
            return;
        }

        try {
            session.sendMessage(new TextMessage(this.json.writeValueAsString(this.mapper.toMessage(event))));
        } catch (JsonProcessingException e) {
            log.warn("Evento {} da missão {} não serializável: {}", event.seq(), event.missionId(), e.getMessage());
        } catch (IOException e) {
            throw new IllegalStateException("Falha enviando evento pelo WebSocket", e);
        }
    }

    private long lastSeq(URI uri) {
        String value = UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst("lastSeq");

        try {
            return nonNull(value) ? Long.parseLong(value) : 0;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("lastSeq inválido: " + value);
        }
    }
}
