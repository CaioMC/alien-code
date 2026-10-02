package dev.aliencode.adapters.mission.git;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.aliencode.core.mission.domain.exception.DeliveryConflictException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Com git de verdade: o repositório do dev, e uma cópia fazendo o papel da Toca. */
class GitWorktreeDeliveryAdapterTest {

    private static final String BRANCH = "alien/m-0000002a";

    @TempDir
    Path dir;

    private final GitWorktreeDeliveryAdapter adapter = new GitWorktreeDeliveryAdapter();

    private Path repository;
    private String baseCommit;
    private String patch;

    @BeforeEach
    void setUp() throws Exception {
        this.repository = Files.createDirectories(this.dir.resolve("calc"));

        Files.writeString(this.repository.resolve("calc.py"), "def soma(a, b):\n    return a - b\n");
        git(this.repository, "init", "-q", "-b", "main");
        git(this.repository, "add", ".");
        commit(this.repository, "soma com bug");

        this.baseCommit = git(this.repository, "rev-parse", "HEAD");

        // a "Toca": clona, corrige (com acento, para provar o UTF-8) e gera o patch
        Path toca = this.dir.resolve("toca");

        git(this.dir, "clone", "-q", this.repository.toString(), toca.toString());
        Files.writeString(toca.resolve("calc.py"), "def soma(a, b):\n    # correção\n    return a + b\n");
        git(toca, "add", ".");
        commit(toca, "Corrija a soma");

        this.patch = git(toca, "format-patch", "--stdout", "--binary", this.baseCommit + "..HEAD") + "\n";
    }

    @Test
    void criaABranchComOsCommitsSemTocarNaBranchAtual() throws Exception {
        Files.writeString(this.repository.resolve("rascunho.txt"), "trabalho do dev, não commitado\n");

        String head = this.adapter.applyToBranch(
                this.repository,
                this.baseCommit,
                BRANCH,
                this.patch
        );

        assertThat(git(this.repository, "rev-parse", BRANCH)).isEqualTo(head);
        assertThat(git(this.repository, "show", BRANCH + ":calc.py")).contains("# correção", "return a + b");
        assertThat(git(this.repository, "log", "-1", "--format=%s", BRANCH)).isEqualTo("Corrija a soma");

        // o dev continua onde estava: mesma branch, mesmo arquivo, rascunho intacto
        assertThat(git(this.repository, "branch", "--show-current")).isEqualTo("main");
        assertThat(Files.readString(this.repository.resolve("calc.py"))).contains("return a - b");
        assertThat(Files.readString(this.repository.resolve("rascunho.txt"))).startsWith("trabalho do dev");
        assertThat(git(this.repository, "worktree", "list")).doesNotContain("alien-delivery-");
    }

    @Test
    void branchQueJaExisteEhRecusada() throws Exception {
        git(this.repository, "branch", BRANCH);

        assertThatThrownBy(() -> this.apply(this.baseCommit, this.patch))
                .isInstanceOf(DeliveryConflictException.class)
                .hasMessageContaining("já existe");
    }

    @Test
    void commitBaseAusenteEhRecusado() {
        assertThatThrownBy(() -> this.apply("0123456789abcdef0123456789abcdef01234567", this.patch))
                .isInstanceOf(DeliveryConflictException.class)
                .hasMessageContaining("commit base");
    }

    @Test
    void patchQueNaoAplicaNaoDeixaBranchNemWorktree() throws Exception {
        String broken = this.patch.replace("return a - b", "return x * y");

        assertThatThrownBy(() -> this.apply(this.baseCommit, broken))
                .isInstanceOf(DeliveryConflictException.class)
                .hasMessageContaining("não aplicou");

        assertThat(git(this.repository, "branch", "--list", BRANCH)).isEmpty();
        assertThat(git(this.repository, "worktree", "list").lines()).hasSize(1);
    }

    private String apply(
            String baseCommit,
            String patch
    ) {
        return this.adapter.applyToBranch(
                this.repository,
                baseCommit,
                BRANCH,
                patch
        );
    }

    private static void commit(
            Path repo,
            String message
    ) throws IOException, InterruptedException {
        git(repo, "-c", "user.name=dev", "-c", "user.email=dev@local", "commit", "-q", "-m", message);
    }

    private static String git(
            Path workdir,
            String... args
    ) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of("git", "-C", workdir.toString()));

        command.addAll(List.of(args));

        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();

        assertThat(process.waitFor()).as(output).isZero();

        return output;
    }
}
