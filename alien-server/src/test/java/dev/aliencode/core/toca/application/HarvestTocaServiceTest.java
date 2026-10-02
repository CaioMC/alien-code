package dev.aliencode.core.toca.application;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.aliencode.core.toca.application.TocaTestDoubles.FakeSandbox;
import dev.aliencode.core.toca.application.TocaTestDoubles.InMemoryTocas;
import dev.aliencode.core.toca.domain.model.ChangedFile;
import dev.aliencode.core.toca.domain.model.Toca;
import dev.aliencode.core.toca.domain.model.TocaEndpoint;
import dev.aliencode.core.toca.domain.model.TocaId;
import dev.aliencode.core.toca.domain.model.WorkspaceChanges;
import dev.aliencode.core.toca.port.sandbox.ExecResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HarvestTocaServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");
    private static final String DIR = "/workspace/calc";
    private static final String PATCH = "From c0ffee0 Mon Sep 17 00:00:00 2001\nSubject: [PATCH] Soma\n\n-    return a - b\n+    return a + b\n";

    private final FakeSandbox sandbox = new FakeSandbox();
    private final InMemoryTocas tocas = new InMemoryTocas();
    private final HarvestTocaService service = new HarvestTocaService(this.sandbox, this.tocas);

    private Toca toca;
    private String status = " M calc.py\n";

    @BeforeEach
    void setUp() {
        this.toca = Toca.provisioning(
                TocaId.newId(),
                "m-42",
                NOW,
                NOW.plusSeconds(3600)
        )
                .withContainer("c-1", new TocaEndpoint(URI.create("http://127.0.0.1:40001"), "opencode", "s3nha"))
                .ready(List.of(DIR));

        this.tocas.save(this.toca);
        this.sandbox.onExec = this::git;
    }

    @Test
    void commitaOPendenteEDevolveOPatchDesdeABase() {
        WorkspaceChanges changes = this.service.harvest(this.toca.id(), DIR, "Corrija a soma");

        assertThat(changes.baseCommit()).isEqualTo("b45e000");
        assertThat(changes.patch()).isEqualTo(PATCH);
        assertThat(changes.files()).containsExactly(new ChangedFile("calc.py", 1, 1), new ChangedFile("logo.png", 0, 0));
        assertThat(this.sandbox.executed).contains(
                List.of("git", "-C", DIR, "add", "--all"),
                List.of("git", "-C", DIR, "commit", "--quiet", "--no-verify", "--message", "Corrija a soma"),
                List.of("git", "-C", DIR, "format-patch", "--stdout", "--binary", "alien-base..HEAD")
        );
    }

    @Test
    void semNadaPendenteNaoCriaCommit() {
        this.status = "";

        this.service.harvest(this.toca.id(), DIR, "Corrija a soma");

        assertThat(this.sandbox.executed).noneMatch(command -> command.contains("commit"));
    }

    @Test
    void gitComErroFalhaAColheita() {
        this.sandbox.onExec = command -> new ExecResult(128, "", "fatal: bad revision 'alien-base'");

        assertThatThrownBy(() -> this.service.harvest(this.toca.id(), DIR, "Corrija a soma")).hasMessageContaining("bad revision");
    }

    @Test
    void numstatDeArquivoBinarioViraZero() {
        assertThat(HarvestTocaService.parseNumstat("3\t1\tsrc/A.java\n-\t-\timg/logo.png\n"))
                .containsExactly(new ChangedFile("src/A.java", 3, 1), new ChangedFile("img/logo.png", 0, 0));
    }

    private ExecResult git(List<String> command) {
        String output = switch (command.get(3)) {
            case "status" -> this.status;
            case "rev-parse" -> "b45e000\n";
            case "format-patch" -> PATCH;
            case "diff" -> "1\t1\tcalc.py\n-\t-\tlogo.png\n";
            default -> "";
        };

        return new ExecResult(0, output, "");
    }
}
