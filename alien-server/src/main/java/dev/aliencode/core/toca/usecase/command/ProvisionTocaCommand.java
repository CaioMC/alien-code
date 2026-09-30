package dev.aliencode.core.toca.usecase.command;

import dev.aliencode.core.toca.domain.model.Seed;

import static java.util.Objects.isNull;

/** @param missionId missão dona da Toca (opcional até o M1, quando missões existirem) */
public record ProvisionTocaCommand(
        String missionId,
        Seed seed
) {

    public ProvisionTocaCommand {
        if (isNull(seed)) {
            throw new IllegalArgumentException("Informe como semear a Toca");
        }
    }
}
