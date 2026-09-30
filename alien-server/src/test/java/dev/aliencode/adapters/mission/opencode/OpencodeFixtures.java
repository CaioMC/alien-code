package dev.aliencode.adapters.mission.opencode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Fluxos SSE reais do opencode 1.18.33, gravados de uma Toca com um LLM roteirizado
 * (ler calc.py → editar → rodar o teste; abort durante {@code sleep 60}; provedor com HTTP 400).
 */
final class OpencodeFixtures {

    static final String COMPLETED = "tarefa-concluida.sse";
    static final String ABORTED = "tarefa-abortada.sse";
    static final String PROVIDER_ERROR = "erro-do-provedor.sse";

    static final String COMPLETED_SESSION = "ses_f1015c5d6ffe0XTfs4w5VfQN3Z";
    static final String ABORTED_SESSION = "ses_f10151278ffeZyJHh5UogAIcGa";
    static final String ERROR_SESSION = "ses_f1014d136ffer03oRDGr2rT02z";

    private OpencodeFixtures() {
    }

    static String raw(String name) {
        try (InputStream in = OpencodeFixtures.class.getResourceAsStream("/opencode/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** O JSON de cada linha {@code data:}. */
    static List<String> data(String name) {
        return raw(name).lines().filter(line -> line.startsWith("data: ")).map(line -> line.substring(6)).toList();
    }
}
