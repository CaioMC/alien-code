package dev.aliencode.core.toca.port.sandbox;

public record ExecResult(int exitCode, String stdout, String stderr) {

    public boolean succeeded() {
        return this.exitCode == 0;
    }
}
