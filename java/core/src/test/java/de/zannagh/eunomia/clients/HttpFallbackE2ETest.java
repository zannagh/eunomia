package de.zannagh.eunomia.clients;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import de.zannagh.eunomia.common.ApiVersion;
import de.zannagh.eunomia.keyed.StoreSyncPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A black-box end-to-end test of the HTTP fallback: it launches the real .NET relay server as a subprocess (with
 * the Mojang gate disabled so it runs offline) and drives it with a raw {@link java.net.http} mock client, using
 * the exact {@link PacketEnvelope}/{@link WsFrame} wire types the Java fallback client uses. It proves the whole
 * fallback contract: a keyed put is stored, relayed to same-scope peers (not the sender), isolated from other
 * scopes, replayed as a snapshot to a later joiner, and rejected (409) when the sender holds no live socket.
 * <p>
 * Opt-in: skipped unless {@code EUNOMIA_E2E=1} and both {@code dotnet} and the csharp project are present, so it
 * never runs (or fails) in a normal unit-test pass. Run with:
 * {@code EUNOMIA_E2E=1 ./gradlew :core:test --tests '*HttpFallbackE2ETest'}
 */
class HttpFallbackE2ETest {

    private static final Gson GSON = new Gson();
    private static final String CHANNEL = "test:e2e";
    // The relay versions its REST surface by URL segment; /health and /ws stay unversioned.
    private static final String PACKETS_KEYED = "/api/v" + ApiVersion.CURRENT + "/packets/keyed";
    private static final Duration RECEIVE = Duration.ofSeconds(5);

    private final HttpClient http = HttpClient.newHttpClient();

    private Path webProject;
    private Path workDir;
    private Process server;
    private int port;
    private String base;

    @BeforeEach
    void startServer() throws Exception {
        Assumptions.assumeTrue("1".equals(System.getenv("EUNOMIA_E2E")), "set EUNOMIA_E2E=1 to run the E2E test");
        webProject = locateWebProject();
        Assumptions.assumeTrue(webProject != null, "csharp Eunomia.Server.Web project not found");
        Assumptions.assumeTrue(commandExists("dotnet"), "dotnet not on PATH");

        port = freePort();
        base = "http://127.0.0.1:" + port;
        workDir = Files.createTempDirectory("eunomia-e2e");

        ProcessBuilder builder = new ProcessBuilder(
                "dotnet", "run", "--project", webProject.toString(), "--urls", base)
                .directory(workDir.toFile())
                .redirectErrorStream(true)
                .redirectOutput(workDir.resolve("server.log").toFile());
        builder.environment().put("EUNOMIA_DISABLE_MOJANG_GATE", "1");
        // Isolate the server's persistence to this run's temp dir (the store otherwise writes ./data
        // relative to the app content root, which would leak state between runs).
        builder.environment().put("EUNOMIA_DATA_DIR", workDir.resolve("data").toString());
        server = builder.start();

        awaitHealth();
    }

    @AfterEach
    void stopServer() throws Exception {
        if (server != null) {
            server.destroy();
            if (!server.waitFor(10, TimeUnit.SECONDS)) {
                server.destroyForcibly();
            }
        }
    }

    @Test
    void keyedPutIsStoredRelayedScopedAndSnapshotted() throws Exception {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID carol = UUID.randomUUID();
        UUID dave = UUID.randomUUID();
        UUID eve = UUID.randomUUID();
        String scope1 = "mc.one:25565";
        String scope2 = "mc.two:25565";

        MockWs aliceWs = openWebSocket(alice, scope1);
        MockWs bobWs = openWebSocket(bob, scope1);
        MockWs carolWs = openWebSocket(carol, scope2);

        // Alice pushes her keyed replicated entry.
        JsonObject payload = new JsonObject();
        payload.addProperty("playerId", alice.toString());
        payload.addProperty("note", "hello");
        PacketEnvelope envelope = new PacketEnvelope(
                scope1, "Scope One", CHANNEL, alice.toString(), true, alice.toString(), payload);
        assertEquals(200, put(PACKETS_KEYED, envelope));

        // Bob (same scope, not the sender) receives the relayed envelope.
        WsFrame relayed = awaitFrame(bobWs, "envelope");
        assertNotNull(relayed, "same-scope peer should receive the relay");
        PacketEnvelope relayEnv = GSON.fromJson(relayed.data, PacketEnvelope.class);
        assertEquals(CHANNEL, relayEnv.channel);
        assertEquals("hello", relayEnv.payload.getAsJsonObject().get("note").getAsString());

        // The sender does not get its own relay; the other scope is isolated.
        assertNull(aliceWs.poll(1000), "sender must not receive its own relay");
        assertNull(carolWs.poll(1000), "a different scope must not receive the relay");

        // A later joiner on the same scope receives the stored snapshot on connect.
        MockWs daveWs = openWebSocket(dave, scope1);
        WsFrame snapshot = awaitFrame(daveWs, "store_sync");
        assertNotNull(snapshot, "a newcomer should receive the store snapshot");
        StoreSyncPayload sync = GSON.fromJson(snapshot.data, StoreSyncPayload.class);
        assertEquals(CHANNEL, sync.channel);
        assertTrue(sync.entries.containsKey(alice.toString()), "snapshot carries the stored entry");

        // A put from an identity with no live socket is refused (session gate).
        PacketEnvelope spoof = new PacketEnvelope(scope1, "Scope One", CHANNEL, eve.toString(), true, eve.toString(), payload);
        assertEquals(409, put(PACKETS_KEYED, spoof));

        aliceWs.close();
        bobWs.close();
        carolWs.close();
        daveWs.close();
    }

    // --- helpers ---------------------------------------------------------------------------------------

    private WsFrame awaitFrame(MockWs socket, String type) throws InterruptedException {
        long deadline = System.nanoTime() + RECEIVE.toNanos();
        while (System.nanoTime() < deadline) {
            String raw = socket.poll(200);
            if (raw == null) {
                continue;
            }
            WsFrame frame = GSON.fromJson(raw, WsFrame.class);
            if (frame != null && type.equals(frame.type)) {
                return frame;
            }
        }
        return null;
    }

    private int put(String path, PacketEnvelope envelope) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(GSON.toJson(envelope)))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    private MockWs openWebSocket(UUID id, String scope) throws Exception {
        String query = "/ws?id=" + id + "&scope=" + URLEncoder.encode(scope, StandardCharsets.UTF_8)
                + "&v=" + ApiVersion.CURRENT;
        URI uri = URI.create("ws://127.0.0.1:" + port + query);
        return new MockWs(http, uri);
    }

    private void awaitHealth() throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
        while (System.nanoTime() < deadline) {
            if (!server.isAlive()) {
                throw new IllegalStateException("server exited early; log:\n" + tailLog());
            }
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/health"))
                        .timeout(Duration.ofSeconds(2)).GET().build();
                if (http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200) {
                    return;
                }
            } catch (Exception ignored) {
                // not up yet
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException("server /health never came up; log:\n" + tailLog());
    }

    private String tailLog() {
        try {
            return Files.readString(workDir.resolve("server.log"));
        } catch (IOException e) {
            return "(no log)";
        }
    }

    private static Path locateWebProject() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve("csharp/src/Eunomia.Server.Web/Eunomia.Server.Web.csproj");
            if (Files.exists(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        return null;
    }

    private static boolean commandExists(String command) {
        try {
            Process probe = new ProcessBuilder(command, "--version").redirectErrorStream(true).start();
            return probe.waitFor(20, TimeUnit.SECONDS) && probe.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /** A minimal WebSocket client that queues each complete text frame. */
    private static final class MockWs {
        private final BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        private final StringBuilder buffer = new StringBuilder();
        private final WebSocket webSocket;

        MockWs(HttpClient http, URI uri) throws Exception {
            this.webSocket = http.newWebSocketBuilder()
                    .buildAsync(uri, new WebSocket.Listener() {
                        @Override
                        public void onOpen(WebSocket socket) {
                            socket.request(1);
                        }

                        @Override
                        public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
                            buffer.append(data);
                            if (last) {
                                frames.add(buffer.toString());
                                buffer.setLength(0);
                            }
                            socket.request(1);
                            return null;
                        }
                    })
                    .get(10, TimeUnit.SECONDS);
        }

        String poll(long millis) throws InterruptedException {
            return frames.poll(millis, TimeUnit.MILLISECONDS);
        }

        void close() {
            try {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
            } catch (Exception ignored) {
                // best effort
            }
        }
    }
}
