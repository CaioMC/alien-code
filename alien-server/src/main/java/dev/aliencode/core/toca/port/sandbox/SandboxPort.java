package dev.aliencode.core.toca.port.sandbox;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/** Ciclo de vida do container da Toca (implementado sobre o Docker Engine). */
public interface SandboxPort {

    SandboxHandle create(SandboxRequest request);

    /** Copia o conteúdo de {@code source} para {@code targetDir} dentro do container (dono: usuário da Toca). */
    void copyDirectory(String containerId, Path source, String targetDir);

    ExecResult exec(String containerId, List<String> command, Duration timeout);

    /** Remove o container e seus dados. Não falha se ele já não existir. */
    void remove(String containerId);

    /** Todos os containers com rótulo de Toca, inclusive órfãos de execuções anteriores do servidor. */
    List<ManagedSandbox> listManaged();
}
