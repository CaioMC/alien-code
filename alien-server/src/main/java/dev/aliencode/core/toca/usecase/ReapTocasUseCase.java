package dev.aliencode.core.toca.usecase;

import java.util.List;

/** Faxina: nenhuma Toca deve sobreviver ao TTL nem a um reinício do servidor (RNF-04). */
public interface ReapTocasUseCase {

    /** Descarta as Tocas ativas cujo TTL expirou. Devolve os ids descartados. */
    List<String> reapExpired();

    /** Remove containers de Toca que não pertencem a nenhuma Toca conhecida. Devolve os ids dos containers. */
    List<String> removeOrphans();
}
