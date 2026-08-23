package io.github.jiangjinyue2333.actiongrid.update;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public final class UpdateFileProvider extends ContentProvider {
    public static final String AUTHORITY = "io.github.jiangjinyue2333.actiongrid.updates";

    public static Uri uriFor(File file) {
        return new Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendPath(file.getName())
                .build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) {
            throw new FileNotFoundException("Updates are read-only");
        }
        File update = updateFile(uri);
        if (update == null) {
            throw new FileNotFoundException("Update file is unavailable");
        }
        return ParcelFileDescriptor.open(update, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    private File updateFile(Uri uri) {
        if (!AUTHORITY.equals(uri.getAuthority()) || uri.getPathSegments().size() != 1) {
            return null;
        }
        try {
            File downloadRoot = requireContext()
                    .getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);
            if (downloadRoot == null) {
                return null;
            }
            File root = downloadRoot.getCanonicalFile();
            File requested = new File(root, uri.getLastPathSegment()).getCanonicalFile();
            if (!requested.toPath().startsWith(root.toPath()) || !requested.isFile()) {
                return null;
            }
            return requested;
        } catch (IOException exception) {
            return null;
        }
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        String[] columns = projection == null
                ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}
                : projection;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        File update = updateFile(uri);
        if (update == null) {
            return cursor;
        }
        Object[] values = new Object[columns.length];
        for (int index = 0; index < columns.length; index++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[index])) {
                values[index] = update.getName();
            } else if (OpenableColumns.SIZE.equals(columns[index])) {
                values[index] = update.length();
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
