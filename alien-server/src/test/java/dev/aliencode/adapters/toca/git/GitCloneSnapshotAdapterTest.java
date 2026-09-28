package dev.aliencode.adapters.toca.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.aliencode.core.toca.domain.RepositorySeed;

class GitCloneSnapshotAdapterTest {

    @TempDir
    Path repo;

    private final GitCloneSnapshotAdapter adapter = new GitCloneSnapshotAdapter();

    @BeforeEach
    void createRepository() throws Exception {
        git("init", "-q", "-b", "main");
        Files.writeString(this.repo.resolve("README.md"), "v1");
        git("add", ".");
        git("commit", "-q", "-m", "v1");
        git("tag", "v1");
        Files.writeString(this.repo.resolve("README.md"), "v2");
        git("commit", "-q", "-am", "v2");
        Files.writeString(this.repo.resolve("README.md"), "sujo, não commitado");
    }

    @Test
    void copiaOHeadSemRemoteESemMexerNoOriginal() throws Exception {
        Path snapshot = this.adapter.snapshot(new RepositorySeed("api", this.repo, null));
        try {
            assertThat(snapshot.getFileName().toString()).isEqualTo("api");
            assertThat(Files.readString(snapshot.resolve("README.md"))).isEqualTo("v2");
            assertThat(run(snapshot, "git", "remote")).isBlank();
            assertThat(Files.readString(this.repo.resolve("README.md"))).isEqualTo("sujo, não commitado");
        } finally {
            this.adapter.discard(snapshot);
        }
        assertThat(snapshot.getParent()).doesNotExist();
    }

    @Test
    void fazCheckoutDaRefPedida() throws Exception {
        Path snapshot = this.adapter.snapshot(new RepositorySeed("api", this.repo, "v1"));
        try {
            assertThat(Files.readString(snapshot.resolve("README.md"))).isEqualTo("v1");
        } finally {
            this.adapter.discard(snapshot);
        }
    }

    @Test
    void pastaQueNaoEGitFalhaComMensagemClara(@TempDir Path plain) {
        assertThatThrownBy(() -> this.adapter.snapshot(new RepositorySeed("x", plain, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("git clone falhou");
    }

    private void git(String... args) throws Exception {
        List<String> command = new ArrayList<>(List.of("git", "-c", "user.name=t", "-c", "user.email=t@t"));
        command.addAll(List.of(args));
        run(this.repo, command.toArray(String[]::new));
    }

    private static String run(Path dir, String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor()).as(output).isZero();
        return output;
    }
}
