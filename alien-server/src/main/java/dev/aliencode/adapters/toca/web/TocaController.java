package dev.aliencode.adapters.toca.web;

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

import dev.aliencode.adapters.toca.web.dto.ProvisionTocaRequest;
import dev.aliencode.adapters.toca.web.dto.TocaView;
import dev.aliencode.core.toca.domain.TocaId;
import dev.aliencode.core.toca.usecase.DisposeTocaUseCase;
import dev.aliencode.core.toca.usecase.ListTocasUseCase;
import dev.aliencode.core.toca.usecase.ProvisionTocaUseCase;

@RestController
@RequestMapping("/api/tocas")
public class TocaController {

    private final ProvisionTocaUseCase provision;
    private final DisposeTocaUseCase dispose;
    private final ListTocasUseCase list;

    public TocaController(ProvisionTocaUseCase provision, DisposeTocaUseCase dispose, ListTocasUseCase list) {
        this.provision = provision;
        this.dispose = dispose;
        this.list = list;
    }

    /** Síncrono no M0: responde quando a Toca está pronta (ou falhou). No M1 o progresso vira eventos. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TocaView provision(@RequestBody ProvisionTocaRequest request) {
        return TocaView.from(this.provision.provision(request.toCommand()));
    }

    @GetMapping
    public List<TocaView> list() {
        return this.list.list().stream().map(TocaView::from).toList();
    }

    @GetMapping("/{id}")
    public TocaView get(@PathVariable String id) {
        return TocaView.from(this.list.get(new TocaId(id)));
    }

    @DeleteMapping("/{id}")
    public TocaView dispose(@PathVariable String id) {
        return TocaView.from(this.dispose.dispose(new TocaId(id)));
    }
}
