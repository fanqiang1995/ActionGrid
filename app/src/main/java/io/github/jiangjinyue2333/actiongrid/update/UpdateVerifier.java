package io.github.jiangjinyue2333.actiongrid.update;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

final class UpdateVerifier {
    private UpdateVerifier() {
    }

    static String verify(Context context, UpdateManifest manifest, File apk) {
        try {
            if (!apk.isFile() || apk.length() != manifest.sizeBytes()) {
                return "size";
            }
            if (!manifest.sha256().equals(sha256(apk))) {
                return "sha256";
            }
            PackageManager manager = context.getPackageManager();
            PackageInfo archive = manager.getPackageArchiveInfo(apk.getAbsolutePath(),
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES));
            if (archive == null || !UpdateManifest.PACKAGE_NAME.equals(archive.packageName)) {
                return "package";
            }
            if (archive.getLongVersionCode() != manifest.versionCode()
                    || !manifest.versionName().equals(archive.versionName)) {
                return "version";
            }
            if (archive.applicationInfo == null || archive.applicationInfo.minSdkVersion != 34) {
                return "minSdk";
            }
            PackageInfo installed = manager.getPackageInfo(context.getPackageName(),
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES));
            if (archive.signingInfo == null || installed.signingInfo == null
                    || !sameSigners(installed.signingInfo.getApkContentsSigners(),
                    archive.signingInfo.getApkContentsSigners())) {
                return "signature";
            }
            return null;
        } catch (IOException | PackageManager.NameNotFoundException
                 | NoSuchAlgorithmException exception) {
            return exception.getClass().getSimpleName();
        }
    }

    private static boolean sameSigners(Signature[] first, Signature[] second)
            throws NoSuchAlgorithmException {
        if (first.length != second.length) {
            return false;
        }
        String[] left = signerDigests(first);
        String[] right = signerDigests(second);
        Arrays.sort(left);
        Arrays.sort(right);
        return Arrays.equals(left, right);
    }

    private static String[] signerDigests(Signature[] signatures) throws NoSuchAlgorithmException {
        String[] output = new String[signatures.length];
        for (int index = 0; index < signatures.length; index++) {
            output[index] = hex(MessageDigest.getInstance("SHA-256")
                    .digest(signatures[index].toByteArray()));
        }
        return output;
    }

    private static String sha256(File file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return hex(digest.digest());
    }

    private static String hex(byte[] bytes) {
        StringBuilder output = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            output.append(Character.forDigit((value >>> 4) & 0xF, 16));
            output.append(Character.forDigit(value & 0xF, 16));
        }
        return output.toString();
    }
}
