package dev.aliencode.core.toca.domain.model;

import java.nio.file.Path;
import java.util.regex.Pattern;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Um repositório local que será copiado para /workspace/{name} dentro da Toca.
 *
 * @param name nome da pasta dentro de /workspace
 * @param path caminho do repositório na máquina do dev
 * @param ref  branch, tag ou commit a usar; nulo = o HEAD atual do repositório
 */
public record RepositorySeed(
        String name,
        Path path,
        String ref
) {

    static final Pattern PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    public RepositorySeed {
        if (isNull(name) || !PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("Nome de repositório inválido: '" + name + "' (use letras, números, '.', '_' ou '-')");
        }

        if (isNull(path)) {
            throw new IllegalArgumentException("O repositório '" + name + "' precisa de um caminho");
        }

        path = path.toAbsolutePath().normalize();

        if (isBlank(ref)) {
            ref = null;
        }
    }

    public boolean hasRef() {
        return nonNull(this.ref);
    }
}
