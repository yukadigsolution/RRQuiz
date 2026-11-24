package com.yuka.rrquiz

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bank soal lokal aplikasi: SQLite rrquiz.db, tabel bank_soal.
 * Dipakai Settings untuk menyimpan hasil import Excel, dan dibaca
 * halaman WebView lewat JS bridge RRQuizBank.
 */
data class BankSoalRow(val id: Long, val soal: String, val jawaban: String)

class BankSoalDbHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "rrquiz.db"
        const val DATABASE_VERSION = 1
        const val TABLE_NAME = "bank_soal"
        const val COL_ID = "id"
        const val COL_SOAL = "soal"
        const val COL_JAWABAN = "jawaban"

        fun escapeSql(value: String): String = value.replace("'", "''")
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $TABLE_NAME " +
                "($COL_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "$COL_SOAL TEXT NOT NULL, $COL_JAWABAN TEXT NOT NULL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    /** Simpan banyak baris sekaligus dalam satu transaksi. Kembalikan jumlah yang tersimpan. */
    fun insertAll(rows: List<Pair<String, String>>): Int {
        if (rows.isEmpty()) return 0
        val db = writableDatabase
        var count = 0
        db.beginTransaction()
        try {
            for ((soal, jawaban) in rows) {
                val values = ContentValues().apply {
                    put(COL_SOAL, soal)
                    put(COL_JAWABAN, jawaban)
                }
                if (db.insert(TABLE_NAME, null, values) != -1L) count++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return count
    }

    fun getAll(): List<BankSoalRow> {
        val result = mutableListOf<BankSoalRow>()
        readableDatabase.query(
            TABLE_NAME,
            arrayOf(COL_ID, COL_SOAL, COL_JAWABAN),
            null, null, null, null,
            "$COL_ID ASC"
        ).use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(COL_ID)
            val soalIdx = cursor.getColumnIndexOrThrow(COL_SOAL)
            val jawabanIdx = cursor.getColumnIndexOrThrow(COL_JAWABAN)
            while (cursor.moveToNext()) {
                result.add(
                    BankSoalRow(
                        cursor.getLong(idIdx),
                        cursor.getString(soalIdx),
                        cursor.getString(jawabanIdx)
                    )
                )
            }
        }
        return result
    }

    fun count(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE_NAME", null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    fun clearAll() {
        writableDatabase.delete(TABLE_NAME, null, null)
    }

    /** Seluruh isi tabel sebagai SQL: CREATE TABLE + INSERT per baris (kutip tunggal di-escape). */
    fun toSql(): String {
        val sb = StringBuilder()
        sb.append("CREATE TABLE IF NOT EXISTS $TABLE_NAME ")
        sb.append("($COL_ID INTEGER PRIMARY KEY, $COL_SOAL TEXT, $COL_JAWABAN TEXT);\n")
        for (row in getAll()) {
            sb.append("INSERT INTO $TABLE_NAME ($COL_SOAL, $COL_JAWABAN) VALUES (")
            sb.append("'").append(escapeSql(row.soal)).append("', ")
            sb.append("'").append(escapeSql(row.jawaban)).append("');\n")
        }
        return sb.toString()
    }

    /** Seluruh isi tabel sebagai JSON array [{soal, jawaban}]. */
    fun toJson(): String {
        val arr = JSONArray()
        for (row in getAll()) {
            arr.put(
                JSONObject().apply {
                    put(COL_SOAL, row.soal)
                    put(COL_JAWABAN, row.jawaban)
                }
            )
        }
        return arr.toString()
    }
}
