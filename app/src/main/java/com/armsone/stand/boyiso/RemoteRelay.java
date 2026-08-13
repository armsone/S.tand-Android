package com.armsone.stand.boyiso;

import org.json.JSONException;
import org.json.JSONObject;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

final class RemoteRelay {
    interface Listener {
        void onEvent(BoyisoEvent event, String path);
        void onPathCount(String path, int count);
        void onTransportError(String path, String message);
    }

    private final OkHttpClient client = new OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();
    private final String relayUrl;
    private final String routingChannel;
    private final String sourceId;
    private final CryptoCodec codec;
    private final Listener listener;
    private final ScheduledExecutorService retry = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean running;
    private volatile WebSocket socket;
    private volatile int reconnectAttempt;
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);

    RemoteRelay(String relayUrl, String roomId, String roomKey, String sourceId,
                CryptoCodec codec, Listener listener) {
        this.relayUrl = relayUrl;
        this.routingChannel = routingChannel(roomId, roomKey);
        this.sourceId = sourceId;
        this.codec = codec;
        this.listener = listener;
    }

    void start() {
        if (relayUrl == null || relayUrl.trim().isEmpty()) return;
        running = true;
        connect();
    }

    private void connect() {
        if (!running) return;
        reconnectScheduled.set(false);
        Request request = new Request.Builder().url(relayUrl).build();
        socket = client.newWebSocket(request, new WebSocketListener() {
            @Override public void onOpen(WebSocket webSocket, Response response) {
                reconnectAttempt = 0;
                listener.onPathCount("INTERNET", 1);
                sendJson("join", null);
            }

            @Override public void onMessage(WebSocket webSocket, String text) {
                try {
                    JSONObject json = new JSONObject(text);
                    if (!"event".equals(json.optString("type"))) return;
                    if (!routingChannel.equals(json.optString("channel"))) return;
                    if (sourceId.equals(json.optString("sender"))) return;
                    String encoded = json.getString("payload");
                    listener.onEvent(codec.openEvent(java.util.Base64.getDecoder().decode(encoded)), "인터넷");
                } catch (JSONException | GeneralSecurityException | IllegalArgumentException ignored) {
                    // Malformed or unauthenticated room traffic is deliberately ignored.
                }
            }

            @Override public void onClosing(WebSocket webSocket, int code, String reason) {
                listener.onPathCount("INTERNET", 0);
            }

            @Override public void onClosed(WebSocket webSocket, int code, String reason) {
                listener.onPathCount("INTERNET", 0);
                scheduleReconnect();
            }

            @Override public void onFailure(WebSocket webSocket, Throwable error, Response response) {
                listener.onPathCount("INTERNET", 0);
                if (running) listener.onTransportError("인터넷", error.getMessage());
                scheduleReconnect();
            }
        });
    }

    void send(BoyisoEvent event) {
        if (!running || socket == null) return;
        try {
            sendJson("event", java.util.Base64.getEncoder().encodeToString(codec.sealEvent(event)));
        } catch (GeneralSecurityException error) {
            listener.onTransportError("인터넷", "암호화 실패");
        }
    }

    void stop() {
        running = false;
        WebSocket active = socket;
        socket = null;
        if (active != null) active.close(1000, "보이소 연결 종료");
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
        retry.shutdownNow();
        listener.onPathCount("INTERNET", 0);
    }

    private void scheduleReconnect() {
        if (!running || retry.isShutdown() || !reconnectScheduled.compareAndSet(false, true)) return;
        int attempt = Math.min(5, reconnectAttempt++);
        long delaySeconds = Math.min(30, 1L << attempt);
        retry.schedule(this::connect, delaySeconds, TimeUnit.SECONDS);
    }

    private void sendJson(String type, String payload) {
        WebSocket active = socket;
        if (active == null) return;
        JSONObject json = new JSONObject();
        try {
            json.put("type", type);
            json.put("channel", routingChannel);
            json.put("sender", sourceId);
            if (payload != null) json.put("payload", payload);
            active.send(json.toString());
        } catch (JSONException ignored) { }
    }

    static String routingChannel(String roomId, String roomKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(("boyiso-route-v2|" + roomId + "|" + roomKey)
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Unable to derive routing channel", error);
        }
    }
}
