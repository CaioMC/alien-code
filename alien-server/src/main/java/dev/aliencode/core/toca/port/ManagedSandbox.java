package dev.aliencode.core.toca.port;

/** Container de Toca encontrado no Docker (pelos rótulos), exista ou não uma Toca registrada. */
public record ManagedSandbox(String containerId, String tocaId) {
}
