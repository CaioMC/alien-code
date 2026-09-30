package dev.aliencode.core.toca.usecase;

import dev.aliencode.core.toca.domain.model.Seed;
import dev.aliencode.core.toca.domain.model.Toca;
import dev.aliencode.core.toca.usecase.command.ProvisionTocaCommand;

public interface ProvisionTocaUseCase {

    /** Cria o container, semeia o workspace e espera o opencode ficar pronto. */
    Toca provision(ProvisionTocaCommand command);

    /**
     * Checa, sem efeito colateral, se a semeadura é aceitável (repositórios existentes e dentro
     * das pastas permitidas). Lança {@link IllegalArgumentException} com o motivo.
     */
    void validate(Seed seed);
}
