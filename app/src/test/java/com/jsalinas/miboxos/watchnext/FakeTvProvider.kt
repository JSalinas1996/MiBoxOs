package com.jsalinas.miboxos.watchnext

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.media.tv.TvContract.WatchNextPrograms as W
import android.net.Uri

/** Proveedor de TV falso con la fila "Watch Next" que publicarían Netflix y YouTube. */
class FakeTvProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, args: Array<String>?, sort: String?): Cursor {
        val cols = projection ?: ROWS.first().keys.toTypedArray()
        val cursor = MatrixCursor(cols)
        ROWS.forEach { row -> cursor.addRow(cols.map { row[it] }) }
        return cursor
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<String>?) = 0

    companion object {
        val ROWS = listOf(
            row(1, "com.netflix.ninja", "Stranger Things", "2", "5", "Dig Dug", 1_000, 50_000, 100_000, "intent://watch/80077368#Intent;scheme=nflx;package=com.netflix.ninja;end", 1),
            row(2, "com.google.android.youtube.tv", "Receta de pizza", null, null, null, 3_000, 30_000, 120_000, "https://www.youtube.com/watch?v=abc", 1),
            row(3, "com.netflix.ninja", "Oculta", null, null, null, 9_000, 0, 0, null, 0),
        )

        private fun row(
            id: Long, pkg: String, title: String, season: String?, episode: String?, episodeTitle: String?,
            engagement: Long, position: Long, duration: Long, intent: String?, browsable: Int,
        ) = mapOf(
            W._ID to id, W.COLUMN_PACKAGE_NAME to pkg, W.COLUMN_TITLE to title,
            W.COLUMN_SEASON_DISPLAY_NUMBER to season, W.COLUMN_EPISODE_DISPLAY_NUMBER to episode, W.COLUMN_EPISODE_TITLE to episodeTitle,
            W.COLUMN_POSTER_ART_URI to null, W.COLUMN_THUMBNAIL_URI to null, W.COLUMN_INTENT_URI to intent,
            W.COLUMN_LAST_PLAYBACK_POSITION_MILLIS to position, W.COLUMN_DURATION_MILLIS to duration,
            W.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS to engagement, W.COLUMN_BROWSABLE to browsable,
        )

        fun install() {
            org.robolectric.Robolectric.buildContentProvider(FakeTvProvider::class.java)
                .create(android.content.pm.ProviderInfo().apply { authority = "android.media.tv" })
        }
    }
}
