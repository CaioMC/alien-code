package dev.aliencode.adapters.mission.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import dev.aliencode.core.mission.domain.model.AgentModel;
import dev.aliencode.core.mission.domain.model.Mission;
import dev.aliencode.core.mission.domain.model.MissionId;
import dev.aliencode.core.mission.domain.model.MissionStatus;
import dev.aliencode.core.mission.port.repository.MissionRepository;
import dev.aliencode.core.toca.domain.model.TocaId;

import static java.util.Objects.isNull;

@Repository
public class SqliteMissionRepository implements MissionRepository {

    private final JdbcClient jdbc;
    private final SeedJsonMapper seeds;

    public SqliteMissionRepository(JdbcClient jdbc, SeedJsonMapper seeds) {
        this.jdbc = jdbc;
        this.seeds = seeds;
    }

    @Override
    public void save(Mission mission) {
        this.jdbc.sql("""
                        INSERT INTO mission (id, title, prompt, seed, model, status, toca_id, session_id, created_at, finished_at, failure_reason)
                        VALUES (:id, :title, :prompt, :seed, :model, :status, :tocaId, :sessionId, :createdAt, :finishedAt, :failureReason)
                        ON CONFLICT (id) DO UPDATE SET
                            status = excluded.status,
                            toca_id = excluded.toca_id,
                            session_id = excluded.session_id,
                            finished_at = excluded.finished_at,
                            failure_reason = excluded.failure_reason
                        """)
                .param("id", mission.id().value())
                .param("title", mission.title())
                .param("prompt", mission.prompt())
                .param("seed", this.seeds.toJson(mission.seed()))
                .param("model", mission.model().toString())
                .param("status", mission.status().name())
                .param("tocaId", mission.hasToca() ? mission.tocaId().value() : null)
                .param("sessionId", mission.sessionId())
                .param("createdAt", mission.createdAt().toString())
                .param("finishedAt", isNull(mission.finishedAt()) ? null : mission.finishedAt().toString())
                .param("failureReason", mission.failureReason())
                .update();
    }

    @Override
    public Optional<Mission> findById(MissionId id) {
        return this.jdbc.sql("SELECT * FROM mission WHERE id = :id")
                .param("id", id.value())
                .query(this::toMission)
                .optional();
    }

    @Override
    public List<Mission> findAll() {
        return this.jdbc.sql("SELECT * FROM mission ORDER BY created_at DESC")
                .query(this::toMission)
                .list();
    }

    private Mission toMission(
            ResultSet row,
            int rowNumber
    ) throws SQLException {
        String tocaId = row.getString("toca_id");
        String finishedAt = row.getString("finished_at");

        return new Mission(
                new MissionId(row.getString("id")),
                row.getString("title"),
                row.getString("prompt"),
                this.seeds.fromJson(row.getString("seed")),
                AgentModel.parse(row.getString("model")),
                MissionStatus.valueOf(row.getString("status")),
                isNull(tocaId) ? null : new TocaId(tocaId),
                row.getString("session_id"),
                Instant.parse(row.getString("created_at")),
                isNull(finishedAt) ? null : Instant.parse(finishedAt),
                row.getString("failure_reason")
        );
    }
}
