package dev.aliencode.adapters.toca.opencode;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpencodeConfigFactoryTest {

    private final ObjectMapper json = new ObjectMapper();

    private final OpencodeConfigFactory factory = new OpencodeConfigFactory(this.json);

    private final OpencodeProvider ollama = new OpencodeProvider(
            "ollama",
            "Ollama",
            "http://ollama:11434/v1",
            "",
            List.of("qwen3:8b")
    );

    private final OpencodeProvider proxy = new OpencodeProvider(
            "proxy",
            "Proxy LiteLLM",
            "https://proxy.example/v1",
            "sk-teste",
            List.of("claude-sonnet-4-6", "gpt-5-mini")
    );

    @Test
    void provedorComChaveLevaApiKeyEModelosComTools() throws Exception {
        JsonNode config = this.json.readTree(this.factory.create(List.of(this.proxy), "proxy/claude-sonnet-4-6", 60_000));
        JsonNode node = config.path("provider").path("proxy");

        assertThat(config.path("model").asText()).isEqualTo("proxy/claude-sonnet-4-6");
        assertThat(node.path("name").asText()).isEqualTo("Proxy LiteLLM");
        assertThat(node.path("options").path("baseURL").asText()).isEqualTo("https://proxy.example/v1");
        assertThat(node.path("options").path("apiKey").asText()).isEqualTo("sk-teste");
        assertThat(node.path("models").path("gpt-5-mini").path("tools").asBoolean()).isTrue();
    }

    @Test
    void provedorSemChaveNaoLevaApiKey() throws Exception {
        JsonNode config = this.json.readTree(this.factory.create(List.of(this.ollama), "ollama/qwen3:8b", 60_000));

        assertThat(config.path("provider").path("ollama").path("options").has("apiKey")).isFalse();
    }

    @Test
    void todosOsProvedoresVaoParaOOpencode() throws Exception {
        JsonNode config = this.json.readTree(this.factory.create(List.of(this.ollama, this.proxy), "ollama/qwen3:8b", 60_000));

        assertThat(config.path("provider").has("ollama")).isTrue();
        assertThat(config.path("provider").has("proxy")).isTrue();
    }

    @Test
    void modeloPadraoDeProvedorQueNaoExisteEhRecusado() {
        assertThatThrownBy(() -> this.factory.create(List.of(this.ollama), "openrouter/qwen3-coder", 60_000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("openrouter")
                .hasMessageContaining("alien.model.providers");
    }

    @Test
    void provedorSemNomeUsaOId() {
        OpencodeProvider provider = new OpencodeProvider("local", null, "http://local/v1", null, null);

        assertThat(provider.name()).isEqualTo("local");
        assertThat(provider.models()).isEmpty();
        assertThat(provider.hasApiKey()).isFalse();
    }

    @Test
    void toStringNaoExpoeAChave() {
        assertThat(this.proxy.toString()).doesNotContain("sk-teste");
    }

    @Test
    void provedorSemBaseUrlEhRecusado() {
        assertThatThrownBy(() -> new OpencodeProvider("proxy", "Proxy", " ", "sk", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alien.model.providers.proxy.base-url");
    }
}
