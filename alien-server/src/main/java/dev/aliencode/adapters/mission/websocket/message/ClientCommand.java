package dev.aliencode.adapters.mission.websocket.message;

/**
 * Mensagem do cliente para o servidor. No M1: {@code {"type":"stop"}}.
 * Orientação ({@code user_message}) e aprovação ({@code approval}) chegam no M2.
 */
public record ClientCommand(String type) {

    public static final String STOP = "stop";
}
