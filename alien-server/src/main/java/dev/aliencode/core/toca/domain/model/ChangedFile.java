package dev.aliencode.core.toca.domain.model;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Um arquivo alterado na Toca, como no {@code git diff --numstat}.
 *
 * @param additions linhas adicionadas (0 para arquivo binário)
 * @param deletions linhas removidas (0 para arquivo binário)
 */
public record ChangedFile(
        String path,
        int additions,
        int deletions
) {

    public ChangedFile {
        if (isBlank(path)) {
            throw new IllegalArgumentException("Arquivo alterado sem caminho");
        }
    }
}
