package dev.aliencode.core.toca.domain.model;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Identificador de uma Toca. Também vira o nome do container ("toca-3f9a1c2e"),
 * por isso o formato é restrito.
 */
public record TocaId(String value) {

    private static final Pattern FORMAT = Pattern.compile("toca-[a-f0-9]{8}");

    public TocaId {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Id de Toca inválido: " + value);
        }
    }

    public static TocaId newId() {
        return new TocaId("toca-" + UUID.randomUUID().toString().substring(0, 8));
    }

    @Override
    public String toString() {
        return this.value;
    }
}
