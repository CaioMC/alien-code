package dev.aliencode.adapters.toca.web.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.aliencode.adapters.toca.web.mapper.TocaWebMapper;
import dev.aliencode.adapters.toca.web.request.ProvisionTocaRequest;
import dev.aliencode.adapters.toca.web.response.TocaResponse;
import dev.aliencode.core.toca.domain.model.TocaId;
import dev.aliencode.core.toca.usecase.DisposeTocaUseCase;
import dev.aliencode.core.toca.usecase.ListTocasUseCase;
import dev.aliencode.core.toca.usecase.ProvisionTocaUseCase;

/** Porta de entrada HTTP: só traduz (mapper) e delega (casos de uso). */
@RestController
@RequestMapping("/api/tocas")
public class TocaController {

    private final ProvisionTocaUseCase provision;
    private final DisposeTocaUseCase dispose;
    private final ListTocasUseCase list;
    private final TocaWebMapper mapper;

    public TocaController(ProvisionTocaUseCase provision, DisposeTocaUseCase dispose, ListTocasUseCase list,
                          TocaWebMapper mapper) {
        this.provision = provision;
        this.dispose = dispose;
        this.list = list;
        this.mapper = mapper;
    }

    /** Síncrono no M0: responde quando a Toca está pronta (ou falhou). No M1 o progresso vira eventos. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TocaResponse provision(@RequestBody ProvisionTocaRequest request) {
        return this.mapper.toResponse(this.provision.provision(this.mapper.toCommand(request)));
    }

    @GetMapping
    public List<TocaResponse> list() {
        return this.mapper.toResponses(this.list.list());
    }

    @GetMapping("/{id}")
    public TocaResponse get(@PathVariable String id) {
        return this.mapper.toResponse(this.list.get(new TocaId(id)));
    }

    @DeleteMapping("/{id}")
    public TocaResponse dispose(@PathVariable String id) {
        return this.mapper.toResponse(this.dispose.dispose(new TocaId(id)));
    }
}
