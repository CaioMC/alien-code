package dev.aliencode.core.toca.domain;

import java.net.URI;

/**
 * Como o Alien Server alcança o opencode que roda dentro da Toca.
 * A senha é gerada por Toca e nunca sai do servidor (não aparece na API REST).
 */
public record TocaEndpoint(URI baseUrl, String username, String password) {

    public TocaEndpoint {
        if (baseUrl == null) {
            throw new IllegalArgumentException("O endpoint da Toca precisa de uma URL");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("O endpoint da Toca precisa de uma senha");
        }
    }

    @Override
    public String toString() {
        return "TocaEndpoint[" + this.baseUrl + ", user=" + this.username + "]";
    }
}
