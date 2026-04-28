package com.assistant.android.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Resolves a spoken name (English or Bangla) to a phone number from the user's contact book.
 *
 * Supports common Bangla/English aliases for family members so commands like
 * "ammu ke call koro" or "call mom" both work.
 */
object ContactResolver {

    private const val TAG = "ContactResolver"

    private val ALIASES: Map<String, List<String>> = mapOf(
        "mom" to listOf("ma", "amma", "ammu", "ammi", "mommy", "mother", "mum", "mama"),
        "dad" to listOf("baba", "abba", "abbu", "abbi", "abbas", "father", "papa", "papi", "daddy"),
        "brother" to listOf("bhai", "vai", "bhaiya", "bro", "bhaijaan"),
        "sister" to listOf("apu", "apa", "boon", "bon", "didi", "sis", "sista"),
        "uncle" to listOf("chacha", "mama", "kaka", "fufa", "khalu"),
        "aunt" to listOf("chachi", "mami", "khala", "fupu", "auntie", "aunty"),
        "wife" to listOf("bou", "begum", "wifey", "biwi"),
        "husband" to listOf("shami", "shamee", "swami", "hubby", "jamai"),
        "son" to listOf("chele", "beta"),
        "daughter" to listOf("meye", "beti"),
        "boss" to listOf("sir", "manager"),
        "friend" to listOf("dost", "bondhu", "buddy")
    )

    /** Returns true if string is already a phone number (digits / + only). */
    fun isPhoneNumber(s: String): Boolean {
        val cleaned = s.replace(" ", "").replace("-", "")
        if (cleaned.length < 4) return false
        return cleaned.matches(Regex("^[+]?[0-9]+$"))
    }

    /**
     * Find a phone number for the given name. Tries:
     *   1. Direct lookup in contacts (substring match, case-insensitive).
     *   2. Translate alias (e.g. "ammu" -> "mom") then re-search.
     */
    fun resolve(context: Context, query: String): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_CONTACTS not granted")
            return null
        }
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) return null

        // 1. Direct match
        lookup(context, normalized)?.let { return it }

        // 2. Try aliases
        val canonical = canonicalNameFor(normalized)
        if (canonical != null) {
            // Try the canonical English name and all aliases together
            (listOf(canonical) + (ALIASES[canonical] ?: emptyList())).forEach { alias ->
                lookup(context, alias)?.let { return it }
            }
        }
        return null
    }

    /** Maps "ammu" -> "mom", "abbu" -> "dad" etc. Null if no alias known. */
    fun canonicalNameFor(name: String): String? {
        val lower = name.lowercase().trim()
        if (ALIASES.containsKey(lower)) return lower
        for ((canonical, aliases) in ALIASES) {
            if (aliases.contains(lower)) return canonical
        }
        return null
    }

    private fun lookup(context: Context, name: String): String? {
        val cr = context.contentResolver
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val args = arrayOf("%$name%")
        return try {
            cr.query(uri, projection, selection, args, null)?.use { c ->
                if (c.moveToFirst()) {
                    val number = c.getString(c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                    val display = c.getString(c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                    Log.d(TAG, "Resolved \"$name\" -> $display ($number)")
                    number
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Contact lookup failed: ${e.message}")
            null
        }
    }
}
