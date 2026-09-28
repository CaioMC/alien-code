package dev.aliencode.core.toca.port;

import dev.aliencode.core.toca.domain.RepositorySeed;

import java.nio.file.Path;

/**
 * Faz uma cópia descartável de um repositório local para ser enviada à Toca.
 * O repositório original nunca é montado no container.
 */
public interface WorkspaceSnapshotPort {

    Path snapshot(RepositorySeed seed);

    void discard(Path snapshot);
}
