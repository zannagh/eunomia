package de.zannagh.eunomia.clients;

/**
 * The out-of-band signals the relay uses to end a session, and the only place they are spelled out on the Java
 * side. Each mirrors a constant on the C# relay; the two have to move together, so they are collected here
 * rather than scattered as literals through {@link ExternalServerClient}'s conditionals.
 */
final class RelayProtocol {

    /**
     * HTTP status the relay returns on a REST send to a blocked scope. Terminal: the relay refuses to serve
     * this Minecraft server at all.
     */
    static final int BLOCKED_STATUS = 403;

    /**
     * WebSocket close code (RFC 6455 "policy violation") the relay uses to close a blocked scope's socket.
     * Terminal, and the socket-side twin of {@link #BLOCKED_STATUS}.
     */
    static final int POLICY_VIOLATION_CLOSE = 1008;

    /**
     * WebSocket close code the relay uses when the {@code v=} on the handshake names an API version it does not
     * serve. Mirrors {@code WebSocketMiddleware.UnsupportedVersionCloseCode} in
     * {@code csharp/src/Eunomia.Server.Api/Middlewares/WebSocketMiddleware.cs}; keep the two in step.
     * <p>
     * It is deliberately an application close code (4000-4999) rather than {@link #POLICY_VIOLATION_CLOSE},
     * because 1008 means "the relay blocked this scope" while this means "the relay does not speak your
     * version". Both are terminal and both fall back to the Minecraft transport, but they must never be
     * conflated in logs or diagnostics: one is an operator decision, the other a deployment mismatch.
     */
    static final int UNSUPPORTED_VERSION_CLOSE = 4001;

    private RelayProtocol() {
    }
}
