package dev.aliencode.adapters.mission.web.response;

import java.time.Instant;
import java.util.List;

/**
 * @param patch      saída do git format-patch, aplicável com git am
 * @param headCommit último commit da branch, depois de aplicada
 */
public record DeliveryResponse(
        String missionId,
        String status,
        String repository,
        String repositoryPath,
        String baseCommit,
        String branch,
        String headCommit,
        int additions,
        int deletions,
        List<ChangedFileResponse> files,
        String patch,
        Instant createdAt,
        Instant resolvedAt
) {
}
