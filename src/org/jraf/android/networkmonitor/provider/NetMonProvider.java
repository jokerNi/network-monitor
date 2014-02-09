/*
 * This source is part of the
 *      _____  ___   ____
 *  __ / / _ \/ _ | / __/___  _______ _
 * / // / , _/ __ |/ _/_/ _ \/ __/ _ `/
 * \___/_/|_/_/ |_/_/ (_)___/_/  \_, /
 *                              /___/
 * repository.
 *
 * Copyright (C) 2013 Carmen Alvarez (c@rmen.ca)
 * Copyright (C) 2013 Benoit 'BoD' Lubek (BoD@JRAF.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jraf.android.networkmonitor.provider;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentValues;
import android.content.OperationApplicationException;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQuery;
import android.net.Uri;
import android.provider.BaseColumns;

import org.jraf.android.networkmonitor.Constants;
import org.jraf.android.networkmonitor.util.Log;

public class NetMonProvider extends ContentProvider { // NO_UCD (use default)
    private static final String TAG = Constants.TAG + NetMonProvider.class.getSimpleName();

    private static final String TYPE_CURSOR_ITEM = "vnd.android.cursor.item/";
    private static final String TYPE_CURSOR_DIR = "vnd.android.cursor.dir/";

    public static final String AUTHORITY = "org.jraf.android.networkmonitor.provider";
    static final String CONTENT_URI_BASE = "content://" + AUTHORITY;

    public static final String QUERY_NOTIFY = "QUERY_NOTIFY";
    private static final String QUERY_GROUP_BY = "QUERY_GROUP_BY";

    private static final int URI_TYPE_NETWORKMONITOR = 0;
    private static final int URI_TYPE_NETWORKMONITOR_ID = 1;
    private static final int URI_TYPE_SUMMARY = 2;

    private static final UriMatcher URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        URI_MATCHER.addURI(AUTHORITY, NetMonColumns.TABLE_NAME, URI_TYPE_NETWORKMONITOR);
        URI_MATCHER.addURI(AUTHORITY, NetMonColumns.TABLE_NAME + "/#", URI_TYPE_NETWORKMONITOR_ID);
        URI_MATCHER.addURI(AUTHORITY, ConnectionTestStatsColumns.VIEW_NAME, URI_TYPE_SUMMARY);
    }

    private NetMonDatabase mNetworkMonitorDatabase;

    @Override
    public boolean onCreate() {
        mNetworkMonitorDatabase = new NetMonDatabase(getContext());
        return true;
    }

    @Override
    public String getType(Uri uri) {
        final int match = URI_MATCHER.match(uri);
        switch (match) {
            case URI_TYPE_NETWORKMONITOR:
            case URI_TYPE_SUMMARY:
                return TYPE_CURSOR_DIR + NetMonColumns.TABLE_NAME;
            case URI_TYPE_NETWORKMONITOR_ID:
                return TYPE_CURSOR_ITEM + NetMonColumns.TABLE_NAME;
        }
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        Log.d(TAG, "insert uri=" + uri + " values=" + values);
        final String table = uri.getLastPathSegment();
        final long rowId = mNetworkMonitorDatabase.getWritableDatabase().insert(table, null, values);
        String notify;
        if (rowId != -1 && ((notify = uri.getQueryParameter(QUERY_NOTIFY)) == null || "true".equals(notify))) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return uri.buildUpon().appendEncodedPath(String.valueOf(rowId)).build();
    }

    @Override
    public int bulkInsert(Uri uri, ContentValues[] values) {
        Log.d(TAG, "bulkInsert uri=" + uri + " values.length=" + values.length);
        final String table = uri.getLastPathSegment();
        final SQLiteDatabase db = mNetworkMonitorDatabase.getWritableDatabase();
        int res = 0;
        db.beginTransaction();
        try {
            for (final ContentValues v : values) {
                final long id = db.insert(table, null, v);
                if (id != -1) {
                    res++;
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        String notify;
        if (res != 0 && ((notify = uri.getQueryParameter(QUERY_NOTIFY)) == null || "true".equals(notify))) {
            getContext().getContentResolver().notifyChange(uri, null);
        }

        return res;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        Log.d(TAG, "update uri=" + uri + " values=" + values + " selection=" + selection);
        final QueryParams queryParams = getQueryParams(uri, selection, false);
        final int res = mNetworkMonitorDatabase.getWritableDatabase().update(queryParams.table, values, queryParams.whereClause, selectionArgs);
        String notify;
        if (res != 0 && ((notify = uri.getQueryParameter(QUERY_NOTIFY)) == null || "true".equals(notify))) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return res;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        Log.d(TAG, "delete uri=" + uri + " selection=" + selection);
        final QueryParams queryParams = getQueryParams(uri, selection, false);
        final int res = mNetworkMonitorDatabase.getWritableDatabase().delete(queryParams.table, queryParams.whereClause, selectionArgs);
        String notify;
        if (res != 0 && ((notify = uri.getQueryParameter(QUERY_NOTIFY)) == null || "true".equals(notify))) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return res;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        final String groupBy = uri.getQueryParameter(QUERY_GROUP_BY);
        Log.d(TAG,
                "query uri=" + uri + ", projection = " + Arrays.toString(projection) + ", selection=" + selection + ", selectionArgs = "
                        + Arrays.toString(selectionArgs) + ", sortOrder=" + sortOrder + ", groupBy=" + groupBy);

        final int matchedId = URI_MATCHER.match(uri);
        final Cursor res;
        switch (matchedId) {
            case URI_TYPE_NETWORKMONITOR:
            case URI_TYPE_NETWORKMONITOR_ID:

                final QueryParams queryParams = getQueryParams(uri, selection, true);
                res = mNetworkMonitorDatabase.getReadableDatabase().query(queryParams.table, projection, queryParams.whereClause, selectionArgs, groupBy, null,
                        sortOrder == null ? queryParams.orderBy : sortOrder);
                break;
            case URI_TYPE_SUMMARY:
                res = mNetworkMonitorDatabase.getReadableDatabase().query(ConnectionTestStatsColumns.VIEW_NAME, projection, selection, selectionArgs, groupBy,
                        null, sortOrder);
                break;
            default:
                return null;
        }
        res.setNotificationUri(getContext().getContentResolver(), uri);
        logCursor(res, selectionArgs);
        return res;
    }

    /**
     * Perform all operations in a single transaction and notify all relevant URIs at the end. The {@link MemberStatsColumns#CONTENT_URI} uri is always notified
     * for a successful transaction.
     * 
     * @see android.content.ContentProvider#applyBatch(java.util.ArrayList)
     */
    @Override
    public ContentProviderResult[] applyBatch(ArrayList<ContentProviderOperation> operations) throws OperationApplicationException {
        Log.v(TAG, "applyBatch: " + operations);
        Set<Uri> urisToNotify = new HashSet<Uri>();
        for (ContentProviderOperation operation : operations)
            urisToNotify.add(operation.getUri());
        Log.v(TAG, "applyBatch: will notify these uris after persisting: " + urisToNotify);
        SQLiteDatabase db = mNetworkMonitorDatabase.getWritableDatabase();
        db.beginTransaction();
        try {
            ContentProviderResult[] result = super.applyBatch(operations);
            db.setTransactionSuccessful();
            for (Uri uri : urisToNotify)
                getContext().getContentResolver().notifyChange(uri, null);
            return result;
        } finally {
            db.endTransaction();
        }
    }

    private static class QueryParams {
        public String table;
        public String whereClause;
        public String orderBy;
    }


    private QueryParams getQueryParams(Uri uri, String selection, boolean isQuery) {
        final QueryParams res = new QueryParams();
        String id = null;
        final int matchedId = URI_MATCHER.match(uri);
        switch (matchedId) {
            case URI_TYPE_NETWORKMONITOR:
            case URI_TYPE_NETWORKMONITOR_ID:
                res.table = NetMonColumns.TABLE_NAME;
                res.orderBy = NetMonColumns.DEFAULT_ORDER;
                break;
            case URI_TYPE_SUMMARY:
                // Nothing to do here.  We will construct our query params in query().
                break;
            default:
                throw new IllegalArgumentException("The uri '" + uri + "' is not supported by this ContentProvider");
        }

        switch (matchedId) {
            case URI_TYPE_NETWORKMONITOR_ID:
                id = uri.getLastPathSegment();
        }
        if (id != null) {
            if (selection != null) {
                res.whereClause = BaseColumns._ID + "=" + id + " and (" + selection + ")";
            } else {
                res.whereClause = BaseColumns._ID + "=" + id;
            }
        } else {
            res.whereClause = selection;
        }
        return res;
    }

    /**
     * Log the query of the given cursor.
     * 
     * @param cursor
     * @param selectionArgs
     */
    private void logCursor(Cursor cursor, String[] selectionArgs) {
        try {
            Field queryField = SQLiteCursor.class.getDeclaredField("mQuery");
            queryField.setAccessible(true);
            SQLiteQuery sqliteQuery = (SQLiteQuery) queryField.get(cursor);
            Log.v(TAG, sqliteQuery.toString() + ": " + Arrays.toString(selectionArgs));
        } catch (Exception e) {
            Log.v(TAG, e.getMessage(), e);
        }
    }
}
