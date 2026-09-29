package dev.aliencode.core.toca.domain.model;

import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Um repositório local que será copiado para /workspace/{name} dentro da Toca.
 *
 * @param name nome da pasta dentro de /workspace
 * @param path caminho do repositório na máquina do dev
 * @param ref  branch, tag ou commit a usar; nulo = o HEAD atual do repositório
 */
public record RepositorySeed(String name, Path path, String ref) {

    static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    public RepositorySeed {
        if (name == null || !NAME.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "Nome de repositório inválido: '" + name + "' (use letras, números, '.', '_' ou '-')");
        }
        if (path == null) {
            throw new IllegalArgumentException("O repositório '" + name + "' precisa de um caminho");
        }
        path = path.toAbsolutePath().normalize();
        if (ref != null && ref.isBlank()) {
            ref = null;
        }
    }

    public boolean hasRef() {
        return this.ref != null;
    }
}
