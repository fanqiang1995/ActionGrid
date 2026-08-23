package io.github.jiangjinyue2333.actiongrid.update;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.HttpsURLConnection;

public final class UpdateClient {
    private static final String MANIFEST_URL =
            "https://api.gitcode.com/api/v5/repos/jinyue66/actiongrid-releases/raw/latest.json?ref=main";
    private static final int MAX_MANIFEST_BYTES = 256 * 1024;

    private UpdateClient() {
    }

    public static UpdateManifest fetch() throws IOException {
        HttpsURLConnection connection = (HttpsURLConnection) new URL(MANIFEST_URL).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "ActionGrid-Android");
        try {
            if (connection.getResponseCode() != HttpsURLConnection.HTTP_OK) {
                throw new IOException("Unexpected HTTP response");
            }
            try (InputStream input = connection.getInputStream()) {
                byte[] bytes = input.readNBytes(MAX_MANIFEST_BYTES + 1);
                if (bytes.length > MAX_MANIFEST_BYTES) {
                    throw new IOException("Manifest is too large");
                }
                return UpdateManifest.parse(new String(bytes, StandardCharsets.UTF_8));
            }
        } finally {
            connection.disconnect();
        }
    }
}
