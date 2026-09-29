package dev.aliencode.core.toca.port.harness;

public class HarnessNotReadyException extends RuntimeException {

    public HarnessNotReadyException(String message) {
        super(message);
    }
}
