package dev.aliencode.adapters.toca.opencode;

import java.util.List;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Provedor de modelos compatível com a API da OpenAI que o opencode usa dentro da Toca
 * (Ollama local, o proxy da TOTVS...).
 *
 * @param id      prefixo dos modelos no opencode ({@code id/modelo})
 * @param name    nome exibido pelo opencode (vazio = o próprio id)
 * @param baseUrl URL do provedor vista de dentro da Toca, com {@code /v1}
 * @param apiKey  chave enviada como {@code Authorization: Bearer}; vazia = sem autenticação
 * @param models  modelos oferecidos ao agente
 */
public record OpencodeProvider(
        String id,
        String name,
        String baseUrl,
        String apiKey,
        List<String> models
) {

    public OpencodeProvider {
        if (isBlank(id) || isBlank(baseUrl)) {
            throw new IllegalArgumentException("Informe a base-url do provedor '" + id + "' (alien.model.providers." + id + ".base-url)");
        }

        name = isBlank(name) ? id : name;
        models = isNull(models) ? List.of() : List.copyOf(models);
    }

    public boolean hasApiKey() {
        return !isBlank(this.apiKey);
    }

    @Override
    public String toString() {
        return "OpencodeProvider[id=" + this.id + ", baseUrl=" + this.baseUrl + ", models=" + this.models + "]";
    }
}
