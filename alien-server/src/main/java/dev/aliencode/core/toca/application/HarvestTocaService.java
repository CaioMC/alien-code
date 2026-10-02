package dev.aliencode.core.toca.application;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import dev.aliencode.core.toca.domain.exception.TocaNotFoundException;
import dev.aliencode.core.toca.domain.model.ChangedFile;
import dev.aliencode.core.toca.domain.model.Toca;
import dev.aliencode.core.toca.domain.model.TocaId;
import dev.aliencode.core.toca.domain.model.WorkspaceChanges;
import dev.aliencode.core.toca.port.repository.TocaRepository;
import dev.aliencode.core.toca.port.sandbox.ExecResult;
import dev.aliencode.core.toca.port.sandbox.SandboxPort;
import dev.aliencode.core.toca.usecase.HarvestTocaUseCase;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Colhe as alterações de um repositório da Toca com o próprio git, dentro do container:
 * {@code git commit} do que ficou pendente e {@code git format-patch} desde a tag {@code alien-base}.
 * Os commits que o agente fez por conta própria entram no patch como estão.
 */
@Service
public class HarvestTocaService implements HarvestTocaUseCase {

    private static final Logger log = LoggerFactory.getLogger(HarvestTocaService.class);

    private static final Duration GIT_TIMEOUT = Duration.ofSeconds(60);
    private static final String RANGE = WorkspaceChanges.BASE_TAG + "..HEAD";

    private final SandboxPort sandbox;
    private final TocaRepository tocas;

    public HarvestTocaService(SandboxPort sandbox, TocaRepository tocas) {
        this.sandbox = sandbox;
        this.tocas = tocas;
    }

    @Override
    public WorkspaceChanges harvest(
            TocaId id,
            String directory,
            String commitMessage
    ) {
        Toca toca = this.tocas.findById(id).orElseThrow(() -> new TocaNotFoundException(id));

        if (!toca.hasContainer()) {
            throw new IllegalStateException("A " + id + " não tem container para colher");
        }

        String container = toca.containerId();

        if (!isBlank(this.git(container, directory, "status", "--porcelain"))) {
            this.git(container, directory, "add", "--all");
            this.git(container, directory, "commit", "--quiet", "--no-verify", "--message", commitMessage);
        }

        String baseCommit = this.git(
                container,
                directory,
                "rev-parse",
                WorkspaceChanges.BASE_TAG + "^{commit}"
        ).strip();
        String patch = this.git(container, directory, "format-patch", "--stdout", "--binary", RANGE);
        List<ChangedFile> files = parseNumstat(this.git(container, directory, "diff", "--numstat", "--no-renames", RANGE));

        log.info("{}: {} arquivo(s) alterado(s) em {} desde {}", id, files.size(), directory, baseCommit);

        return new WorkspaceChanges(baseCommit, patch, files);
    }

    private String git(
            String container,
            String directory,
            String... args
    ) {
        List<String> command = new ArrayList<>(List.of("git", "-C", directory));

        command.addAll(List.of(args));

        ExecResult result = this.sandbox.exec(container, command, GIT_TIMEOUT);

        if (!result.succeeded()) {
            throw new IllegalStateException("git " + args[0] + " falhou na Toca: " + result.stderr().strip());
        }

        return result.stdout();
    }

    /** Linhas {@code adicionadas<TAB>removidas<TAB>caminho}; arquivo binário vem com "-" nos números. */
    static List<ChangedFile> parseNumstat(String numstat) {
        List<ChangedFile> files = new ArrayList<>();

        for (String line : numstat.lines().toList()) {
            String[] columns = line.split("\t", 3);

            if (columns.length == 3) {
                files.add(new ChangedFile(columns[2], count(columns[0]), count(columns[1])));
            }
        }

        return files;
    }

    private static int count(String column) {
        return "-".equals(column) ? 0 : Integer.parseInt(column);
    }
}
