package dev.aliencode.core.mission.port.repository;

import java.util.List;
import java.util.Optional;

import dev.aliencode.core.mission.domain.model.Mission;
import dev.aliencode.core.mission.domain.model.MissionId;

public interface MissionRepository {

    void save(Mission mission);

    Optional<Mission> findById(MissionId id);

    /** Da mais recente para a mais antiga. */
    List<Mission> findAll();
}
