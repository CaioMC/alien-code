package dev.aliencode.adapters.mission.persistence;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.aliencode.core.mission.domain.model.Delivery;
import dev.aliencode.core.mission.domain.model.DeliveryStatus;
import dev.aliencode.core.mission.domain.model.MissionId;
import dev.aliencode.core.mission.port.repository.DeliveryRepository;
import dev.aliencode.core.toca.domain.model.ChangedFile;

import static java.util.Objects.isNull;

@Repository
public class SqliteDeliveryRepository implements DeliveryRepository {

    private static final TypeReference<List<ChangedFile>> FILES = new TypeReference<>() {
    };

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public SqliteDeliveryRepository(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void save(Delivery delivery) {
        this.jdbc.sql("""
                        INSERT INTO delivery (mission_id, repository, repository_path, base_commit, patch, files, status, branch, head_commit, created_at, resolved_at)
                        VALUES (:missionId, :repository, :repositoryPath, :baseCommit, :patch, :files, :status, :branch, :headCommit, :createdAt, :resolvedAt)
                        ON CONFLICT (mission_id) DO UPDATE SET
                            status = excluded.status,
                            head_commit = excluded.head_commit,
                            resolved_at = excluded.resolved_at
                        """)
                .param("missionId", delivery.missionId().value())
                .param("repository", delivery.repository())
                .param("repositoryPath", delivery.repositoryPath().toString())
                .param("baseCommit", delivery.baseCommit())
                .param("patch", delivery.patch())
                .param("files", this.toJson(delivery.files()))
                .param("status", delivery.status().name())
                .param("branch", delivery.branch())
                .param("headCommit", delivery.headCommit())
                .param("createdAt", delivery.createdAt().toString())
                .param("resolvedAt", isNull(delivery.resolvedAt()) ? null : delivery.resolvedAt().toString())
                .update();
    }

    @Override
    public Optional<Delivery> findByMissionId(MissionId id) {
        return this.jdbc.sql("SELECT * FROM delivery WHERE mission_id = :id")
                .param("id", id.value())
                .query(this::toDelivery)
                .optional();
    }

    private Delivery toDelivery(
            ResultSet row,
            int rowNumber
    ) throws SQLException {
        String resolvedAt = row.getString("resolved_at");

        return new Delivery(
                new MissionId(row.getString("mission_id")),
                row.getString("repository"),
                Path.of(row.getString("repository_path")),
                row.getString("base_commit"),
                row.getString("patch"),
                this.fromJson(row.getString("files")),
                DeliveryStatus.valueOf(row.getString("status")),
                row.getString("branch"),
                row.getString("head_commit"),
                Instant.parse(row.getString("created_at")),
                isNull(resolvedAt) ? null : Instant.parse(resolvedAt)
        );
    }

    private String toJson(List<ChangedFile> files) {
        try {
            return this.json.writeValueAsString(files);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Não foi possível gravar os arquivos da entrega", e);
        }
    }

    private List<ChangedFile> fromJson(String files) {
        try {
            return this.json.readValue(files, FILES);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Arquivos da entrega ilegíveis no banco", e);
        }
    }
}
