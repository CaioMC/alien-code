package dev.aliencode.adapters.mission.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.aliencode.core.mission.domain.model.AlienEvent;
import dev.aliencode.core.mission.domain.model.EventSource;
import dev.aliencode.core.mission.domain.model.EventType;
import dev.aliencode.core.mission.domain.model.MissionId;
import dev.aliencode.core.mission.domain.model.NewEvent;
import dev.aliencode.core.mission.port.event.EventStorePort;

/**
 * Event Store append-only em SQLite. O {@code seq} é o próximo da missão, calculado e gravado
 * na mesma transação; {@code append} é sincronizado porque o SQLite aceita um escritor por vez.
 */
@Repository
public class SqliteEventStore implements EventStorePort {

    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {
    };

    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;
    private final ObjectMapper json;
    private final Clock clock;

    public SqliteEventStore(
            JdbcClient jdbc,
            TransactionTemplate transactions,
            ObjectMapper json,
            Clock clock
    ) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.json = json;
        this.clock = clock;
    }

    @Override
    public synchronized AlienEvent append(
            MissionId missionId,
            NewEvent event
    ) {
        return this.transactions.execute(status -> {
            long seq = this.jdbc.sql("SELECT COALESCE(MAX(seq), 0) + 1 FROM mission_event WHERE mission_id = :missionId")
                    .param("missionId", missionId.value())
                    .query(Long.class)
                    .single();

            AlienEvent stored = new AlienEvent(
                    missionId,
                    seq,
                    this.clock.instant(),
                    event.type(),
                    event.stepId(),
                    event.parentStepId(),
                    event.source(),
                    event.payload()
            );

            this.jdbc.sql("""
                            INSERT INTO mission_event (mission_id, seq, ts, type, step_id, parent_step_id, source, payload)
                            VALUES (:missionId, :seq, :ts, :type, :stepId, :parentStepId, :source, :payload)
                            """)
                    .param("missionId", missionId.value())
                    .param("seq", seq)
                    .param("ts", stored.ts().toString())
                    .param("type", stored.type().wireName())
                    .param("stepId", stored.stepId())
                    .param("parentStepId", stored.parentStepId())
                    .param("source", stored.source().name())
                    .param("payload", this.toJson(stored.payload()))
                    .update();

            return stored;
        });
    }

    @Override
    public List<AlienEvent> findAfter(
            MissionId missionId,
            long lastSeq
    ) {
        return this.jdbc.sql("SELECT * FROM mission_event WHERE mission_id = :missionId AND seq > :lastSeq ORDER BY seq")
                .param("missionId", missionId.value())
                .param("lastSeq", lastSeq)
                .query(this::toEvent)
                .list();
    }

    private AlienEvent toEvent(
            ResultSet row,
            int rowNumber
    ) throws SQLException {
        return new AlienEvent(
                new MissionId(row.getString("mission_id")),
                row.getLong("seq"),
                Instant.parse(row.getString("ts")),
                EventType.fromWireName(row.getString("type")),
                row.getString("step_id"),
                row.getString("parent_step_id"),
                EventSource.valueOf(row.getString("source")),
                this.fromJson(row.getString("payload"))
        );
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return this.json.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Payload de evento não serializável", e);
        }
    }

    private Map<String, Object> fromJson(String payload) {
        try {
            return this.json.readValue(payload, PAYLOAD_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Payload de evento gravado inválido", e);
        }
    }
}
