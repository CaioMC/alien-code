package dev.aliencode.core.toca.port;

public class HarnessNotReadyException extends RuntimeException {

    public HarnessNotReadyException(String message) {
        super(message);
    }
}
