package dev.aliencode.adapters.toca.git;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import dev.aliencode.core.toca.domain.model.RepositorySeed;
import dev.aliencode.core.toca.port.workspace.WorkspaceSnapshotPort;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * Snapshot com {@code git clone --local --no-hardlinks}: cópia independente do repositório,
 * no commit pedido, sem o remote apontando para a máquina do dev.
 * Alterações não commitadas do repositório original não entram (ficam para uma próxima versão).
 */
@Component
public class GitCloneSnapshotAdapter implements WorkspaceSnapshotPort {

    private static final Logger log = LoggerFactory.getLogger(GitCloneSnapshotAdapter.class);
    private static final long GIT_TIMEOUT_SECONDS = 120;

    @Override
    public Path snapshot(RepositorySeed seed) {
        Path base = createTempDirectory();
        Path target = base.resolve(seed.name());
        try {
            git(null, "clone", "--quiet", "--local", "--no-hardlinks", seed.path().toString(), target.toString());
            if (seed.hasRef()) {
                git(target, "checkout", "--quiet", seed.ref());
            }
            git(target, "remote", "remove", "origin");
            return target;
        } catch (RuntimeException e) {
            deleteRecursively(base);
            throw e;
        }
    }

    @Override
    public void discard(Path snapshot) {
        deleteRecursively(snapshot.getParent());
    }

    private static void git(Path workdir, String... args) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().put("GIT_TERMINAL_PROMPT", "0");
        if (nonNull(workdir)) {
            builder.directory(workdir.toFile());
        }
        try {
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            if (!process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("git " + args[0] + " excedeu " + GIT_TIMEOUT_SECONDS + "s");
            }
            if (process.exitValue() != 0) {
                throw new IllegalArgumentException("git " + args[0] + " falhou: " + output);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível executar o git", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrompido executando git", e);
        }
    }

    private static Path createTempDirectory() {
        try {
            return Files.createTempDirectory("alien-snapshot-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void deleteRecursively(Path dir) {
        if (isNull(dir) || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    log.warn("Não foi possível apagar {}", path);
                }
            });
        } catch (IOException e) {
            log.warn("Não foi possível limpar {}", dir, e);
        }
    }
}
