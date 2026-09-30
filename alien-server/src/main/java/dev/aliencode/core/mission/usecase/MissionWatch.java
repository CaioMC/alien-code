package dev.aliencode.core.mission.usecase;

/** Acompanhamento de uma missão. Fechar para de receber eventos. */
public interface MissionWatch extends AutoCloseable {

    @Override
    void close();
}
