package dev.aliencode.core.mission.domain.exception;

/** A entrega não pode ser aplicada ou descartada agora (já resolvida, branch existente, commit base ausente...). */
public class DeliveryConflictException extends RuntimeException {

    public DeliveryConflictException(String message) {
        super(message);
    }

    public DeliveryConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
