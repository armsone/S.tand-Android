package com.armsone.stand.boyiso;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

final class BoyisoEvent {
    static final String HEARTBEAT = "heartbeat";
    static final String SOUND = "sound";
    static final String MOVEMENT = "movement";
    static final String TOKTOK = "toktok";
    static final String DETAIL_BIG_SOUND = "big_sound";
    static final String DETAIL_CONTINUOUS_SOUND = "continuous_sound";
    static final String DETAIL_FINGER_SNAP = "finger_snap";
    static final String MODE_MATE = "mate";
    static final String MODE_OBJECT = "object";

    final String id;
    final String sourceId;
    final String sourceName;
    final String role;
    final String kind;
    final long sentAtMilliseconds;
    final Double intensity;
    final String detail;
    final boolean monitoring;
    final Integer batteryPercent;
    final String displayMode;
    final boolean sessionActive;

    BoyisoEvent(String id, String sourceId, String sourceName, String role, String kind,
                long sentAtMilliseconds, Double intensity, String detail,
                boolean monitoring, Integer batteryPercent, String displayMode,
                boolean sessionActive) {
        this.id = canonicalIdentifier(id);
        this.sourceId = canonicalIdentifier(sourceId);
        this.sourceName = sourceName;
        this.role = role;
        this.kind = kind;
        this.sentAtMilliseconds = sentAtMilliseconds;
        this.intensity = intensity;
        this.detail = detail;
        this.monitoring = monitoring;
        this.batteryPercent = batteryPercent;
        this.displayMode = displayMode;
        this.sessionActive = sessionActive;
    }

    /** UUID text is case-insensitive; retain legacy non-UUID identifiers exactly as stored. */
    static String canonicalIdentifier(String value) {
        String trimmed = value == null ? "" : value.trim();
        try {
            return UUID.fromString(trimmed).toString();
        } catch (IllegalArgumentException ignored) {
            return trimmed;
        }
    }

    static boolean sameIdentifier(String left, String right) {
        return canonicalIdentifier(left).equals(canonicalIdentifier(right));
    }

    static BoyisoEvent heartbeat(String sourceId, String sourceName, String role, boolean monitoring,
                                 Integer batteryPercent, String displayMode, boolean sessionActive) {
        return create(sourceId, sourceName, role, HEARTBEAT, null, null, monitoring, batteryPercent,
                displayMode, sessionActive);
    }

    static BoyisoEvent sound(String sourceId, String sourceName, String role, String detail,
                             double intensity, Integer batteryPercent, String displayMode,
                             boolean sessionActive) {
        return create(sourceId, sourceName, role, SOUND, intensity, detail, true, batteryPercent,
                displayMode, sessionActive);
    }

    static BoyisoEvent movement(String sourceId, String sourceName, String role,
                                Integer batteryPercent, String displayMode, boolean sessionActive) {
        return create(sourceId, sourceName, role, MOVEMENT, null, "turning", true, batteryPercent,
                displayMode, sessionActive);
    }

    static BoyisoEvent tokTok(String sourceId, String sourceName, String role, Integer batteryPercent,
                              String displayMode, boolean sessionActive) {
        return create(sourceId, sourceName, role, TOKTOK, null, "greeting", true, batteryPercent,
                displayMode, sessionActive);
    }

    private static BoyisoEvent create(String sourceId, String sourceName, String role, String kind,
                                      Double intensity, String detail, boolean monitoring,
                                      Integer batteryPercent) {
        return create(sourceId, sourceName, role, kind, intensity, detail, monitoring,
                batteryPercent, null, false);
    }

    private static BoyisoEvent create(String sourceId, String sourceName, String role, String kind,
                                      Double intensity, String detail, boolean monitoring,
                                      Integer batteryPercent, String displayMode,
                                      boolean sessionActive) {
        return new BoyisoEvent(UUID.randomUUID().toString(), sourceId, sourceName, role, kind,
                System.currentTimeMillis(), intensity, detail, monitoring, batteryPercent,
                displayMode, sessionActive);
    }

    byte[] encodeBytes() {
        JSONObject json = new JSONObject();
        try {
            json.put("version", 2);
            json.put("id", id);
            json.put("sourceID", sourceId);
            json.put("sourceName", sourceName);
            json.put("role", role);
            json.put("kind", kind);
            json.put("sentAtMilliseconds", sentAtMilliseconds);
            if (intensity != null) json.put("intensity", intensity);
            if (detail != null) json.put("detail", detail);
            json.put("monitoring", monitoring);
            if (batteryPercent != null) json.put("batteryPercent", batteryPercent);
            if (displayMode != null) json.put("displayMode", displayMode);
            json.put("sessionActive", sessionActive);
            return json.toString().getBytes(StandardCharsets.UTF_8);
        } catch (JSONException error) {
            throw new IllegalStateException("Unable to encode event", error);
        }
    }

    static BoyisoEvent decode(byte[] value) throws JSONException {
        JSONObject json = new JSONObject(new String(value, StandardCharsets.UTF_8));
        if (json.getInt("version") != 2) throw new JSONException("Unsupported Boyiso version");
        return new BoyisoEvent(
                json.getString("id"),
                json.getString("sourceID"),
                json.getString("sourceName"),
                json.getString("role"),
                json.getString("kind"),
                json.getLong("sentAtMilliseconds"),
                json.has("intensity") ? json.getDouble("intensity") : null,
                json.has("detail") ? json.getString("detail") : null,
                json.getBoolean("monitoring"),
                json.has("batteryPercent") ? json.getInt("batteryPercent") : null,
                json.has("displayMode") ? json.getString("displayMode") : null,
                json.optBoolean("sessionActive", false)
        );
    }
}
