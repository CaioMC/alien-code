package dev.aliencode.adapters.mission.web.mapper;

import org.springframework.stereotype.Component;

import dev.aliencode.adapters.mission.web.response.ChangedFileResponse;
import dev.aliencode.adapters.mission.web.response.DeliveryResponse;
import dev.aliencode.core.mission.domain.model.Delivery;
import dev.aliencode.core.toca.domain.model.ChangedFile;

@Component
public class DeliveryWebMapper {

    public DeliveryResponse toResponse(Delivery delivery) {
        return new DeliveryResponse(
                delivery.missionId().value(),
                delivery.status().name(),
                delivery.repository(),
                delivery.repositoryPath().toString(),
                delivery.baseCommit(),
                delivery.branch(),
                delivery.headCommit(),
                delivery.additions(),
                delivery.deletions(),
                delivery.files().stream().map(this::toFile).toList(),
                delivery.patch(),
                delivery.createdAt(),
                delivery.resolvedAt()
        );
    }

    private ChangedFileResponse toFile(ChangedFile file) {
        return new ChangedFileResponse(file.path(), file.additions(), file.deletions());
    }
}
