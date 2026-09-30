package dev.aliencode.core.mission.usecase.command;

import dev.aliencode.core.toca.domain.model.Seed;

import static java.util.Objects.isNull;

/**
 * @param title opcional; sem título, a primeira linha do prompt
 * @param model opcional ({@code provedor/modelo}); sem modelo, o padrão configurado
 */
public record StartMissionCommand(
        String title,
        String prompt,
        Seed seed,
        String model
) {

    public StartMissionCommand {
        if (isNull(seed)) {
            throw new IllegalArgumentException("Informe como semear a Toca da missão");
        }
    }
}
