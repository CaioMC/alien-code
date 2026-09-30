package dev.aliencode.adapters.mission.opencode;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.aliencode.core.mission.port.agent.AgentEvent;

import static dev.aliencode.adapters.mission.opencode.OpencodeFixtures.ABORTED;
import static dev.aliencode.adapters.mission.opencode.OpencodeFixtures.COMPLETED;
import static dev.aliencode.adapters.mission.opencode.OpencodeFixtures.COMPLETED_SESSION;
import static dev.aliencode.adapters.mission.opencode.OpencodeFixtures.PROVIDER_ERROR;
import static org.assertj.core.api.Assertions.assertThat;

class OpencodeEventTranslatorTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void tarefaConcluidaViraRaciocinioTextoToolsDiffTokensEIdle() throws JsonProcessingException {
        List<AgentEvent> events = this.translate(COMPLETED);

        assertThat(events).extracting(event -> event.getClass().getSimpleName()).containsSubsequence(
                "ReasoningDelta",
                "TextDelta",
                "ToolStarted",
                "ToolFinished",
                "ModelCallFinished",
                "ToolStarted",
                "ToolFinished",
                "FileChanged",
                "ToolStarted",
                "ToolFinished",
                "TextDelta",
                "SessionIdle"
        );

        assertThat(events).allMatch(event -> event.sessionId().equals(COMPLETED_SESSION));
        assertThat(events.getLast()).isInstanceOf(AgentEvent.SessionIdle.class);

        String reasoning = this.textOf(events, AgentEvent.ReasoningDelta.class);

        assertThat(reasoning).isEqualTo("Preciso ver o arquivo antes. ");
        assertThat(this.textOf(events, AgentEvent.TextDelta.class)).startsWith("Vou ler o calc.py. ").endsWith("imprime 5. ");
    }

    @Test
    void cadaToolComecaUmaVezETerminaComTituloSaidaEDuracao() throws JsonProcessingException {
        List<AgentEvent> events = this.translate(COMPLETED);

        List<AgentEvent.ToolStarted> started = this.ofType(events, AgentEvent.ToolStarted.class);
        List<AgentEvent.ToolFinished> finished = this.ofType(events, AgentEvent.ToolFinished.class);

        assertThat(started).extracting(AgentEvent.ToolStarted::tool).containsExactly("read", "edit", "bash");
        assertThat(started.getFirst().input()).containsEntry("filePath", "/workspace/demo/calc.py");
        assertThat(started.getLast().title()).isEqualTo("python3 -c 'from calc import soma; print(soma(2,3))'");

        AgentEvent.ToolFinished bash = finished.getLast();

        assertThat(bash.success()).isTrue();
        assertThat(bash.output()).isEqualTo("5\n");
        assertThat(bash.exitCode()).isZero();
        assertThat(bash.durationMs()).isPositive();
        assertThat(finished.getFirst().exitCode()).isNull();
    }

    @Test
    void edicaoTrazODiffDoArquivo() throws JsonProcessingException {
        AgentEvent.FileChanged changed = this.ofType(this.translate(COMPLETED), AgentEvent.FileChanged.class).getFirst();

        assertThat(changed.path()).isEqualTo("/workspace/demo/calc.py");
        assertThat(changed.patch()).contains("-    return a - b").contains("+    return a + b");
        assertThat(changed.additions()).isEqualTo(1);
        assertThat(changed.deletions()).isEqualTo(1);
    }

    @Test
    void cadaChamadaAoModeloReportaTokens() throws JsonProcessingException {
        List<AgentEvent.ModelCallFinished> calls = this.ofType(this.translate(COMPLETED), AgentEvent.ModelCallFinished.class);

        assertThat(calls).hasSize(4);
        assertThat(calls.getFirst().inputTokens()).isEqualTo(6724);
        assertThat(calls.getFirst().outputTokens()).isEqualTo(42);
    }

    @Test
    void abortViraFalhaAbortadaSeguidaDeIdle() throws JsonProcessingException {
        List<AgentEvent> events = this.translate(ABORTED);
        AgentEvent.SessionFailed failed = this.ofType(events, AgentEvent.SessionFailed.class).getFirst();

        assertThat(failed.aborted()).isTrue();
        assertThat(this.ofType(events, AgentEvent.ToolStarted.class)).extracting(AgentEvent.ToolStarted::title).containsExactly("sleep 60");
        assertThat(events.getLast()).isInstanceOf(AgentEvent.SessionIdle.class);
    }

    @Test
    void erroDoProvedorTrazAMensagemENaoEAbort() throws JsonProcessingException {
        AgentEvent.SessionFailed failed = this.ofType(this.translate(PROVIDER_ERROR), AgentEvent.SessionFailed.class).getFirst();

        assertThat(failed.aborted()).isFalse();
        assertThat(failed.message()).isEqualTo("model not found: qwen9:999b");
    }

    @Test
    void eventosSemSessaoOuDesconhecidosSaoIgnorados() throws JsonProcessingException {
        OpencodeEventTranslator translator = new OpencodeEventTranslator(this.json);

        assertThat(translator.translate(this.json.readTree("{\"type\":\"server.connected\",\"properties\":{}}"))).isEmpty();
        assertThat(translator.translate(this.json.readTree("{\"type\":\"file.edited\",\"properties\":{\"file\":\"/x\"}}"))).isEmpty();
        assertThat(translator.translate(this.json.readTree("{\"type\":\"lsp.updated\",\"properties\":{\"sessionID\":\"ses_1\"}}"))).isEmpty();
    }

    private List<AgentEvent> translate(String fixture) throws JsonProcessingException {
        OpencodeEventTranslator translator = new OpencodeEventTranslator(this.json);
        List<AgentEvent> events = new ArrayList<>();

        for (String data : OpencodeFixtures.data(fixture)) {
            events.addAll(translator.translate(this.json.readTree(data)));
        }

        return events;
    }

    private <T extends AgentEvent> List<T> ofType(
            List<AgentEvent> events,
            Class<T> type
    ) {
        return events.stream().filter(type::isInstance).map(type::cast).toList();
    }

    private String textOf(
            List<AgentEvent> events,
            Class<? extends AgentEvent> type
    ) {
        StringBuilder text = new StringBuilder();

        for (AgentEvent event : events) {
            switch (event) {
                case AgentEvent.TextDelta delta when type == AgentEvent.TextDelta.class -> text.append(delta.text());
                case AgentEvent.ReasoningDelta delta when type == AgentEvent.ReasoningDelta.class -> text.append(delta.text());
                default -> {
                }
            }
        }

        return text.toString();
    }
}
