package dev.aliencode.adapters.mission.web.controller;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import dev.aliencode.adapters.mission.web.mapper.MissionWebMapper;
import dev.aliencode.adapters.toca.web.mapper.TocaWebMapper;
import dev.aliencode.core.mission.domain.exception.MissionNotFoundException;
import dev.aliencode.core.mission.domain.model.AgentModel;
import dev.aliencode.core.mission.domain.model.Mission;
import dev.aliencode.core.mission.domain.model.MissionId;
import dev.aliencode.core.mission.usecase.GetMissionUseCase;
import dev.aliencode.core.mission.usecase.StartMissionUseCase;
import dev.aliencode.core.mission.usecase.StopMissionUseCase;
import dev.aliencode.core.mission.usecase.command.StartMissionCommand;
import dev.aliencode.core.toca.domain.model.RepositorySeed;
import dev.aliencode.core.toca.domain.model.Seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MissionController.class)
@Import({MissionWebMapper.class, TocaWebMapper.class})
class MissionControllerTest {

    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");

    @Autowired
    MockMvc mvc;

    @MockitoBean
    StartMissionUseCase start;
    @MockitoBean
    GetMissionUseCase get;
    @MockitoBean
    StopMissionUseCase stop;

    @Test
    void abreAMissaoEDevolveCriada() throws Exception {
        Mission mission = this.mission();
        ArgumentCaptor<StartMissionCommand> command = ArgumentCaptor.forClass(StartMissionCommand.class);

        when(this.start.start(command.capture())).thenReturn(mission);

        this.mvc.perform(post("/api/missions").contentType(MediaType.APPLICATION_JSON).content("""
                        {"prompt": "Corrija a soma", "model": "ollama/qwen3:8b",
                         "seed": {"type": "existing", "repositories": [{"path": "/home/dev/calc"}]}}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(mission.id().value()))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.model").value("ollama/qwen3:8b"));

        assertThat(command.getValue().prompt()).isEqualTo("Corrija a soma");
        assertThat(command.getValue().model()).isEqualTo("ollama/qwen3:8b");
        assertThat(((Seed.ExistingRepositories) command.getValue().seed()).repositories().getFirst().name()).isEqualTo("calc");
    }

    @Test
    void semSeedEPedidoInvalido() throws Exception {
        this.mvc.perform(post("/api/missions").contentType(MediaType.APPLICATION_JSON).content("{\"prompt\": \"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Informe seed.type: 'existing' ou 'new'"));
    }

    @Test
    void snapshotTrazOUltimoSeq() throws Exception {
        Mission mission = this.mission();

        when(this.get.get(mission.id())).thenReturn(mission);
        when(this.get.lastSeq(mission.id())).thenReturn(42L);

        this.mvc.perform(get("/api/missions/" + mission.id().value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastSeq").value(42))
                .andExpect(jsonPath("$.title").value("Soma"));
    }

    @Test
    void missaoInexistenteE404() throws Exception {
        MissionId id = MissionId.newId();

        when(this.get.get(id)).thenThrow(new MissionNotFoundException(id));

        this.mvc.perform(get("/api/missions/" + id.value())).andExpect(status().isNotFound());
    }

    @Test
    void idMalformadoE400() throws Exception {
        this.mvc.perform(get("/api/missions/..%2Fetc")).andExpect(status().isBadRequest());
    }

    @Test
    void pararDevolveAceito() throws Exception {
        Mission mission = this.mission();

        when(this.stop.stop(any())).thenReturn(mission);

        this.mvc.perform(post("/api/missions/" + mission.id().value() + "/stop")).andExpect(status().isAccepted());

        verify(this.stop).stop(mission.id());
    }

    @Test
    void listaAsMissoes() throws Exception {
        when(this.get.list()).thenReturn(List.of(this.mission(), this.mission()));

        this.mvc.perform(get("/api/missions")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }

    private Mission mission() {
        Seed seed = new Seed.ExistingRepositories(List.of(new RepositorySeed("calc", Path.of("/home/dev/calc"), null)));

        return Mission.create(
                MissionId.newId(),
                "Soma",
                "Corrija a soma",
                seed,
                AgentModel.parse("ollama/qwen3:8b"),
                NOW
        );
    }
}
