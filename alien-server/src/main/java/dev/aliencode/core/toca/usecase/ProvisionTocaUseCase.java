package dev.aliencode.core.toca.usecase;

import dev.aliencode.core.toca.domain.model.Toca;
import dev.aliencode.core.toca.usecase.command.ProvisionTocaCommand;

public interface ProvisionTocaUseCase {

    /** Cria o container, semeia o workspace e espera o opencode ficar pronto. */
    Toca provision(ProvisionTocaCommand command);
}
