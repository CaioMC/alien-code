package dev.aliencode.core.toca.domain.model;

import java.util.List;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * O que o agente mudou num repositório da Toca desde que ele foi semeado.
 *
 * @param baseCommit commit em que o repositório foi semeado (existe também no repositório original)
 * @param patch      saída do {@code git format-patch --stdout base..HEAD}, aplicável com {@code git am}
 * @param files      arquivos alterados, com linhas adicionadas e removidas
 */
public record WorkspaceChanges(
        String baseCommit,
        String patch,
        List<ChangedFile> files
) {

    /** Tag que marca, dentro da Toca, o commit em que cada repositório foi semeado. */
    public static final String BASE_TAG = "alien-base";

    public WorkspaceChanges {
        if (isBlank(baseCommit)) {
            throw new IllegalArgumentException("Mudanças sem commit base");
        }

        patch = isNull(patch) ? "" : patch;
        files = isNull(files) ? List.of() : List.copyOf(files);
    }

    public boolean isEmpty() {
        return isBlank(this.patch);
    }

    public int additions() {
        return this.files.stream().mapToInt(ChangedFile::additions).sum();
    }

    public int deletions() {
        return this.files.stream().mapToInt(ChangedFile::deletions).sum();
    }
}
