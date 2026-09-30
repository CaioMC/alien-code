package dev.aliencode.core.mission.domain.model;

/** De onde veio um evento da timeline (campo {@code source} do envelope). */
public enum EventSource {
    ALIEN,
    OPENCODE,
    DOCKER;

    public String wireName() {
        return this.name().toLowerCase();
    }
}
