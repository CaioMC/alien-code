package dev.aliencode.core.toca.usecase;

import dev.aliencode.core.toca.domain.Toca;

public interface ProvisionTocaUseCase {

    /** Cria o container, semeia o workspace e espera o opencode ficar pronto. */
    Toca provision(ProvisionTocaCommand command);
}
