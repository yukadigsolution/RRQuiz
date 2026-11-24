package com.yuka.rrquiz

/**
 * Pencocokan fuzzy string tanpa library luar.
 * Dipakai AutoAnswerRunner untuk mencocokkan teks soal di halaman kuis
 * dengan soal di bank soal, dan jawaban bank dengan teks opsi.
 *
 * similarity = 0.6 * levRatio + 0.4 * tokenJaccard
 * - levRatio: kemiripan urutan karakter (1 - jarak Levenshtein / panjang maks).
 *   Kuat untuk typo kecil dan perbedaan beberapa kata.
 * - tokenJaccard: kemiripan himpunan kata (irisan / gabungan).
 *   Kuat kalau kata-katanya sama tapi urutannya beda.
 * Ambang default 0.60: skor di bawah itu dianggap tidak cocok.
 */
object FuzzyMatcher {

    /** Lowercase, buang tanda baca, gabungkan spasi berlebih, trim. */
    fun normalize(s: String): String =
        s.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    /** Jarak edit Levenshtein (DP dua baris). */
    fun levenshtein(a: String, b: String): Int {
        val x = normalize(a)
        val y = normalize(b)
        if (x == y) return 0
        if (x.isEmpty()) return y.length
        if (y.isEmpty()) return x.length
        var prev = IntArray(y.length + 1) { it }
        var curr = IntArray(y.length + 1)
        for (i in 1..x.length) {
            curr[0] = i
            for (j in 1..y.length) {
                val cost = if (x[i - 1] == y[j - 1]) 0 else 1
                curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[y.length]
    }

    /** 1 - dist / max(len); 1.0 bila keduanya kosong. */
    fun levRatio(a: String, b: String): Double {
        val x = normalize(a)
        val y = normalize(b)
        val maxLen = maxOf(x.length, y.length)
        if (maxLen == 0) return 1.0
        return 1.0 - levenshtein(a, b).toDouble() / maxLen
    }

    /** Irisan / gabungan himpunan kata setelah normalize. */
    fun tokenJaccard(a: String, b: String): Double {
        val x = normalize(a).split(" ").filter { it.isNotEmpty() }.toSet()
        val y = normalize(b).split(" ").filter { it.isNotEmpty() }.toSet()
        if (x.isEmpty() && y.isEmpty()) return 1.0
        if (x.isEmpty() || y.isEmpty()) return 0.0
        return x.intersect(y).size.toDouble() / x.union(y).size
    }

    /** Skor gabungan 0.0-1.0. */
    fun similarity(a: String, b: String): Double =
        0.6 * levRatio(a, b) + 0.4 * tokenJaccard(a, b)

    /** Kandidat dengan skor tertinggi; null bila skor terbaik di bawah threshold. */
    fun bestMatch(query: String, candidates: List<String>, threshold: Double = 0.60): String? {
        val idx = bestMatchIndex(query, candidates, threshold)
        return if (idx >= 0) candidates[idx] else null
    }

    /** Indeks kandidat dengan skor tertinggi; -1 bila skor terbaik di bawah threshold. */
    fun bestMatchIndex(query: String, candidates: List<String>, threshold: Double = 0.60): Int {
        var bestIdx = -1
        var bestScore = 0.0
        for ((i, c) in candidates.withIndex()) {
            val s = similarity(query, c)
            if (s > bestScore) {
                bestScore = s
                bestIdx = i
            }
        }
        return if (bestScore >= threshold) bestIdx else -1
    }
}
