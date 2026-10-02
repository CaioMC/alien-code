package dev.aliencode.adapters.mission.persistence;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.aliencode.core.mission.domain.model.Delivery;
import dev.aliencode.core.mission.domain.model.DeliveryStatus;
import dev.aliencode.core.mission.domain.model.MissionId;
import dev.aliencode.core.toca.domain.model.ChangedFile;
import dev.aliencode.core.toca.domain.model.WorkspaceChanges;

import static org.assertj.core.api.Assertions.assertThat;

class SqliteDeliveryRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");

    @TempDir
    Path dir;

    private SqliteDeliveryRepository repository;

    @BeforeEach
    void setUp() {
        SqliteTestDatabase database = new SqliteTestDatabase(this.dir.resolve("alien.db"));

        this.repository = new SqliteDeliveryRepository(database.jdbc, new ObjectMapper());
    }

    @Test
    void gravaAEntregaPendenteEDepoisAplicada() {
        MissionId id = MissionId.newId();
        WorkspaceChanges changes = new WorkspaceChanges(
                "b45e000",
                "From c0ffee0\n+    # correção\n",
                List.of(new ChangedFile("calc.py", 2, 1), new ChangedFile("logo.png", 0, 0))
        );
        Delivery pending = Delivery.pending(
                id,
                "calc",
                Path.of("/home/dev/calc"),
                changes,
                NOW
        );

        this.repository.save(pending);

        assertThat(this.repository.findByMissionId(id)).contains(pending);

        Delivery applied = pending.applied("c0ffee0", NOW.plusSeconds(30));

        this.repository.save(applied);

        Delivery found = this.repository.findByMissionId(id).orElseThrow();

        assertThat(found.status()).isEqualTo(DeliveryStatus.APPLIED);
        assertThat(found.headCommit()).isEqualTo("c0ffee0");
        assertThat(found.resolvedAt()).isEqualTo(NOW.plusSeconds(30));
        assertThat(found.patch()).contains("# correção");
        assertThat(found.files()).containsExactlyElementsOf(changes.files());
    }

    @Test
    void missaoSemEntregaNaoAcha() {
        assertThat(this.repository.findByMissionId(MissionId.newId())).isEmpty();
    }
}
