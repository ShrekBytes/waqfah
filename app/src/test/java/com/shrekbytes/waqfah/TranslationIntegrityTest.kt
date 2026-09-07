package com.shrekbytes.waqfah

import com.shrekbytes.waqfah.data.model.TranslationLanguage
import com.shrekbytes.waqfah.data.model.TranslationMeta
import com.shrekbytes.waqfah.data.repository.TranslationRepository
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

// Download hardening: integrity verification must fail closed — a downloadable
// catalog entry without a pinned checksum must never install unverified bytes
// of scripture — and the response body must not land unbounded before that
// verification runs.
class TranslationIntegrityTest {

    @Test
    fun matchingChecksum_passes() {
        val bytes = "verse text".toByteArray()
        val meta = downloadableMeta(checksum = sha256Hex(bytes))
        TranslationRepository.verifyChecksum(meta, sha256(bytes)) // must not throw
    }

    @Test
    fun mismatchedChecksum_throws() {
        val meta = downloadableMeta(checksum = sha256Hex("published bytes".toByteArray()))
        val thrown = assertThrows(IOException::class.java) {
            TranslationRepository.verifyChecksum(meta, sha256("tampered bytes".toByteArray()))
        }
        assertTrue(thrown.message!!.contains("integrity check"))
    }

    // The catalog's pin test keeps today's entries honest; this pins the code
    // path itself so a future unpinned entry can't silently download without
    // any byte verification.
    @Test
    fun missingChecksum_failsClosed() {
        val meta = downloadableMeta(checksum = null)
        val thrown = assertThrows(IllegalStateException::class.java) {
            TranslationRepository.verifyChecksum(meta, sha256("anything".toByteArray()))
        }
        assertTrue(thrown.message!!.contains("no pinned SHA-256"))
    }

    @Test
    fun declaredSizeOverLimit_rejectedWithoutReading() {
        val refusingInput = object : InputStream() {
            override fun read(): Int =
                throw AssertionError("body must not be read once the declared size is over the limit")
        }
        val thrown = assertThrows(IOException::class.java) {
            TranslationRepository.copyBodyCapped(
                input = refusingInput,
                output = ByteArrayOutputStream(),
                totalBytes = TranslationRepository.MAX_DOWNLOAD_BYTES + 1,
                onProgress = {},
            )
        }
        assertTrue(thrown.message!!.contains("limit"))
    }

    @Test
    fun bodyOverLimit_abortsBeforeEof() {
        val body = ByteArray(100_000) { (it % 251).toByte() }
        val output = ByteArrayOutputStream()
        val smallLimit = 16L * 1024
        assertThrows(IOException::class.java) {
            TranslationRepository.copyBodyCapped(
                input = ByteArrayInputStream(body),
                output = output,
                totalBytes = -1, // no declared length — only the read-loop guard applies
                maxBytes = smallLimit,
                onProgress = {},
            )
        }
        // Aborted mid-stream, not at EOF: what landed is bounded by one buffer
        // past the limit, and the caller deletes the tmp file on throw.
        assertTrue(output.size() < body.size)
        assertTrue(output.size() <= smallLimit + TranslationRepository.DOWNLOAD_BUFFER_BYTES)
    }

    @Test
    fun bodyUnderLimit_copiesFullyAndHashes() {
        val body = "verse text".toByteArray()
        val output = ByteArrayOutputStream()
        val digest = TranslationRepository.copyBodyCapped(
            input = ByteArrayInputStream(body),
            output = output,
            totalBytes = body.size.toLong(),
            onProgress = {},
        )
        assertArrayEquals(body, output.toByteArray())
        assertArrayEquals(sha256(body).digest(), digest.digest())
    }

    private fun downloadableMeta(checksum: String?) = TranslationMeta(
        id = "test",
        name = "Test",
        language = TranslationLanguage.ENGLISH,
        isBundled = false,
        downloadUrl = "https://example.invalid/en/test.db",
        checksumSha256 = checksum,
    )

    private fun sha256(bytes: ByteArray): MessageDigest =
        MessageDigest.getInstance("SHA-256").apply { update(bytes) }

    private fun sha256Hex(bytes: ByteArray): String =
        sha256(bytes).digest().joinToString("") { "%02x".format(it) }
}
