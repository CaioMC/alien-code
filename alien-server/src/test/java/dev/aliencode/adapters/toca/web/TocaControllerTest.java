package dev.aliencode.adapters.toca.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import dev.aliencode.core.toca.domain.Seed;
import dev.aliencode.core.toca.domain.Toca;
import dev.aliencode.core.toca.domain.TocaEndpoint;
import dev.aliencode.core.toca.domain.TocaId;
import dev.aliencode.core.toca.domain.TocaNotFoundException;
import dev.aliencode.core.toca.domain.TocaProvisioningException;
import dev.aliencode.core.toca.usecase.DisposeTocaUseCase;
import dev.aliencode.core.toca.usecase.ListTocasUseCase;
import dev.aliencode.core.toca.usecase.ProvisionTocaCommand;
import dev.aliencode.core.toca.usecase.ProvisionTocaUseCase;

@WebMvcTest(TocaController.class)
class TocaControllerTest {

    private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ProvisionTocaUseCase provision;
    @MockitoBean
    DisposeTocaUseCase dispose;
    @MockitoBean
    ListTocasUseCase list;

    @Test
    void provisionaEDevolveATocaSemASenha() throws Exception {
        Toca toca = readyToca();
        ArgumentCaptor<ProvisionTocaCommand> command = ArgumentCaptor.forClass(ProvisionTocaCommand.class);
        when(this.provision.provision(command.capture())).thenReturn(toca);

        this.mvc.perform(post("/api/tocas").contentType(MediaType.APPLICATION_JSON).content("""
                        {"missionId": "m-1", "seed": {"type": "existing",
                         "repositories": [{"path": "/home/dev/api", "ref": "main"}]}}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(toca.id().value()))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.agentUrl").value("http://127.0.0.1:40001"))
                .andExpect(jsonPath("$.containerId").value("0123456789ab"))
                .andExpect(content().string(not(containsString("s3nha"))));

        Seed.ExistingRepositories seed = (Seed.ExistingRepositories) command.getValue().seed();
        org.assertj.core.api.Assertions.assertThat(seed.repositories().getFirst().name()).isEqualTo("api");
    }

    @Test
    void seedInvalidoVira400() throws Exception {
        this.mvc.perform(post("/api/tocas").contentType(MediaType.APPLICATION_JSON).content("""
                        {"seed": {"type": "nuvem"}}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("nuvem")));
    }

    @Test
    void falhaDeProvisionamentoVira502ComATocaFailed() throws Exception {
        Toca failed = readyToca().failed("opencode não respondeu");
        when(this.provision.provision(any())).thenThrow(new TocaProvisioningException(failed, new RuntimeException()));

        this.mvc.perform(post("/api/tocas").contentType(MediaType.APPLICATION_JSON).content("""
                        {"seed": {"type": "new", "name": "demo"}}
                        """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.toca.status").value("FAILED"));
    }

    @Test
    void listaBuscaEDescarta() throws Exception {
        Toca toca = readyToca();
        when(this.list.list()).thenReturn(List.of(toca));
        when(this.list.get(toca.id())).thenReturn(toca);
        when(this.dispose.dispose(toca.id())).thenReturn(toca.disposed());

        this.mvc.perform(get("/api/tocas")).andExpect(jsonPath("$[0].id").value(toca.id().value()));
        this.mvc.perform(get("/api/tocas/" + toca.id())).andExpect(status().isOk());
        this.mvc.perform(delete("/api/tocas/" + toca.id())).andExpect(jsonPath("$.status").value("DISPOSED"));
    }

    @Test
    void tocaInexistenteVira404() throws Exception {
        TocaId id = TocaId.newId();
        when(this.list.get(id)).thenThrow(new TocaNotFoundException(id));

        this.mvc.perform(get("/api/tocas/" + id)).andExpect(status().isNotFound());
    }

    private static Toca readyToca() {
        return Toca.provisioning(TocaId.newId(), "m-1", NOW, NOW.plusSeconds(3600))
                .withContainer("0123456789abcdef", new TocaEndpoint(URI.create("http://127.0.0.1:40001"), "opencode", "s3nha"))
                .ready(List.of("/workspace/api"));
    }
}
