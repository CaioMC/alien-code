package dev.aliencode.adapters.toca.opencode;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.substringBefore;

/**
 * Monta o {@code opencode.json} que a Toca recebe em OPENCODE_CONFIG_CONTENT (especificação,
 * seção 6.3): os provedores de modelos compatíveis com a API da OpenAI e as permissões do agente.
 *
 * <p>No M1 não há aprovação na UI, então edição e comandos são liberados dentro da Toca
 * (que é isolada e descartável); os pedidos de permissão chegam com o M2.
 */
public class OpencodeConfigFactory {

    private final ObjectMapper json;

    public OpencodeConfigFactory(ObjectMapper json) {
        this.json = json;
    }

    /**
     * @param providers        provedores de modelos (Ollama, OpenRouter, um proxy LiteLLM...)
     * @param defaultModel     modelo padrão, no formato {@code provedor/modelo}; o provedor precisa estar na lista
     * @param requestTimeoutMs tempo máximo de uma chamada ao modelo (na CPU, o padrão de 5 min estoura)
     */
    public String create(
            List<OpencodeProvider> providers,
            String defaultModel,
            long requestTimeoutMs
    ) {
        this.requireProviderOf(defaultModel, providers);

        ObjectNode config = this.json.createObjectNode();

        config.put("$schema", "https://opencode.ai/config.json");
        config.put("autoupdate", false);
        config.put("share", "disabled");
        config.put("model", defaultModel);
        config.put("small_model", defaultModel);

        ObjectNode providerNodes = config.putObject("provider");

        providers.forEach(provider -> this.putProvider(providerNodes, provider, requestTimeoutMs));

        config.putObject("permission")
                .put("edit", "allow")
                .put("bash", "allow")
                .put("webfetch", "deny");

        return config.toString();
    }

    private void putProvider(
            ObjectNode providerNodes,
            OpencodeProvider provider,
            long requestTimeoutMs
    ) {
        ObjectNode providerNode = providerNodes.putObject(provider.id());

        providerNode.put("npm", "@ai-sdk/openai-compatible");
        providerNode.put("name", provider.name());

        ObjectNode options = providerNode.putObject("options").put("baseURL", provider.baseUrl()).put("timeout", requestTimeoutMs);

        if (provider.hasApiKey()) {
            options.put("apiKey", provider.apiKey());
        }

        ObjectNode modelNodes = providerNode.putObject("models");

        provider.models().forEach(model -> modelNodes.putObject(model).put("name", model).put("tools", true));
    }

    private void requireProviderOf(String defaultModel, List<OpencodeProvider> providers) {
        String providerId = isBlank(defaultModel) ? "" : substringBefore(defaultModel, "/");

        if (providers.stream().noneMatch(provider -> provider.id().equals(providerId))) {
            throw new IllegalArgumentException("O modelo padrão '" + defaultModel + "' usa o provedor '" + providerId + "', que não está em alien.model.providers");
        }
    }
}
