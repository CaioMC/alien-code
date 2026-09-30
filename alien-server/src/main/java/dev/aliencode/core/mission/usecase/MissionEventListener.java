package dev.aliencode.core.mission.usecase;

import dev.aliencode.core.mission.domain.model.AlienEvent;

@FunctionalInterface
public interface MissionEventListener {

    void onEvent(AlienEvent event);
}
