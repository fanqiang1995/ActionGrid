package io.github.jiangjinyue2333.actiongrid.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public final class UpdateManifestTest {
    private static final String VALID = """
            {
              "schemaVersion": 1,
              "packageName": "io.github.jiangjinyue2333.actiongrid",
              "versionCode": 2,
              "versionName": "1.1.0",
              "minSdk": 34,
              "apkUrl": "https://example.com/actiongrid.apk",
              "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
              "sizeBytes": 123,
              "publishedAt": "2026-08-22T12:00:00Z",
              "changelog": ["新增功能"]
            }
            """;

    @Test
    public void parsesStrictManifest() {
        UpdateManifest manifest = UpdateManifest.parse(VALID);
        assertEquals(2, manifest.versionCode());
        assertEquals("1.1.0", manifest.versionName());
        assertEquals(VALID_MANIFEST_SHA, manifest.sha256());
    }

    @Test
    public void rejectsNonHttpsDownload() {
        assertThrows(IllegalArgumentException.class,
                () -> UpdateManifest.parse(VALID.replace("https://", "http://")));
    }

    @Test
    public void rejectsWrongMinimumSdk() {
        assertThrows(IllegalArgumentException.class,
                () -> UpdateManifest.parse(VALID.replace("\"minSdk\": 34", "\"minSdk\": 33")));
    }

    @Test
    public void rejectsUnexpectedField() {
        assertThrows(IllegalArgumentException.class,
                () -> UpdateManifest.parse(VALID.replace("\"schemaVersion\": 1,",
                        "\"schemaVersion\": 1, \"forced\": true,")));
    }

    private static final String VALID_MANIFEST_SHA =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
}
