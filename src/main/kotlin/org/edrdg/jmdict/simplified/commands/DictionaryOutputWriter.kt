package org.edrdg.jmdict.simplified.commands

import org.edrdg.jmdict.simplified.conversion.OutputDictionaryEntry
import java.io.RandomAccessFile
import java.nio.file.Path

class DictionaryOutputWriter(
    path: Path,
    val languages: Set<String>,
    val commonOnly: Boolean,
) {
    private val raf = RandomAccessFile(path.toFile(), "rw").apply {
        setLength(0) // overwrite
    }

    private var entryCountOffset: Long? = null
    private var entryCountWidth: Int = 0

    fun beginJsonObjectWithEntryCountPlaceholder() {
        val width = ULong.MAX_VALUE.toString().length

        write("{\n\"entryCount\": ")
        entryCountOffset = raf.filePointer
        entryCountWidth = width
        write(" ".repeat(width))
        write(",\n")
    }

    fun patchEntryCount(value: Long) {
        val off = requireNotNull(entryCountOffset) {
            "entryCount placeholder wasn't initialized"
        }
        val width = entryCountWidth
        val s = value.toString()
        require(s.length <= width) {
            "entryCount '$s' is longer than placeholder width $width"
        }

        val tail = raf.filePointer
        raf.seek(off)
        raf.write(s.padEnd(width, ' ').toByteArray(Charsets.UTF_8)) // spaces are valid before comma
        raf.seek(tail)
    }

    fun write(text: String) {
        raf.write(text.toByteArray(Charsets.UTF_8))
    }

    fun close() {
        raf.close()
    }

    private var acceptedEntry = false

    var acceptedAtLeastOneEntry
        get() = acceptedEntry
        set(value) {
            if (!acceptedEntry && value) acceptedEntry = true
        }

    fun <O : OutputDictionaryEntry<O>> acceptsEntry(word: O): Boolean {
        val shareSomeLanguages = languages.intersect(word.allLanguages).isNotEmpty()
        // For non-only-common outputs, all words must be accepted
        // Otherwise, for only-common outputs, allow only common words
        val matchesByCommon = !commonOnly || word.isCommon
        return (shareSomeLanguages || languages.contains("all")) && matchesByCommon
    }
}
