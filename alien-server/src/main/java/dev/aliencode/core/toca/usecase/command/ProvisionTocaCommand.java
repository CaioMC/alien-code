package dev.aliencode.core.toca.usecase.command;

import dev.aliencode.core.toca.domain.model.Seed;

/** @param missionId missão dona da Toca (opcional até o M1, quando missões existirem) */
public record ProvisionTocaCommand(String missionId, Seed seed) {

    public ProvisionTocaCommand {
        if (seed == null) {
            throw new IllegalArgumentException("Informe como semear a Toca");
        }
    }
}
