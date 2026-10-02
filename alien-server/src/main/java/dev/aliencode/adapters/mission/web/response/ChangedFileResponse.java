package dev.aliencode.adapters.mission.web.response;

public record ChangedFileResponse(
        String path,
        int additions,
        int deletions
) {
}
