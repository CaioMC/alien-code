package dev.aliencode.core.mission.domain.model;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.aliencode.core.mission.domain.exception.DeliveryConflictException;
import dev.aliencode.core.toca.domain.model.ChangedFile;
import dev.aliencode.core.toca.domain.model.WorkspaceChanges;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryTest {

    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");

    private final WorkspaceChanges changes = new WorkspaceChanges(
            "b45e000",
            "From c0ffee0 ...",
            List.of(new ChangedFile("calc.py", 1, 1), new ChangedFile("teste.py", 4, 0))
    );

    @Test
    void nascePendenteNaBranchDaMissao() {
        Delivery delivery = this.pending();

        assertThat(delivery.status()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(delivery.branch()).isEqualTo("alien/m-0000002a");
        assertThat(delivery.additions()).isEqualTo(5);
        assertThat(delivery.deletions()).isEqualTo(1);
    }

    @Test
    void aplicadaGuardaOCommitEDepoisNaoMudaMais() {
        Delivery applied = this.pending().applied("c0ffee0", NOW.plusSeconds(5));

        assertThat(applied.status()).isEqualTo(DeliveryStatus.APPLIED);
        assertThat(applied.headCommit()).isEqualTo("c0ffee0");
        assertThat(applied.resolvedAt()).isEqualTo(NOW.plusSeconds(5));
        assertThatThrownBy(() -> applied.rejected(NOW)).isInstanceOf(DeliveryConflictException.class);
    }

    @Test
    void mudancasVaziasNaoViramEntrega() {
        WorkspaceChanges empty = new WorkspaceChanges("b45e000", "", List.of());

        assertThat(empty.isEmpty()).isTrue();
        assertThatThrownBy(() -> Delivery.pending(
                MissionId.newId(),
                "calc",
                Path.of("/tmp/calc"),
                empty,
                NOW
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private Delivery pending() {
        return Delivery.pending(
                new MissionId("m-0000002a"),
                "calc",
                Path.of("/home/dev/calc"),
                this.changes,
                NOW
        );
    }
}
