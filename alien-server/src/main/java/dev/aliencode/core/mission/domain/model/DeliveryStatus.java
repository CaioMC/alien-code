package dev.aliencode.core.mission.domain.model;

public enum DeliveryStatus {
    /** Patch colhido, esperando o dev. */
    PENDING,
    /** Aplicado numa branch local do repositório original. */
    APPLIED,
    /** Descartado pelo dev. */
    REJECTED
}
