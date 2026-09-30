package dev.aliencode.adapters.mission.persistence;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.aliencode.core.mission.domain.model.AgentModel;
import dev.aliencode.core.mission.domain.model.Mission;
import dev.aliencode.core.mission.domain.model.MissionId;
import dev.aliencode.core.mission.domain.model.MissionStatus;
import dev.aliencode.core.toca.domain.model.RepositorySeed;
import dev.aliencode.core.toca.domain.model.Seed;
import dev.aliencode.core.toca.domain.model.TocaId;

import static org.assertj.core.api.Assertions.assertThat;

class SqliteMissionRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");
    private static final AgentModel MODEL = AgentModel.parse("ollama/qwen3:8b");

    @TempDir
    Path dir;

    private SqliteMissionRepository repository;

    @BeforeEach
    void setUp() {
        SqliteTestDatabase database = new SqliteTestDatabase(this.dir.resolve("alien.db"));

        this.repository = new SqliteMissionRepository(database.jdbc, new SeedJsonMapper(new ObjectMapper()));
    }

    @Test
    void gravaEAtualizaOEstadoDaMissao() {
        Seed seed = new Seed.ExistingRepositories(List.of(new RepositorySeed("calc", Path.of("/home/dev/calc"), "main")));
        Mission mission = Mission.create(
                MissionId.newId(),
                "Soma",
                "Corrija a soma",
                seed,
                MODEL,
                NOW
        );
        TocaId toca = TocaId.newId();

        this.repository.save(mission);

        Mission failed = mission.provisioning().withToca(toca).executing(toca, "ses_1").failed("model not found", NOW.plusSeconds(5));

        this.repository.save(failed);

        Mission stored = this.repository.findById(mission.id()).orElseThrow();

        assertThat(stored).isEqualTo(failed);
        assertThat(stored.status()).isEqualTo(MissionStatus.FAILED);
        assertThat(stored.seed()).isEqualTo(seed);
    }

    @Test
    void guardaProjetoNovoEListaDoMaisRecenteParaOMaisAntigo() {
        Mission older = Mission.create(
                MissionId.newId(),
                null,
                "Primeira",
                new Seed.NewProject("novo"),
                MODEL,
                NOW
        );
        Mission newer = Mission.create(
                MissionId.newId(),
                null,
                "Segunda",
                new Seed.NewProject("outro"),
                MODEL,
                NOW.plusSeconds(60)
        );

        this.repository.save(older);
        this.repository.save(newer);

        assertThat(this.repository.findAll()).containsExactly(newer, older);
        assertThat(this.repository.findById(older.id()).orElseThrow().seed()).isEqualTo(new Seed.NewProject("novo"));
        assertThat(this.repository.findById(MissionId.newId())).isEmpty();
    }
}
