package dev.aliencode.core.mission.port.delivery;

import java.nio.file.Path;

/**
 * Leva a entrega ao repositório original do dev, numa branch nova que nasce do commit base.
 * A branch atual e os arquivos abertos do dev não são tocados.
 */
public interface DeliveryPort {

    /**
     * @return o último commit da branch criada
     * @throws dev.aliencode.core.mission.domain.exception.DeliveryConflictException se a branch já existe,
     *         o commit base não está no repositório ou o patch não aplica
     */
    String applyToBranch(
            Path repository,
            String baseCommit,
            String branch,
            String patch
    );
}
