package dev.aliencode.adapters.mission.git;

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

import dev.aliencode.core.mission.domain.exception.DeliveryConflictException;
import dev.aliencode.core.mission.port.delivery.DeliveryPort;

/**
 * Aplica a entrega com o git do host, sem tocar no que o dev tem aberto:
 * <ol>
 *   <li>{@code git worktree add -b alien/<missão> <pasta temporária> <commit base>};</li>
 *   <li>{@code git am} do patch dentro dessa worktree;</li>
 *   <li>{@code git worktree remove}: sobra só a branch nova, com os commits.</li>
 * </ol>
 * Se o {@code git am} falhar, a worktree e a branch são removidas e o repositório fica como estava.
 */
@Component
public class GitWorktreeDeliveryAdapter implements DeliveryPort {

    private static final Logger log = LoggerFactory.getLogger(GitWorktreeDeliveryAdapter.class);
    private static final long GIT_TIMEOUT_SECONDS = 120;

    @Override
    public String applyToBranch(
            Path repository,
            String baseCommit,
            String branch,
            String patch
    ) {
        if (this.git(repository, "rev-parse", "--verify", "--quiet", "refs/heads/" + branch).succeeded()) {
            throw new DeliveryConflictException("A branch " + branch + " já existe em " + repository);
        }

        if (!this.git(repository, "cat-file", "-e", baseCommit + "^{commit}").succeeded()) {
            throw new DeliveryConflictException("O commit base " + baseCommit + " não existe em " + repository + " (o histórico foi reescrito?)");
        }

        Path temp = createTempDirectory();
        Path worktree = temp.resolve("worktree");
        Path patchFile = temp.resolve("entrega.patch");

        try {
            Files.writeString(patchFile, patch, StandardCharsets.UTF_8);

            this.require(this.git(repository, "worktree", "add", "--quiet", "-b", branch, worktree.toString(), baseCommit), "worktree add");

            GitResult applied = this.git(worktree, "am", "--quiet", patchFile.toString());

            if (!applied.succeeded()) {
                this.git(worktree, "am", "--abort");
                this.git(repository, "worktree", "remove", "--force", worktree.toString());
                this.git(repository, "branch", "-D", branch);

                throw new DeliveryConflictException("O patch não aplicou sobre " + baseCommit + ": " + applied.output());
            }

            String headCommit = this.require(this.git(worktree, "rev-parse", "HEAD"), "rev-parse").output();

            this.require(this.git(repository, "worktree", "remove", worktree.toString()), "worktree remove");
            log.info("Branch {} criada em {} ({})", branch, repository, headCommit);

            return headCommit;
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível gravar o patch da entrega", e);
        } finally {
            deleteRecursively(temp);
        }
    }

    private GitResult require(
            GitResult result,
            String step
    ) {
        if (!result.succeeded()) {
            throw new DeliveryConflictException("git " + step + " falhou: " + result.output());
        }

        return result;
    }

    private GitResult git(
            Path workdir,
            String... args
    ) {
        List<String> command = new ArrayList<>(List.of("git", "-C", workdir.toString()));

        command.addAll(List.of(args));

        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);

        builder.environment().put("GIT_TERMINAL_PROMPT", "0");

        try {
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();

            if (!process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("git " + args[0] + " excedeu " + GIT_TIMEOUT_SECONDS + "s");
            }

            return new GitResult(process.exitValue(), output);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível executar o git", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrompido executando git", e);
        }
    }

    private static Path createTempDirectory() {
        try {
            return Files.createTempDirectory("alien-delivery-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void deleteRecursively(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }

        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        } catch (IOException e) {
            log.warn("Não foi possível limpar {}", dir, e);
        }
    }

    private record GitResult(
            int exitCode,
            String output
    ) {

        boolean succeeded() {
            return this.exitCode == 0;
        }
    }
}
