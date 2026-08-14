package com.armsone.stand.boyiso;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioAttributes;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class MonitoringService extends Service implements LanTransport.Listener, BleTransport.Listener,
        RemoteRelay.Listener {
    private static final String TAG = "BoyisoMonitoring";
    static volatile boolean isActive;
    static final String ACTION_START = "com.armsone.stand.boyiso.START";
    static final String ACTION_STOP = "com.armsone.stand.boyiso.STOP";
    static final String ACTION_STATE = "com.armsone.stand.boyiso.STATE";
    static final String ACTION_EVENT = "com.armsone.stand.boyiso.EVENT";
    static final String ACTION_TOKTOK = "com.armsone.stand.boyiso.TOKTOK";
    static final String ACTION_MOVEMENT = "com.armsone.stand.boyiso.MOVEMENT";
    static final String ACTION_UPDATE_STAND_STATE = "com.armsone.stand.boyiso.UPDATE_STAND_STATE";
    static final String ACTION_UPDATE_IDENTITY = "com.armsone.stand.boyiso.UPDATE_IDENTITY";
    static final String EXTRA_ROLE = "role";
    static final String EXTRA_ROOM_CODE = "roomCode";
    static final String EXTRA_ROOM_ID = "roomId";
    static final String EXTRA_SOURCE_NAME = "sourceName";
    static final String EXTRA_DISPLAY_MODE = "displayMode";
    static final String EXTRA_SESSION_ACTIVE = "sessionActive";
    static final String ROLE_HOST = "host";
    static final String ROLE_GUEST = "guest";
    private static final String CHANNEL_ID = "boyiso_monitoring";
    private static final String TOKTOK_CHANNEL_ID = "boyiso_toktok";
    private static final int NOTIFICATION_ID = 4101;
    private static final long STALE_MILLIS = 15_000;

    private final EventDeduplicator deduplicator = new EventDeduplicator();
    private final Map<String, Long> sourceLastSeen = new ConcurrentHashMap<>();
    private final Map<String, BoyisoEvent> sourceLatest = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> sourcePaths = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler;
    private LanTransport lan;
    private BleTransport ble;
    private RemoteRelay remote;
    private AudioRecord audioRecord;
    private Thread audioThread;
    private PowerManager.WakeLock wakeLock;
    private WifiManager.MulticastLock multicastLock;
    private String role;
    private String sourceId;
    private String sourceName;
    private volatile boolean running;
    private volatile int lanCount;
    private volatile int bleCount;
    private volatile int internetCount;
    private volatile String latestError;
    private volatile long lastTokTokSentAt;
    private volatile boolean hadConnectedDevice;
    private volatile String displayMode = BoyisoEvent.MODE_OBJECT;
    private volatile boolean sessionActive;

    @Override public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        sourceId = getSharedPreferences("boyiso", MODE_PRIVATE).getString("source_id", null);
        if (sourceId == null) {
            sourceId = UUID.randomUUID().toString();
            getSharedPreferences("boyiso", MODE_PRIVATE).edit().putString("source_id", sourceId).apply();
        }
        sourceName = getSharedPreferences("boyiso", MODE_PRIVATE).getString(
                "device_name", Build.MANUFACTURER + " " + Build.MODEL);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        if (ACTION_STOP.equals(intent.getAction())) {
            stopMonitoring();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_TOKTOK.equals(intent.getAction())) {
            sendTokTok();
            return START_NOT_STICKY;
        }
        if (ACTION_MOVEMENT.equals(intent.getAction())) {
            sendMovement();
            return START_NOT_STICKY;
        }
        if (ACTION_UPDATE_STAND_STATE.equals(intent.getAction())) {
            updateStandState(intent);
            return START_NOT_STICKY;
        }
        if (ACTION_UPDATE_IDENTITY.equals(intent.getAction())) {
            updateIdentity(intent);
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(intent.getAction())) return START_NOT_STICKY;
        if (running) return START_REDELIVER_INTENT;
        role = intent.getStringExtra(EXTRA_ROLE);
        displayMode = normalizedDisplayMode(intent.getStringExtra(EXTRA_DISPLAY_MODE));
        sessionActive = intent.getBooleanExtra(EXTRA_SESSION_ACTIVE, false);
        String roomCode = intent.getStringExtra(EXTRA_ROOM_CODE);
        String roomId = intent.getStringExtra(EXTRA_ROOM_ID);
        String requestedSourceName = intent.getStringExtra(EXTRA_SOURCE_NAME);
        if (requestedSourceName != null && !requestedSourceName.trim().isEmpty()) {
            sourceName = requestedSourceName.trim();
            getSharedPreferences("boyiso", MODE_PRIVATE).edit()
                    .putString("device_name", sourceName).apply();
        }
        if ((!ROLE_HOST.equals(role) && !ROLE_GUEST.equals(role)) || roomCode == null
                || roomId == null || roomId.trim().isEmpty()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            startMonitoring(new CryptoCodec(roomCode), roomId, roomCode);
        } catch (IllegalArgumentException error) {
            latestError = "QR 초대 정보가 올바르지 않습니다";
            broadcastState();
            stopSelf();
        }
        return running ? START_REDELIVER_INTENT : START_NOT_STICKY;
    }

    private void startMonitoring(CryptoCodec codec, String roomId, String roomKey) {
        running = true;
        isActive = true;
        int foregroundType = 0;
        if (Build.VERSION.SDK_INT >= 29) {
            foregroundType = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE;
            if (ROLE_GUEST.equals(role)
                    && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                foregroundType |= android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
            }
            startForeground(NOTIFICATION_ID, buildNotification(), foregroundType);
        } else {
            startForeground(NOTIFICATION_ID, buildNotification());
        }
        acquireLocks();
        lan = new LanTransport(this, codec, sourceId, this);
        ble = new BleTransport(this, codec, this);
        remote = new RemoteRelay(com.armsone.stand.BuildConfig.BOYISO_RELAY_URL,
                roomId, roomKey, sourceId, codec, this);
        scheduler = Executors.newSingleThreadScheduledExecutor();
        lan.startGuest();
        lan.startHost();
        if (hasBluetoothPermissions()) {
            ble.startGuest();
            ble.startHost();
        } else {
            latestError = "블루투스 권한 없이 Wi-Fi와 인터넷만 사용 중입니다";
        }
        scheduler.scheduleAtFixedRate(this::sendHeartbeat, 0, 5, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::expireStaleSources, 1, 2, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::maintainNearbyConnections, 5, 5, TimeUnit.SECONDS);
        remote.start();
        if (ROLE_GUEST.equals(role)) {
            startAudioCapture();
        }
        broadcastState();
    }

    private void acquireLocks() {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Boyiso:NearbyConnection");
        wakeLock.acquire();
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        multicastLock = wifiManager.createMulticastLock("BoyisoNsd");
        multicastLock.setReferenceCounted(false);
        multicastLock.acquire();
    }

    private boolean hasBluetoothPermissions() {
        if (Build.VERSION.SDK_INT < 31) return true;
        return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED;
    }

    private void sendHeartbeat() {
        if (!running) return;
        BoyisoEvent heartbeat = BoyisoEvent.heartbeat(sourceId, sourceName, role,
                audioRecord != null && audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING,
                batteryPercent(), displayMode, sessionActive);
        lan.sendFromGuest(heartbeat);
        ble.sendFromGuest(heartbeat);
        remote.send(heartbeat);
    }

    private void updateIdentity(Intent intent) {
        String requestedSourceName = intent.getStringExtra(EXTRA_SOURCE_NAME);
        if (!running || requestedSourceName == null || requestedSourceName.trim().isEmpty()) return;
        sourceName = requestedSourceName.trim();
        getSharedPreferences("boyiso", MODE_PRIVATE).edit().putString("device_name", sourceName).apply();
        sendHeartbeat();
        broadcastState();
    }

    private void sendTokTok() {
        long now = System.currentTimeMillis();
        if (!running) {
            Log.i(TAG, "톡톡 보내기 생략: 연결 꺼짐");
            return;
        }
        if (now - lastTokTokSentAt < 5_000) {
            Log.i(TAG, "톡톡 보내기 생략: 연속 누르기 보호");
            return;
        }
        lastTokTokSentAt = now;
        BoyisoEvent event = BoyisoEvent.tokTok(sourceId, sourceName, role, batteryPercent(),
                displayMode, sessionActive);
        lan.sendFromGuest(event);
        ble.sendFromGuest(event);
        remote.send(event);
        Log.i(TAG, "톡톡 보냄");
    }

    private void sendMovement() {
        if (!running) return;
        BoyisoEvent event = BoyisoEvent.movement(sourceId, sourceName, role, batteryPercent(),
                displayMode, sessionActive);
        sendEvent(event);
        Log.i(TAG, "뒤척임 보냄");
    }

    private void updateStandState(Intent intent) {
        displayMode = normalizedDisplayMode(intent.getStringExtra(EXTRA_DISPLAY_MODE));
        sessionActive = intent.getBooleanExtra(EXTRA_SESSION_ACTIVE, false);
        if (running) sendHeartbeat();
    }

    private String normalizedDisplayMode(String requested) {
        return BoyisoEvent.MODE_MATE.equals(requested) ? BoyisoEvent.MODE_MATE : BoyisoEvent.MODE_OBJECT;
    }

    private void sendEvent(BoyisoEvent event) {
        if (lan != null) lan.sendFromGuest(event);
        if (ble != null) ble.sendFromGuest(event);
        if (remote != null) remote.send(event);
    }

    private Integer batteryPercent() {
        BatteryManager manager = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
        int value = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        return value >= 0 && value <= 100 ? value : null;
    }

    private void maintainNearbyConnections() {
        if (!running) return;
        if (lan != null) lan.maintainConnections();
        if (ble != null && hasBluetoothPermissions()) ble.maintainConnections();
    }

    private void startAudioCapture() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            latestError = "마이크 권한이 없어 소리 감시를 시작하지 못했습니다";
            broadcastState();
            return;
        }
        int sampleRate = 16_000;
        int minimum = AudioRecord.getMinBufferSize(sampleRate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minimum <= 0) {
            latestError = "이 기기에서 마이크 입력을 준비하지 못했습니다";
            return;
        }
        int bufferSize = Math.max(minimum, 4_096);
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release();
            audioRecord = null;
            latestError = "마이크를 시작하지 못했습니다";
            return;
        }
        audioRecord.startRecording();
        audioThread = new Thread(() -> captureAudio(bufferSize), "BoyisoAudio");
        audioThread.start();
    }

    private void captureAudio(int bufferSizeBytes) {
        short[] samples = new short[Math.max(1_024, bufferSizeBytes / 2)];
        SoundEventDetector detector = new SoundEventDetector();
        while (running && audioRecord != null) {
            int count = audioRecord.read(samples, 0, samples.length, AudioRecord.READ_BLOCKING);
            if (count > 0) {
                detector.observe(samples, count, System.currentTimeMillis(), (detail, level) -> {
                    BoyisoEvent event = BoyisoEvent.sound(sourceId, sourceName, role, detail,
                            level / 100.0, batteryPercent(), displayMode, sessionActive);
                    sendEvent(event);
                    broadcastLocalDetection(event);
                });
            } else if (count < 0) {
                latestError = "마이크 입력이 중단되었습니다 (" + count + ")";
                broadcastState();
                break;
            }
        }
    }

    @Override public void onEvent(BoyisoEvent event, String path) {
        if (sourceId.equals(event.sourceId)) return;
        long now = System.currentTimeMillis();
        sourceLastSeen.put(event.sourceId, now);
        sourceLatest.put(event.sourceId, event);
        sourcePaths.computeIfAbsent(event.sourceId, ignored -> ConcurrentHashMap.newKeySet()).add(path);
        hadConnectedDevice = true;
        if (!deduplicator.accept(event.id, now)) return;
        if (BoyisoEvent.TOKTOK.equals(event.kind)) Log.i(TAG, "톡톡 받음: " + path);
        // Re-broadcast on every path, including the arrival transport, so a device between two
        // otherwise unreachable peers can relay the event. Source checks and event-ID deduplication
        // stop the echoes created by the bidirectional mesh.
        lan.sendFromGuest(event);
        ble.sendFromGuest(event);
        remote.send(event);
        if (!BoyisoEvent.HEARTBEAT.equals(event.kind)) broadcastReceivedEvent(event, path);
        broadcastState();
    }

    @Override public void onPathCount(String path, int count) {
        if ("LAN".equals(path)) lanCount = count;
        else if ("BLE".equals(path)) bleCount = count;
        else if ("INTERNET".equals(path)) internetCount = count;
        latestError = null;
        broadcastState();
    }

    @Override public void onTransportError(String path, String message) {
        latestError = path + ": " + (message == null ? "연결 오류" : message);
        broadcastState();
    }

    private void expireStaleSources() {
        long now = System.currentTimeMillis();
        boolean removed = sourceLastSeen.entrySet().removeIf(entry -> {
            boolean stale = now - entry.getValue() > STALE_MILLIS;
            if (stale) {
                sourceLatest.remove(entry.getKey());
                sourcePaths.remove(entry.getKey());
            }
            return stale;
        });
        if (removed) {
            broadcastState();
        }
    }

    private void broadcastReceivedEvent(BoyisoEvent event, String path) {
        Intent update = new Intent(ACTION_EVENT).setPackage(getPackageName());
        update.putExtra("sourceName", event.sourceName);
        update.putExtra("kind", event.kind);
        update.putExtra("detail", event.detail);
        update.putExtra("intensity", event.intensity == null ? 0.0 : event.intensity);
        update.putExtra("path", path);
        update.putExtra("timestamp", event.sentAtMilliseconds);
        sendBroadcast(update);
        if (BoyisoEvent.TOKTOK.equals(event.kind)) {
            if (!BoyisoManager.isAppVisible()) showTokTokNotification(event.sourceName);
        } else {
            updateNotification("소리 이벤트를 확인했습니다");
        }
    }

    private void broadcastLocalDetection(BoyisoEvent event) {
        Intent update = new Intent(ACTION_EVENT).setPackage(getPackageName());
        update.putExtra("sourceName", sourceName);
        update.putExtra("kind", event.kind);
        update.putExtra("detail", event.detail);
        update.putExtra("intensity", event.intensity == null ? 0.0 : event.intensity);
        update.putExtra("path", "이 기기");
        update.putExtra("timestamp", event.sentAtMilliseconds);
        sendBroadcast(update);
    }

    private void broadcastState() {
        Intent update = new Intent(ACTION_STATE).setPackage(getPackageName());
        update.putExtra("running", running);
        update.putExtra("role", role);
        update.putExtra("localSourceId", sourceId);
        update.putExtra("lanCount", lanCount);
        update.putExtra("bleCount", bleCount);
        update.putExtra("internetCount", internetCount);
        update.putExtra("hadConnectedDevice", hadConnectedDevice);
        update.putExtra("guestCount", sourceLastSeen.size());
        update.putExtra("monitoring", audioRecord != null
                && audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING);
        update.putExtra("error", latestError);
        java.util.ArrayList<String> sourceIds = new java.util.ArrayList<>();
        java.util.ArrayList<String> sourceNames = new java.util.ArrayList<>();
        java.util.ArrayList<String> sourceRoles = new java.util.ArrayList<>();
        java.util.ArrayList<String> sourceDisplayModes = new java.util.ArrayList<>();
        java.util.ArrayList<String> sourceTransportPaths = new java.util.ArrayList<>();
        java.util.ArrayList<Integer> sourceBatteries = new java.util.ArrayList<>();
        boolean[] sourceMonitoring = new boolean[sourceLatest.size()];
        boolean[] sourceSessionActive = new boolean[sourceLatest.size()];
        long[] sourceSeen = new long[sourceLatest.size()];
        java.util.ArrayList<BoyisoEvent> sources = new java.util.ArrayList<>(sourceLatest.values());
        sources.sort((left, right) -> left.sourceName.compareToIgnoreCase(right.sourceName));
        for (int index = 0; index < sources.size(); index++) {
            BoyisoEvent source = sources.get(index);
            sourceIds.add(source.sourceId);
            sourceNames.add(source.sourceName);
            sourceRoles.add(source.role);
            sourceDisplayModes.add(source.displayMode == null ? "" : source.displayMode);
            java.util.ArrayList<String> paths = new java.util.ArrayList<>(
                    sourcePaths.getOrDefault(source.sourceId, java.util.Collections.emptySet()));
            paths.sort(String::compareToIgnoreCase);
            sourceTransportPaths.add(String.join(",", paths));
            sourceBatteries.add(source.batteryPercent == null ? -1 : source.batteryPercent);
            sourceMonitoring[index] = source.monitoring;
            sourceSessionActive[index] = source.sessionActive;
            sourceSeen[index] = sourceLastSeen.getOrDefault(source.sourceId, 0L);
        }
        update.putStringArrayListExtra("sourceIds", sourceIds);
        update.putStringArrayListExtra("sourceNames", sourceNames);
        update.putStringArrayListExtra("sourceRoles", sourceRoles);
        update.putStringArrayListExtra("sourceDisplayModes", sourceDisplayModes);
        update.putStringArrayListExtra("sourceTransportPaths", sourceTransportPaths);
        update.putIntegerArrayListExtra("sourceBatteries", sourceBatteries);
        update.putExtra("sourceMonitoring", sourceMonitoring);
        update.putExtra("sourceSessionActive", sourceSessionActive);
        update.putExtra("sourceLastSeen", sourceSeen);
        sendBroadcast(update);
        updateNotification(null);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                getString(com.armsone.stand.R.string.boyiso_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(com.armsone.stand.R.string.boyiso_notification_channel_description));
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        NotificationChannel tokTok = new NotificationChannel(TOKTOK_CHANNEL_ID,
                "톡톡", NotificationManager.IMPORTANCE_HIGH);
        tokTok.setDescription("연결된 사람이 보내는 짧은 톡톡 인사를 알립니다.");
        tokTok.enableVibration(true);
        tokTok.setVibrationPattern(new long[]{0, 80, 70, 120});
        android.net.Uri tokTokSound = android.net.Uri.parse(
                "android.resource://" + getPackageName() + "/" +
                        com.armsone.stand.R.raw.boyiso_toktok);
        tokTok.setSound(tokTokSound, new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build());
        getSystemService(NotificationManager.class).createNotificationChannel(tokTok);
    }

    private Notification buildNotification() {
        return buildNotification(null);
    }

    private Notification buildNotification(String override) {
        Intent open = new Intent(this, com.armsone.stand.MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String text = override;
        if (text == null) {
            if (ROLE_GUEST.equals(role)) text = "말할 사람 기기에서 소리를 살피는 중";
            else {
                long speakerCount = sourceLatest.values().stream()
                        .filter(event -> ROLE_GUEST.equals(event.role)).count();
                text = speakerCount == 0 ? "말할 사람 기기 연결을 기다리는 중"
                        : speakerCount + "대의 말할 사람 기기를 살피는 중";
            }
        }
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
                .setContentTitle("보이소")
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pendingIntent)
                .build();
    }

    private void updateNotification(String text) {
        if (!running) return;
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, buildNotification(text));
    }

    private void showTokTokNotification(String senderName) {
        Intent open = new Intent(this, com.armsone.stand.MainActivity.class)
                .setData(android.net.Uri.parse("stand://boyiso"));
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 2, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, TOKTOK_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("톡톡")
                .setContentText(senderName + "에서 인사를 보냈어요")
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setContentIntent(pendingIntent)
                .build();
        getSystemService(NotificationManager.class).notify(
                4_200 + (int) (System.currentTimeMillis() % 100), notification);
    }

    private void stopMonitoring() {
        running = false;
        isActive = false;
        if (scheduler != null) scheduler.shutdownNow();
        if (audioRecord != null) {
            try { audioRecord.stop(); } catch (IllegalStateException ignored) { }
            audioRecord.release();
            audioRecord = null;
        }
        if (audioThread != null) {
            audioThread.interrupt();
            audioThread = null;
        }
        if (lan != null) { lan.stop(); lan = null; }
        if (ble != null) { ble.stop(); ble = null; }
        if (remote != null) { remote.stop(); remote = null; }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (multicastLock != null && multicastLock.isHeld()) multicastLock.release();
        sourceLastSeen.clear();
        sourceLatest.clear();
        sourcePaths.clear();
        lanCount = 0;
        bleCount = 0;
        internetCount = 0;
        hadConnectedDevice = false;
        broadcastState();
        stopForeground(STOP_FOREGROUND_REMOVE);
    }

    @Override public void onDestroy() {
        stopMonitoring();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
