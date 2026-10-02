package dev.aliencode.core.mission.domain.model;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import dev.aliencode.core.mission.domain.exception.DeliveryConflictException;
import dev.aliencode.core.toca.domain.model.ChangedFile;
import dev.aliencode.core.toca.domain.model.WorkspaceChanges;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * A entrega de uma missão: o patch que o agente produziu na Toca, esperando o dev decidir.
 * Aplicada, vira commits numa branch nova do repositório original; o resto do repositório
 * (branch atual, arquivos abertos) não é tocado.
 *
 * @param repository     nome do repositório dentro da Toca
 * @param repositoryPath repositório original na máquina do dev
 * @param baseCommit     commit em que a Toca foi semeada; a branch nasce dele
 * @param branch         branch criada ao aplicar ({@code alien/<missão>})
 * @param headCommit     último commit da branch, depois de aplicada
 */
public record Delivery(
        MissionId missionId,
        String repository,
        Path repositoryPath,
        String baseCommit,
        String patch,
        List<ChangedFile> files,
        DeliveryStatus status,
        String branch,
        String headCommit,
        Instant createdAt,
        Instant resolvedAt
) {

    public static final String BRANCH_PREFIX = "alien/";

    public Delivery {
        if (isNull(missionId) || isNull(repositoryPath) || isBlank(baseCommit) || isBlank(patch) || isNull(status) || isNull(createdAt)) {
            throw new IllegalArgumentException("Entrega incompleta");
        }

        files = isNull(files) ? List.of() : List.copyOf(files);
    }

    public static Delivery pending(
            MissionId missionId,
            String repository,
            Path repositoryPath,
            WorkspaceChanges changes,
            Instant now
    ) {
        return new Delivery(
                missionId,
                repository,
                repositoryPath,
                changes.baseCommit(),
                changes.patch(),
                changes.files(),
                DeliveryStatus.PENDING,
                BRANCH_PREFIX + missionId.value(),
                null,
                now,
                null
        );
    }

    public Delivery applied(
            String headCommit,
            Instant now
    ) {
        this.requirePending();

        return this.with(
                DeliveryStatus.APPLIED,
                headCommit,
                now
        );
    }

    public Delivery rejected(Instant now) {
        this.requirePending();

        return this.with(
                DeliveryStatus.REJECTED,
                null,
                now
        );
    }

    public boolean isPending() {
        return this.status == DeliveryStatus.PENDING;
    }

    public int additions() {
        return this.files.stream().mapToInt(ChangedFile::additions).sum();
    }

    public int deletions() {
        return this.files.stream().mapToInt(ChangedFile::deletions).sum();
    }

    private Delivery with(
            DeliveryStatus status,
            String headCommit,
            Instant resolvedAt
    ) {
        return new Delivery(
                this.missionId,
                this.repository,
                this.repositoryPath,
                this.baseCommit,
                this.patch,
                this.files,
                status,
                this.branch,
                headCommit,
                this.createdAt,
                resolvedAt
        );
    }

    private void requirePending() {
        if (!this.isPending()) {
            throw new DeliveryConflictException("A entrega da missão " + this.missionId + " já foi resolvida (" + this.status + ")");
        }
    }
}
