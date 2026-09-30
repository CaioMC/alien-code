package dev.aliencode.core.mission.domain.model;

import java.util.UUID;
import java.util.regex.Pattern;

import static java.util.Objects.isNull;

/** Identificador de uma missão ("m-3f9a1c2e"). Aparece em URLs, labels de container e no Event Store. */
public record MissionId(String value) {

    private static final Pattern FORMAT = Pattern.compile("m-[a-f0-9]{8}");

    public MissionId {
        if (isNull(value) || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Id de missão inválido: " + value);
        }
    }

    public static MissionId newId() {
        return new MissionId("m-" + UUID.randomUUID().toString().substring(0, 8));
    }

    @Override
    public String toString() {
        return this.value;
    }
}
