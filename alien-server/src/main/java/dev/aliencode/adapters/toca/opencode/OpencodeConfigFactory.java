package dev.aliencode.adapters.toca.opencode;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Monta o {@code opencode.json} que a Toca recebe em OPENCODE_CONFIG_CONTENT (especificação,
 * seção 6.3): o Ollama como provedor compatível com a API da OpenAI e as permissões do agente.
 *
 * <p>No M1 não há aprovação na UI, então edição e comandos são liberados dentro da Toca
 * (que é isolada e descartável); os pedidos de permissão chegam com o M2.
 */
public class OpencodeConfigFactory {

    public static final String PROVIDER = "ollama";

    private final ObjectMapper json;

    public OpencodeConfigFactory(ObjectMapper json) {
        this.json = json;
    }

    /**
     * @param baseUrl          URL do Ollama vista de dentro da Toca, com {@code /v1}
     * @param models           modelos disponíveis no Ollama
     * @param defaultModel     modelo padrão, no formato {@code ollama/modelo}
     * @param requestTimeoutMs tempo máximo de uma chamada ao modelo (na CPU, o padrão de 5 min estoura)
     */
    public String create(
            String baseUrl,
            List<String> models,
            String defaultModel,
            long requestTimeoutMs
    ) {
        ObjectNode config = this.json.createObjectNode();

        config.put("$schema", "https://opencode.ai/config.json");
        config.put("autoupdate", false);
        config.put("share", "disabled");
        config.put("model", defaultModel);
        config.put("small_model", defaultModel);

        ObjectNode provider = config.putObject("provider").putObject(PROVIDER);

        provider.put("npm", "@ai-sdk/openai-compatible");
        provider.put("name", "Ollama");
        provider.putObject("options").put("baseURL", baseUrl).put("timeout", requestTimeoutMs);

        ObjectNode modelNodes = provider.putObject("models");

        models.forEach(model -> modelNodes.putObject(model).put("name", model).put("tools", true));

        config.putObject("permission")
                .put("edit", "allow")
                .put("bash", "allow")
                .put("webfetch", "deny");

        return config.toString();
    }
}
