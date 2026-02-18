package org.edrdg.jmdict.simplified.processing

import org.edrdg.jmdict.simplified.commands.DictionaryOutputWriter
import org.edrdg.jmdict.simplified.conversion.Converter
import org.edrdg.jmdict.simplified.conversion.OutputDictionaryEntry
import org.edrdg.jmdict.simplified.parsing.InputDictionaryEntry
import org.edrdg.jmdict.simplified.parsing.Metadata
import java.nio.file.Path

/**
 * Parses, analyzes, and converts to JSON a dictionary XML file.
 * Can produce a report file.
 */
open class ConvertingHandler<I : InputDictionaryEntry, O : OutputDictionaryEntry<O>, M : Metadata>(
    open val dictionaryName: String,
    open val version: String,
    open val languages: List<String>,
    open val outputDirectory: Path,
    open val outputs: List<DictionaryOutputWriter>,
    open val converter: Converter<I, O, M>,
) : EventHandler<I, M> {

    override fun onStart() {
        println("Output directory: $outputDirectory")
        println()
        println("Output files:")
        languages.forEach {
            println(" - $dictionaryName-$it-$version.json")
        }
        println()
    }

    override fun beforeEntries(metadata: M) {
        // Start JSON object with entryCount placeholder
        outputs.forEach { it.beginJsonObjectWithEntryCountPlaceholder() }
    }

    private val entriesByLanguage = mutableMapOf<String, Long>()
    private var entryCount: Long = 0L

    override fun onEntry(entry: I) {
        val word = converter.convert(entry)
        val relevantOutputs = outputs.filter { it.acceptsEntry(word) }

        entryCount += 1
        entry.allLanguages.forEach { lang ->
            entriesByLanguage.putIfAbsent(lang, 0L)
            entriesByLanguage.computeIfPresent(lang) { _, n -> n + 1L }
        }

        relevantOutputs.forEach { output ->
            val filteredWord = word.onlyWithLanguages(output.languages)
            val json = filteredWord.toJsonString()
            output.write("${if (output.acceptedAtLeastOneEntry) "," else ""}\n$json")
            output.acceptedAtLeastOneEntry = true
        }
    }

    override fun afterEntries() {
        outputs.forEach { out ->
            val lang = out.languages.first()
            val entries = if (lang == "all") entryCount else (entriesByLanguage[lang] ?: 0L)
            println("$lang has $entries entries.")

            // just close the array/object (no entryCount here anymore)
            out.write("\n] }")

            // patch entryCount at the top
            out.patchEntryCount(entries)
        }
    }

    override fun onFinish() {
        outputs.forEach { it.close() }
    }
}
