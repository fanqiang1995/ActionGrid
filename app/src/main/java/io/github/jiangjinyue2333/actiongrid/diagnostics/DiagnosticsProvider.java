package io.github.jiangjinyue2333.actiongrid.diagnostics;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

public final class DiagnosticsProvider extends ContentProvider {
    public static final String AUTHORITY = "io.github.jiangjinyue2333.actiongrid.diagnostics";
    public static final Uri LOG_URI = Uri.parse("content://" + AUTHORITY + "/" + LogStore.FILE_NAME);
    public static final String METHOD_APPEND = "append";
    public static final String EXTRA_SOURCE = "source";
    public static final String EXTRA_MESSAGE = "message";

    private static final String TARGET_PACKAGE = "com.miui.home";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (!isTrustedCaller()) {
            throw new SecurityException("Untrusted diagnostics caller");
        }
        if (METHOD_APPEND.equals(method) && extras != null) {
            LogStore.append(requireContext(), extras.getString(EXTRA_SOURCE, "hook"),
                    extras.getString(EXTRA_MESSAGE, ""));
            return Bundle.EMPTY;
        }
        throw new IllegalArgumentException("Unknown diagnostics method");
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode) || !LOG_URI.equals(uri)) {
            throw new FileNotFoundException("Unsupported diagnostics path");
        }
        File file = LogStore.file(requireContext());
        if (!file.isFile()) {
            throw new FileNotFoundException("No diagnostics file");
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    private boolean isTrustedCaller() {
        int uid = Binder.getCallingUid();
        if (uid == requireContext().getApplicationInfo().uid) {
            return true;
        }
        return TARGET_PACKAGE.equals(getCallingPackage());
    }

    @Override
    public String getType(Uri uri) {
        return LOG_URI.equals(uri) ? "text/plain" : null;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        if (!LOG_URI.equals(uri)) {
            return null;
        }
        String[] columns = projection == null
                ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}
                : projection;
        File file = LogStore.file(requireContext());
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        Object[] values = new Object[columns.length];
        for (int index = 0; index < columns.length; index++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[index])) {
                values[index] = LogStore.FILE_NAME;
            } else if (OpenableColumns.SIZE.equals(columns[index])) {
                values[index] = file.length();
            }
        }
        cursor.addRow(values);
        return cursor;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}
