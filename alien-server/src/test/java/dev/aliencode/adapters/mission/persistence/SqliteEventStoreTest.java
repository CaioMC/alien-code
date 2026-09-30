package dev.aliencode.adapters.mission.persistence;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.aliencode.core.mission.domain.model.AlienEvent;
import dev.aliencode.core.mission.domain.model.EventSource;
import dev.aliencode.core.mission.domain.model.EventType;
import dev.aliencode.core.mission.domain.model.MissionId;
import dev.aliencode.core.mission.domain.model.NewEvent;

import static org.assertj.core.api.Assertions.assertThat;

class SqliteEventStoreTest {

    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");

    @TempDir
    Path dir;

    @Test
    void seqCrescePorMissaoEFindAfterDevolveEmOrdem() {
        SqliteEventStore store = this.store(this.dir.resolve("alien.db"));
        MissionId a = MissionId.newId();
        MissionId b = MissionId.newId();

        store.append(a, this.delta("um"));
        store.append(b, this.delta("outra missão"));
        store.append(a, this.delta("dois"));
        store.append(a, this.delta("três"));

        assertThat(store.findAfter(a, 0)).extracting(AlienEvent::seq).containsExactly(1L, 2L, 3L);
        assertThat(store.findAfter(a, 1)).extracting(e -> e.payload().get("text")).containsExactly("dois", "três");
        assertThat(store.findAfter(b, 0)).extracting(AlienEvent::seq).containsExactly(1L);
    }

    @Test
    void gravaOEnvelopeCompletoESobreviveAReabrirOBanco() {
        Path file = this.dir.resolve("alien.db");
        MissionId id = MissionId.newId();

        NewEvent tool = new NewEvent(
                EventType.TOOL_COMPLETED,
                "t1.tool.call_1",
                "t1",
                EventSource.OPENCODE,
                Map.of("tool", "bash", "exitCode", 1, "output", "Tests run: 12, Failures: 1", "input", Map.of("command", "mvn test"))
        );

        this.store(file).append(id, tool);

        AlienEvent stored = this.store(file).findAfter(id, 0).getFirst();

        assertThat(stored.seq()).isEqualTo(1);
        assertThat(stored.ts()).isEqualTo(NOW);
        assertThat(stored.type()).isEqualTo(EventType.TOOL_COMPLETED);
        assertThat(stored.stepId()).isEqualTo("t1.tool.call_1");
        assertThat(stored.parentStepId()).isEqualTo("t1");
        assertThat(stored.source()).isEqualTo(EventSource.OPENCODE);
        assertThat(stored.payload()).containsEntry("exitCode", 1).containsEntry("input", Map.of("command", "mvn test"));
    }

    @Test
    void semEventosDevolveListaVazia() {
        assertThat(this.store(this.dir.resolve("alien.db")).findAfter(MissionId.newId(), 0)).isEqualTo(List.of());
    }

    private SqliteEventStore store(Path file) {
        SqliteTestDatabase database = new SqliteTestDatabase(file);

        return new SqliteEventStore(
                database.jdbc,
                database.transactions,
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private NewEvent delta(String text) {
        return new NewEvent(EventType.ASSISTANT_DELTA, "t1.text.p1", "t1", EventSource.OPENCODE, Map.of("text", text));
    }
}
