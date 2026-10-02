package dev.aliencode.core.toca.usecase;

import dev.aliencode.core.toca.domain.model.TocaId;
import dev.aliencode.core.toca.domain.model.WorkspaceChanges;

/** Passo 6 do ciclo de vida da Toca (especificação, seção 5): colher o que o agente produziu. */
public interface HarvestTocaUseCase {

    /**
     * Commita o que o agente deixou sem commitar e devolve tudo desde o commit base como patch.
     *
     * @param directory     repositório dentro da Toca (ex.: /workspace/calc)
     * @param commitMessage mensagem do commit das alterações não commitadas
     */
    WorkspaceChanges harvest(
            TocaId id,
            String directory,
            String commitMessage
    );
}
