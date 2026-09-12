package com.example.mysearchapp

import android.content.Context
import org.json.JSONObject

class ClipTokenizer(context: Context) {
    private val vocab = mutableMapOf<String, Int>()
    private val merges = mutableMapOf<Pair<String, String>, Int>()

    init {
        // 1. Load the Vocabulary mapping words to Integers
        val vocabStr = context.assets.open("vocab.json").bufferedReader().use { it.readText() }
        val vocabJson = JSONObject(vocabStr)
        vocabJson.keys().forEach { vocab[it] = vocabJson.getInt(it) }

        // 2. Load the Merge rules for Byte-Pair Encoding
        context.assets.open("merges.txt").bufferedReader().useLines { lines ->
            lines.drop(1).forEachIndexed { index, line ->
                val parts = line.split(" ")
                if (parts.size == 2) {
                    merges[parts[0] to parts[1]] = index
                }
            }
        }
    }

    fun encode(text: String): IntArray {
        // Clean and prepare the text
        val cleanText = text.lowercase().trim().replace(Regex("\\s+"), " ")
        val words = cleanText.split(" ")

        val tokens = mutableListOf(49406) // <|startoftext|>

        for (word in words) {
            if (word.isNotEmpty()) {
                tokens.addAll(bpe(word))
            }
        }

        tokens.add(49407) // <|endoftext|>

        // Pad to exactly 77 tokens for the ONNX model
        val result = IntArray(77) { 0 }
        for (i in 0 until minOf(tokens.size, 77)) {
            result[i] = tokens[i]
        }
        return result
    }

    private fun bpe(word: String): List<Int> {
        var chars = word.map { it.toString() }.toMutableList()
        if (chars.isNotEmpty()) {
            chars[chars.size - 1] = chars.last() + "</w>"
        }

        while (chars.size > 1) {
            var minRank = Int.MAX_VALUE
            var mergeTarget: Pair<String, String>? = null

            for (i in 0 until chars.size - 1) {
                val pair = chars[i] to chars[i + 1]
                val rank = merges[pair]
                if (rank != null && rank < minRank) {
                    minRank = rank
                    mergeTarget = pair
                }
            }

            if (mergeTarget == null) break

            val newChars = mutableListOf<String>()
            var i = 0
            while (i < chars.size) {
                if (i < chars.size - 1 && chars[i] == mergeTarget.first && chars[i + 1] == mergeTarget.second) {
                    newChars.add(chars[i] + chars[i + 1])
                    i += 2
                } else {
                    newChars.add(chars[i])
                    i++
                }
            }
            chars = newChars
        }

        return chars.mapNotNull { vocab[it] }
    }
}