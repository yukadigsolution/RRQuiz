package com.yuka.rrquiz

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Demo pengerjaan otomatis untuk halaman kuis Stok Akhir (halaman latihan milik sendiri).
 *
 * Alur: baca soal + opsi dari DOM halaman yang sedang tampil (evaluateJavascript),
 * cocokan tiap soal dengan bank soal lewat FuzzyMatcher (bukan pencocokan persis —
 * toleran terhadap perbedaan kata/typo), cocokan jawaban bank dengan teks opsi,
 * lalu panggil fungsi global halaman pick(i, oi) untuk menjawab. Pindah halaman
 * dengan goPage(p+1) sampai halaman terakhir. TIDAK PERNAH klik "Finish attempt"
 * / submit — berhenti setelah halaman terakhir selesai dijawab.
 *
 * Semua evaluateJavascript jalan di UI thread dan berurutan lewat callback +
 * Handler.postDelayed (bukan Thread.sleep di UI thread). Kalau struktur .que
 * tidak ditemukan, onNotQuizPage dipanggil dan proses berhenti.
 */
data class AutoResult(val total: Int, val answered: Int, val unmatched: List<Int>) {
    val percent: Int get() = if (total == 0) 0 else (answered * 100 / total)
}

private data class PageOption(val idx: Int, val text: String)

private data class PageQuestion(
    val index: Int,
    val text: String,
    val answered: Boolean,
    val options: List<PageOption>
)

class AutoAnswerRunner(
    private val activity: Activity,
    private val webView: WebView,
    private val dbHelper: BankSoalDbHelper,
    private val onFinished: (AutoResult) -> Unit,
    private val onNotQuizPage: () -> Unit
) {

    companion object {
        private const val QUESTION_THRESHOLD = 0.60
        private const val ANSWER_THRESHOLD = 0.60
        private const val ANSWER_DELAY_MS = 250L
        private const val PAGE_DELAY_MS = 400L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var bank: List<BankSoalRow> = emptyList()
    private var total = 0
    private var answered = 0
    private val unmatched = mutableListOf<Int>()

    /**
     * Baca halaman kuis yang sedang tampil. Kembalikan string JSON:
     * {error:false, page, totalPages, questions:[{i, q, answered, o:[{idx, text}]}]}
     * atau {error:true} bila tidak ada kartu .que (bukan halaman kuis Stok Akhir).
     * Teks soal dibersihkan dari label "Question text", teks opsi dari awalan "A. ".
     */
    private val jsExtract = """
        (function(){
          var cards = document.querySelectorAll('.que');
          if (!cards.length) return JSON.stringify({error:true});
          var title = document.querySelector('.pagetitle');
          var m = title ? title.textContent.match(/page\s+(\d+)\s+of\s+(\d+)/i) : null;
          var page = m ? parseInt(m[1], 10) - 1 : 0;
          var totalPages = m ? parseInt(m[2], 10) : 1;
          var out = [];
          cards.forEach(function(card){
            var idm = (card.id || '').match(/question-(\d+)/);
            var i = idm ? parseInt(idm[1], 10) : -1;
            var qt = card.querySelector('.qtext');
            var q = qt ? qt.innerText.replace(/^Question text\s*/, '').trim() : '';
            var answered = !!card.querySelector('input[type=radio]:checked');
            var opts = [];
            card.querySelectorAll('.answer label').forEach(function(l){
              var inp = l.querySelector('input[type=radio]');
              var sp = l.querySelector('span');
              var t = sp ? sp.innerText.replace(/^[A-E]\.\s*/, '').trim() : l.innerText.trim();
              opts.push({idx: inp ? parseInt(inp.value, 10) : opts.length, text: t});
            });
            out.push({i: i, q: q, answered: answered, o: opts});
          });
          return JSON.stringify({error:false, page:page, totalPages:totalPages, questions:out});
        })()
    """.trimIndent()

    fun start() {
        if (running) return
        running = true
        bank = dbHelper.getAll()
        extractCurrentPage(first = true)
    }

    fun cancel() {
        running = false
        handler.removeCallbacksAndMessages(null)
    }

    /** Jalankan JS di WebView (UI thread), hasil callback juga di UI thread. */
    private fun eval(js: String, cb: (String?) -> Unit) {
        if (!running) return
        if (activity.isFinishing || activity.isDestroyed) {
            cancel()
            return
        }
        activity.runOnUiThread {
            if (!running) return@runOnUiThread
            try {
                webView.evaluateJavascript(js) { value ->
                    if (running) cb(value)
                }
            } catch (e: Exception) {
                // WebView sudah dihancurkan
                cancel()
            }
        }
    }

    /** evaluateJavascript mengembalikan nilai sebagai string JSON-encoded; buka bungkusnya. */
    private fun parseResult(value: String?): JSONObject? {
        if (value.isNullOrBlank() || value == "null") return null
        return try {
            when (val inner = JSONTokener(value).nextValue()) {
                is String -> JSONObject(inner)
                is JSONObject -> inner
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseQuestions(json: JSONObject): List<PageQuestion> {
        val list = mutableListOf<PageQuestion>()
        val arr = json.optJSONArray("questions") ?: return list
        for (k in 0 until arr.length()) {
            val o = arr.optJSONObject(k) ?: continue
            val opts = mutableListOf<PageOption>()
            val oarr = o.optJSONArray("o")
            if (oarr != null) {
                for (j in 0 until oarr.length()) {
                    val oo = oarr.optJSONObject(j) ?: continue
                    opts.add(PageOption(oo.optInt("idx", j), oo.optString("text", "")))
                }
            }
            list.add(
                PageQuestion(
                    o.optInt("i", -1),
                    o.optString("q", ""),
                    o.optBoolean("answered", false),
                    opts
                )
            )
        }
        return list
    }

    private fun extractCurrentPage(first: Boolean = false) {
        eval(jsExtract) { value ->
            val json = parseResult(value)
            if (json == null || json.optBoolean("error", true)) {
                // Bukan halaman kuis Stok Akhir (belum ada soal diproses) atau
                // DOM berubah di tengah jalan (selesaikan dengan yang sudah ada).
                if (total == 0) {
                    running = false
                    onNotQuizPage()
                } else {
                    finish()
                }
                return@eval
            }
            val page = json.optInt("page", 0)
            val totalPages = json.optInt("totalPages", 1)
            if (first && page != 0) {
                // Mulai selalu dari halaman 1 sesuai spek.
                eval("goPage(0);") {
                    handler.postDelayed({ extractCurrentPage(first = false) }, PAGE_DELAY_MS)
                }
                return@eval
            }
            val questions = parseQuestions(json)
            if (questions.isEmpty()) {
                finish()
                return@eval
            }
            total += questions.size
            processQuestions(questions, 0, page, totalPages)
        }
    }

    private fun processQuestions(
        qs: List<PageQuestion>,
        idx: Int,
        page: Int,
        totalPages: Int
    ) {
        if (!running) return
        if (idx >= qs.size) {
            // Halaman selesai: lanjut halaman berikut, atau selesai total.
            if (page + 1 < totalPages) {
                eval("goPage(${page + 1});") {
                    handler.postDelayed({ extractCurrentPage() }, PAGE_DELAY_MS)
                }
            } else {
                finish()
            }
            return
        }
        val q = qs[idx]
        if (q.answered) {
            // Sudah dijawab manual sebelumnya: lewati, tidak dihitung tidak cocok.
            handler.postDelayed({ processQuestions(qs, idx + 1, page, totalPages) }, ANSWER_DELAY_MS)
            return
        }
        val qIdx = FuzzyMatcher.bestMatchIndex(
            q.text,
            bank.map { it.soal },
            QUESTION_THRESHOLD
        )
        var picked = false
        if (qIdx >= 0) {
            val answerText = bank[qIdx].jawaban
            val optIdx = FuzzyMatcher.bestMatchIndex(
                answerText,
                q.options.map { it.text },
                ANSWER_THRESHOLD
            )
            if (optIdx >= 0) {
                picked = true
                answered++
                eval("pick(${q.index}, ${q.options[optIdx].idx});") { }
            }
        }
        if (!picked) unmatched.add(q.index + 1) // nomor soal 1-based untuk tampilan
        handler.postDelayed({ processQuestions(qs, idx + 1, page, totalPages) }, ANSWER_DELAY_MS)
    }

    private fun finish() {
        if (!running) return
        running = false
        handler.removeCallbacksAndMessages(null)
        onFinished(AutoResult(total, answered, unmatched.sorted()))
    }
}
