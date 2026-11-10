package com.goreecloud.contacts

/** Short-lived, on-demand view of private notes. No persistent reveal flag. */
object PrivateNotes {
    const val MAX_NOTES = 8
    const val MAX_NOTE_CHARS = 4096

    fun hiddenLabel(notes: List<String>): String {
        require(notes.size <= MAX_NOTES) { "Too many private notes" }
        return if (notes.size == 1) "1 private note hidden"
            else notes.size.toString() + " private notes hidden"
    }

    /** Only invoke after the user explicitly opens the private-notes dialog. */
    fun reveal(notes: List<String>): String {
        require(notes.isNotEmpty() && notes.size <= MAX_NOTES) {
            "No valid private notes to reveal"
        }
        notes.forEach {
            require(it.isNotBlank() && it.length <= MAX_NOTE_CHARS) {
                "Invalid private note"
            }
        }
        return notes.joinToString("\n\n")
    }
}
