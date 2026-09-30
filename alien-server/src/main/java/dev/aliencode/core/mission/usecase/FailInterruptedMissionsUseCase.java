package dev.aliencode.core.mission.usecase;

import java.util.List;

/**
 * Missões que estavam ativas quando o servidor parou não têm mais quem as conduza
 * (as Tocas delas viram órfãs e são removidas). Marca essas missões como FAILED.
 */
public interface FailInterruptedMissionsUseCase {

    /** @return os ids das missões marcadas */
    List<String> failInterrupted();
}
