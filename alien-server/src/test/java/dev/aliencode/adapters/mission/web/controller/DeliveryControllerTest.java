package dev.aliencode.adapters.mission.web.controller;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import dev.aliencode.adapters.mission.web.mapper.DeliveryWebMapper;
import dev.aliencode.adapters.toca.web.mapper.TocaWebMapper;
import dev.aliencode.core.mission.domain.exception.DeliveryConflictException;
import dev.aliencode.core.mission.domain.exception.DeliveryNotFoundException;
import dev.aliencode.core.mission.domain.model.Delivery;
import dev.aliencode.core.mission.domain.model.MissionId;
import dev.aliencode.core.mission.usecase.ApproveDeliveryUseCase;
import dev.aliencode.core.mission.usecase.GetDeliveryUseCase;
import dev.aliencode.core.mission.usecase.RejectDeliveryUseCase;
import dev.aliencode.core.toca.domain.model.ChangedFile;
import dev.aliencode.core.toca.domain.model.WorkspaceChanges;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeliveryController.class)
@Import({DeliveryWebMapper.class, TocaWebMapper.class})
class DeliveryControllerTest {

    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");
    private static final MissionId ID = new MissionId("m-0000002a");

    @Autowired
    MockMvc mvc;

    @MockitoBean
    GetDeliveryUseCase get;
    @MockitoBean
    ApproveDeliveryUseCase approve;
    @MockitoBean
    RejectDeliveryUseCase reject;

    @Test
    void mostraOPatchEOsArquivos() throws Exception {
        when(this.get.get(ID)).thenReturn(this.delivery());

        this.mvc.perform(get("/api/missions/m-0000002a/delivery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.branch").value("alien/m-0000002a"))
                .andExpect(jsonPath("$.additions").value(1))
                .andExpect(jsonPath("$.files[0].path").value("calc.py"))
                .andExpect(jsonPath("$.patch").value("From c0ffee0\n"));
    }

    @Test
    void aprovarDevolveAEntregaAplicada() throws Exception {
        when(this.approve.approve(ID)).thenReturn(this.delivery().applied("c0ffee0", NOW));

        this.mvc.perform(post("/api/missions/m-0000002a/delivery/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.headCommit").value("c0ffee0"));
    }

    @Test
    void conflitoViraHttp409() throws Exception {
        when(this.approve.approve(ID)).thenThrow(new DeliveryConflictException("A branch alien/m-0000002a já existe"));

        this.mvc.perform(post("/api/missions/m-0000002a/delivery/approve"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A branch alien/m-0000002a já existe"));
    }

    @Test
    void missaoSemEntregaViraHttp404() throws Exception {
        when(this.reject.reject(ID)).thenThrow(new DeliveryNotFoundException(ID));

        this.mvc.perform(post("/api/missions/m-0000002a/delivery/reject"))
                .andExpect(status().isNotFound());
    }

    private Delivery delivery() {
        WorkspaceChanges changes = new WorkspaceChanges("b45e000", "From c0ffee0\n", List.of(new ChangedFile("calc.py", 1, 1)));

        return Delivery.pending(
                ID,
                "calc",
                Path.of("/home/dev/calc"),
                changes,
                NOW
        );
    }
}
