package dev.shrekbytes.waqfah

import dev.shrekbytes.waqfah.data.model.ArabicFont
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every character a Quran font is asked to draw must exist in that font's
 * glyph set. Quranic text with a codepoint the font lacks renders as tofu,
 * or — worse — falls through to whatever Arabic font the device has and
 * misplaces diacritics over sacred text. (Characters the layout engine never
 * sends to a font — bidi controls and zero-width format marks — are exempt;
 * see [ignoredByFonts].)
 *
 * Three guarantees here:
 *
 * 1. **Coverage.** For each script's verse-text column in the bundled
 *    `quran_core.db`, every codepoint the font must draw is present in the
 *    cmap of the one font that renders it (`ArabicFont` pairs them, and the
 *    pairing is read from there so it can't drift).
 * 2. **Format.** Both fonts are TrueType (`glyf`) outlines. The Indopak font
 *    originally shipped as static CFF2/OTF, which failed to load on a Realme
 *    C12 (Android 11) — the whole font, silently, with the ayah falling back
 *    to the device system font. TrueType loads on every supported Android.
 * 3. **Completeness.** Every font file under `res/font` is claimed by an
 *    `ArabicFont` entry, so a font dropped in without being wired up fails
 *    here instead of shipping unverified.
 *
 * This test reads the shipped font binaries and the shipped Quran database
 * directly from the module, so it re-checks the real pairing on every build —
 * a re-sourced font or an updated text can't quietly drop a codepoint.
 */
class QuranFontCoverageTest {

    @Test
    fun `every bundled font covers every codepoint its script column renders`() {
        ArabicFont.entries.forEach { font ->
            val file = fontFileName(font)
            val column = "arabic_${font.script.name.lowercase()}"
            assertCovers(
                cmapOf("res/font/$file"),
                codepointsInColumn(column),
                "$file (the ${font.script.name.lowercase()} font)",
            )
        }
    }

    @Test
    fun `every bundled font is trueType outlines`() {
        ArabicFont.entries.forEach { font ->
            assertTrue(
                "${fontFileName(font)} must be TrueType (glyf); CFF/CFF2 fonts fail " +
                    "to load on some OEM Android builds and fall back to the system font",
                isTrueType("res/font/${fontFileName(font)}"),
            )
        }
    }

    @Test
    fun `every font file in res-font is claimed by an ArabicFont entry`() {
        val expected = ArabicFont.entries.map { fontFileName(it) }.toSet()
        val shipped = modulePath("res/font").listFiles()!!.map { it.name }.toSet()
        assertEquals(
            "font files under res/font and ArabicFont entries disagree — a new font " +
                "must be added to ArabicFont (which pairs it with a script) too",
            expected,
            shipped,
        )
    }

    /** Font files are named after their enum entry: DIGITAL_KHATT_INDOPAK → digital_khatt_indopak.ttf. */
    private fun fontFileName(font: ArabicFont): String = "${font.name.lowercase()}.ttf"

    private fun assertCovers(font: Set<Int>, column: Set<Int>, label: String) {
        val missing = (column - font) - ignoredByFonts
        assertEquals(
            "$label cannot render: " +
                missing.sorted().joinToString { "U+%04X %s".format(it, unicodeName(it)) },
            emptySet<Int>(),
            missing,
        )
    }

    private fun unicodeName(codepoint: Int): String =
        try {
            Character.getName(codepoint)
        } catch (_: IllegalArgumentException) {
            "<unnamed>"
        }

    private fun isTrueType(relative: String): Boolean {
        val version = String(modulePath(relative).readBytes(), 0, 4, Charsets.ISO_8859_1)
        return version == "\u0000\u0001\u0000\u0000" || version == "true"
    }

    // ---------------------------------------------------------------- sfnt

    /**
     * The set of codepoints with a non-zero glyph in any of the font's cmap
     * subtables. Handles format 4 (segments, including idRangeOffset
     * indirection) and format 12 (groups) — the shapes real fonts use.
     */
    private fun cmapOf(relative: String): Set<Int> {
        val bytes = modulePath(relative).readBytes()
        val numTables = beShort(bytes, 4)
        var cmapOffset = -1
        var record = 12
        repeat(numTables) {
            if (String(bytes, record, 4, Charsets.ISO_8859_1) == "cmap") {
                cmapOffset = beInt(bytes, record + 8)
            }
            record += 16
        }
        require(cmapOffset >= 0) { "no cmap table in $relative" }

        val codepoints = mutableSetOf<Int>()
        val numSubtables = beShort(bytes, cmapOffset + 2)
        var subtable = cmapOffset + 4
        repeat(numSubtables) {
            val offset = cmapOffset + beInt(bytes, subtable + 4)
            when (beShort(bytes, offset)) {
                4 -> readCmapFormat4(bytes, offset, codepoints)
                12 -> readCmapFormat12(bytes, offset, codepoints)
                else -> Unit // other formats are not used by either font
            }
            subtable += 8
        }
        return codepoints
    }

    private fun readCmapFormat4(bytes: ByteArray, table: Int, out: MutableSet<Int>) {
        val segCount = beShort(bytes, table + 6) / 2
        val endCodes = table + 14
        val startCodes = endCodes + 2 * segCount + 2 // past endCodes and reservedPad
        val idDelta = startCodes + 2 * segCount
        val idRangeOffset = idDelta + 2 * segCount
        for (segment in 0 until segCount) {
            val start = beShort(bytes, startCodes + 2 * segment)
            val end = beShort(bytes, endCodes + 2 * segment)
            if (start == 0xFFFF) continue // sentinel terminator segment
            val delta = beShort(bytes, idDelta + 2 * segment).toShort().toInt()
            val rangeOffset = beShort(bytes, idRangeOffset + 2 * segment)
            var codepoint = start
            while (codepoint <= end) {
                val glyphId = if (rangeOffset == 0) {
                    (codepoint + delta) and 0xFFFF
                } else {
                    val glyphIdAt = idRangeOffset + 2 * segment + rangeOffset + 2 * (codepoint - start)
                    val id = beShort(bytes, glyphIdAt)
                    if (id == 0) 0 else (id + delta) and 0xFFFF
                }
                if (glyphId != 0) out += codepoint
                codepoint++
            }
        }
    }

    private fun readCmapFormat12(bytes: ByteArray, table: Int, out: MutableSet<Int>) {
        val numGroups = beInt(bytes, table + 12)
        var group = table + 16
        repeat(numGroups) {
            val start = beInt(bytes, group)
            val end = beInt(bytes, group + 4)
            require(end >= start && end <= 0x10FFFF) { "implausible cmap group in font" }
            for (codepoint in start..end) out += codepoint
            group += 12
        }
    }

    // -------------------------------------------------------------- sqlite

    /**
     * Codepoints used by one text column of the bundled Quran text. Reads the
     * sqlite file directly — the DB is a checked-in asset, and this test runs
     * on the JVM where Room's Android drivers don't work.
     */
    private fun codepointsInColumn(column: String): Set<Int> {
        val bytes = modulePath("assets/databases/quran_core.db").readBytes()
        require(String(bytes, 0, 15, Charsets.US_ASCII) == "SQLite format 3") {
            "quran_core.db is not a sqlite 3 database"
        }
        require(beInt(bytes, 56) == 1) { "expected a UTF-8 database" }
        val pageSize = beShort(bytes, 16).let { if (it == 1) 65536 else it }
        val usableSize = pageSize - (bytes[20].toInt() and 0xFF)

        var versesRootPage = -1
        var versesSql = ""
        walkTableBTree(bytes, page1Offset = 100, pageSize = pageSize, usableSize = usableSize) { row ->
            if (row.getOrNull(0) == "table" && row.getOrNull(1) == "verses") {
                versesRootPage = (row[3] as Number).toInt()
                versesSql = row[4] as String
            }
        }
        require(versesRootPage > 0) { "no verses table in quran_core.db" }

        val columns = createTableColumns(versesSql)
        val columnIndex = columns.indexOf(column)
        require(columnIndex >= 0) {
            "verses table has no '$column' column; found ${columns.joinToString()}"
        }

        val codepoints = mutableSetOf<Int>()
        walkTableBTree(
            bytes,
            page1Offset = 0,
            pageSize = pageSize,
            usableSize = usableSize,
            rootPage = versesRootPage,
        ) { row ->
            val text = row[columnIndex] as? String ?: return@walkTableBTree
            text.codePoints().forEach { codepoints.add(it) }
        }
        require(codepoints.isNotEmpty()) { "no text read from the $column column" }
        return codepoints
    }

    /**
     * Visits every row of a table b-tree in page order: children of interior
     * pages (type 5) left to right, then the right-most pointer; rows of leaf
     * pages (type 13). Record values arrive as their sqlite types
     * (Long, Double, String, ByteArray, or null).
     */
    private fun walkTableBTree(
        bytes: ByteArray,
        page1Offset: Int,
        pageSize: Int,
        usableSize: Int,
        rootPage: Int = 1,
        onRow: (List<Any?>) -> Unit,
    ) {
        val pageBase = (rootPage - 1) * pageSize
        val pageStart = pageBase + if (rootPage == 1) page1Offset else 0
        val cellCount = beShort(bytes, pageStart + 3)
        when (bytes[pageStart].toInt()) {
            13 -> { // leaf table page: cells are rows
                for (cell in 0 until cellCount) {
                    // cell pointers are offsets within the page, page 1 included
                    val cellOffset = beShort(bytes, pageStart + 8 + 2 * cell)
                    val cursor = Cursor(bytes, pageBase + cellOffset)
                    val payloadSize = cursor.varint().toInt()
                    cursor.varint() // rowid — unused
                    onRow(readRecord(bytes, cursor, payloadSize, pageSize, usableSize))
                }
            }
            5 -> { // interior table page: cells are child page numbers
                for (cell in 0 until cellCount) {
                    val cellOffset = beShort(bytes, pageStart + 12 + 2 * cell)
                    val childPage = beInt(bytes, pageBase + cellOffset)
                    walkTableBTree(bytes, page1Offset, pageSize, usableSize, childPage, onRow)
                }
                val rightMost = beInt(bytes, pageStart + 8)
                walkTableBTree(bytes, page1Offset, pageSize, usableSize, rightMost, onRow)
            }
            else -> error("unexpected b-tree page type ${bytes[pageStart].toInt()} in quran_core.db")
        }
    }

    /**
     * One sqlite record: a header of serial types followed by the values.
     */
    private fun readRecord(
        bytes: ByteArray,
        cursor: Cursor,
        payloadSize: Int,
        pageSize: Int,
        usableSize: Int,
    ): List<Any?> {
        val record = Cursor(readPayload(bytes, cursor, payloadSize, pageSize, usableSize), 0)
        val headerStart = record.position
        val headerSize = record.varint().toInt()
        require(headerSize in 1..payloadSize) { "implausible record header size $headerSize" }
        val headerEnd = headerStart + headerSize
        val serialTypes = mutableListOf<Long>()
        while (record.position < headerEnd) serialTypes += record.varint()
        require(record.position == headerEnd) { "record header overruns its declared size" }

        val values = mutableListOf<Any?>()
        for (serialType in serialTypes) {
            when {
                serialType == 0L -> values += null
                serialType == 8L -> values += 0L
                serialType == 9L -> values += 1L
                serialType == 7L -> values += Double.fromBits(record.signed(8))
                serialType in 1..6 -> values += record.signed(serialTypeToLength(serialType))
                serialType >= 13 && serialType % 2 == 1L ->
                    values += String(record.take(serialTypeToLength(serialType)), Charsets.UTF_8)
                serialType >= 12 -> { // blob — not needed for verse text
                    record.take(serialTypeToLength(serialType))
                    values += null
                }
                else -> error("unsupported sqlite serial type $serialType")
            }
        }
        return values
    }

    /**
     * A cell's payload as contiguous bytes. Rows that fit their page are read
     * in one piece; longer rows — the longest ayat (e.g. Al-Baqarah 282)
     * overflow — keep a local prefix and continue down the overflow-page
     * chain, which this stitches back together.
     */
    private fun readPayload(
        bytes: ByteArray,
        cursor: Cursor,
        payloadSize: Int,
        pageSize: Int,
        usableSize: Int,
    ): ByteArray {
        val maxLocal = usableSize - 35
        if (payloadSize <= maxLocal) return cursor.take(payloadSize)
        val minLocal = (usableSize - 12) * 32 / 255 - 23
        val candidate = minLocal + (payloadSize - minLocal) % (usableSize - 4)
        val localSize = if (candidate <= maxLocal) candidate else minLocal
        val payload = ByteArray(payloadSize)
        cursor.take(localSize).copyInto(payload, 0)
        var nextPage = beInt(bytes, cursor.position)
        cursor.position += 4
        var filled = localSize
        while (nextPage != 0 && filled < payloadSize) {
            val pageBase = (nextPage - 1) * pageSize
            nextPage = beInt(bytes, pageBase)
            val chunk = minOf(usableSize - 4, payloadSize - filled)
            bytes.copyInto(payload, filled, pageBase + 4, pageBase + 4 + chunk)
            filled += chunk
        }
        check(filled == payloadSize) { "overflow chain ended before the row was complete" }
        return payload
    }

    private fun serialTypeToLength(serialType: Long): Int = when (serialType.toInt()) {
        1 -> 1
        2 -> 2
        3 -> 3
        4 -> 4
        5 -> 6
        6, 7 -> 8
        else -> {
            val isText = serialType >= 13 && serialType % 2 == 1L
            ((serialType - if (isText) 13 else 12) / 2).toInt()
        }
    }

    /**
     * Codepoints the text pipeline consumes without ever asking the font for a
     * glyph: bidi controls and zero-width format marks. They shape how the
     * verse is laid out but never draw, so a font that lacks them still
     * displays the verse correctly — the Indopak column wraps one ayah number
     * in U+202E and pads 1,707 verses with combining grapheme joiner. Only
     * marks this app's own text can carry belong here; a longer list would
     * quietly exempt more than the layout engine actually swallows.
     */
    private val ignoredByFonts = setOf(
        0x00AD, // soft hyphen
        0x034F, // combining grapheme joiner
        0x061C, // arabic letter mark
        0x200B, 0x200C, // zero-width space and non-joiner
        0x200E, 0x200F, // left/right-to-left marks
        0x202A, 0x202B, 0x202C, 0x202D, 0x202E, // bidi embeddings and overrides
        0x2066, 0x2067, 0x2068, 0x2069, // bidi isolates
        0x206A, 0x206B, 0x206C, 0x206D, 0x206E, 0x206F, // deprecated format characters
        0xFEFF, // zero-width no-break space
    )

    /** Column names from a CREATE TABLE statement, in order. */
    private fun createTableColumns(sql: String): List<String> {
        val open = sql.indexOf('(')
        val close = sql.lastIndexOf(')')
        require(open in 0 until close) { "unexpected CREATE TABLE: $sql" }
        val constraintKeywords = setOf("primary", "unique", "check", "foreign", "constraint", "key")
        return splitTopLevel(sql.substring(open + 1, close)).mapNotNull { definition ->
            val identifier = definition.trim()
                .takeWhile { !it.isWhitespace() }
                .trim('"', '`', '[', ']', '\'')
            identifier.takeIf { it.isNotEmpty() && it.lowercase() !in constraintKeywords }
        }
    }

    /** Splits on commas that aren't nested in parentheses or quotes. */
    private fun splitTopLevel(text: String): List<String> {
        val parts = mutableListOf<String>()
        var depth = 0
        var quoted = false
        val current = StringBuilder()
        for (char in text) {
            when {
                char == '\'' -> {
                    quoted = !quoted
                    current.append(char)
                }
                !quoted && char == '(' -> {
                    depth++
                    current.append(char)
                }
                !quoted && char == ')' -> {
                    depth--
                    current.append(char)
                }
                !quoted && depth == 0 && char == ',' -> {
                    parts += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
        }
        parts += current.toString()
        return parts
    }

    private class Cursor(private val bytes: ByteArray, var position: Int) {
        fun take(length: Int): ByteArray {
            val slice = bytes.copyOfRange(position, position + length)
            position += length
            return slice
        }

        fun signed(length: Int): Long {
            var value = 0L
            for (byte in take(length)) value = (value shl 8) or (byte.toLong() and 0xFF)
            val shift = 64 - length * 8
            return if (shift in 1..63) (value shl shift) shr shift else value
        }

        /** sqlite varint: big-endian 7-bit groups; the 9th byte holds 8 bits. */
        fun varint(): Long {
            var value = 0L
            for (index in 0 until 8) {
                val byte = bytes[position++].toLong() and 0xFF
                value = (value shl 7) or (byte and 0x7F)
                if (byte and 0x80 == 0L) return value
            }
            return (value shl 8) or (bytes[position++].toLong() and 0xFF)
        }
    }

    private fun beShort(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

    private fun beInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    /** Locates a path under `app/src/main` whether the test runs from the repo or module dir. */
    private fun modulePath(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir"))
        while (dir != null) {
            val candidate = File(dir, "app/src/main/$relative")
            if (candidate.exists()) return candidate
            dir = dir.parentFile
        }
        error("could not locate app/src/main/$relative from ${System.getProperty("user.dir")}")
    }
}
