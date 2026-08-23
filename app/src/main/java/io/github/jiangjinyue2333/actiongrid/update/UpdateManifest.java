package io.github.jiangjinyue2333.actiongrid.update;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record UpdateManifest(
        int versionCode,
        String versionName,
        String apkUrl,
        String sha256,
        long sizeBytes,
        Instant publishedAt,
        List<String> changelog
) {
    public static final String PACKAGE_NAME = "io.github.jiangjinyue2333.actiongrid";
    private static final Set<String> FIELDS = Set.of(
            "schemaVersion", "packageName", "versionCode", "versionName", "minSdk",
            "apkUrl", "sha256", "sizeBytes", "publishedAt", "changelog");

    public UpdateManifest {
        changelog = List.copyOf(changelog);
    }

    public static UpdateManifest parse(String json) {
        try {
            JSONObject object = new JSONObject(json);
            requireExactFields(object);
            requireInteger(object, "schemaVersion", 1);
            requireString(object, "packageName", PACKAGE_NAME);
            requireInteger(object, "minSdk", 34);
            int versionCode = positiveInt(object, "versionCode");
            String versionName = nonEmptyString(object, "versionName");
            String apkUrl = nonEmptyString(object, "apkUrl");
            URI uri = URI.create(apkUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw new IllegalArgumentException("apkUrl must be HTTPS");
            }
            String sha256 = nonEmptyString(object, "sha256");
            if (!sha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("sha256 must be lowercase hex");
            }
            long sizeBytes = positiveLong(object, "sizeBytes");
            Instant publishedAt = Instant.parse(nonEmptyString(object, "publishedAt"));
            JSONArray entries = requireType(object, "changelog", JSONArray.class);
            List<String> changelog = new ArrayList<>(entries.length());
            for (int index = 0; index < entries.length(); index++) {
                Object entry = entries.get(index);
                if (!(entry instanceof String text) || text.isBlank()) {
                    throw new IllegalArgumentException("Invalid changelog entry");
                }
                changelog.add(text);
            }
            return new UpdateManifest(versionCode, versionName, apkUrl, sha256, sizeBytes,
                    publishedAt, changelog);
        } catch (JSONException | DateTimeParseException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid update manifest", exception);
        }
    }

    public String toJson() {
        try {
            JSONObject object = new JSONObject()
                    .put("schemaVersion", 1)
                    .put("packageName", PACKAGE_NAME)
                    .put("versionCode", versionCode)
                    .put("versionName", versionName)
                    .put("minSdk", 34)
                    .put("apkUrl", apkUrl)
                    .put("sha256", sha256)
                    .put("sizeBytes", sizeBytes)
                    .put("publishedAt", publishedAt.toString())
                    .put("changelog", new JSONArray(changelog));
            return object.toString();
        } catch (JSONException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void requireExactFields(JSONObject object) throws JSONException {
        Set<String> names = new HashSet<>();
        JSONArray array = object.names();
        if (array != null) {
            for (int index = 0; index < array.length(); index++) {
                names.add(array.getString(index));
            }
        }
        if (!names.equals(FIELDS)) {
            throw new IllegalArgumentException("Unexpected manifest fields");
        }
    }

    private static void requireInteger(JSONObject object, String key, int expected)
            throws JSONException {
        if (positiveInt(object, key) != expected) {
            throw new IllegalArgumentException("Unexpected " + key);
        }
    }

    private static int positiveInt(JSONObject object, String key) throws JSONException {
        Object value = object.get(key);
        if (!(value instanceof Integer integer) || integer <= 0) {
            throw new IllegalArgumentException(key + " must be a positive integer");
        }
        return integer;
    }

    private static long positiveLong(JSONObject object, String key) throws JSONException {
        Object value = object.get(key);
        if (!(value instanceof Number number) || value instanceof Double || value instanceof Float
                || number.longValue() <= 0) {
            throw new IllegalArgumentException(key + " must be a positive integer");
        }
        return number.longValue();
    }

    private static void requireString(JSONObject object, String key, String expected)
            throws JSONException {
        if (!expected.equals(nonEmptyString(object, key))) {
            throw new IllegalArgumentException("Unexpected " + key);
        }
    }

    private static String nonEmptyString(JSONObject object, String key) throws JSONException {
        Object value = object.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(key + " must be a non-empty string");
        }
        return text;
    }

    private static <T> T requireType(JSONObject object, String key, Class<T> type)
            throws JSONException {
        Object value = object.get(key);
        if (!type.isInstance(value)) {
            throw new IllegalArgumentException("Unexpected type for " + key);
        }
        return type.cast(value);
    }
}
