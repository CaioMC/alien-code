package dev.aliencode.adapters.toca.web.request;

import java.util.List;

/**
 * Como semear a Toca.
 *
 * @param type         {@code existing} (repositórios locais) ou {@code new} (projeto novo)
 * @param name         nome do projeto novo (só para {@code new})
 * @param repositories repositórios a copiar (só para {@code existing})
 */
public record SeedRequest(String type, String name, List<RepositoryRequest> repositories) {

    public static final String EXISTING = "existing";
    public static final String NEW = "new";
}
