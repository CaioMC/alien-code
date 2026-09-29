package dev.aliencode.core.toca.port.workspace;

import java.nio.file.Path;

import dev.aliencode.core.toca.domain.model.RepositorySeed;

/**
 * Faz uma cópia descartável de um repositório local para ser enviada à Toca.
 * O repositório original nunca é montado no container.
 */
public interface WorkspaceSnapshotPort {

    Path snapshot(RepositorySeed seed);

    void discard(Path snapshot);
}
