package dev.aliencode.adapters.toca.web.request;

/**
 * Um repositório local a copiar para a Toca.
 *
 * @param name nome da pasta em /workspace; se ausente, usa o nome da pasta do repositório
 * @param path caminho absoluto do repositório na máquina do dev
 * @param ref  branch, tag ou commit; se ausente, o HEAD atual
 */
public record RepositoryRequest(String name, String path, String ref) {
}
