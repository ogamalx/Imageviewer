package com.ogamalx.imgviewer

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var txtInfo: TextView
    private lateinit var btnConvert: Button

    private var currentUri: Uri? = null
    private var sparseInfo: SparseImageInfo? = null
    private var pendingSparseUri: Uri? = null

    private val openFile = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            currentUri = uri
            analyze(uri)
        }
    }

    private val createRawDoc = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { outUri ->
        val srcUri = pendingSparseUri ?: return@registerForActivityResult
        if (outUri != null) {
            lifecycleScope.launch {
                btnConvert.isEnabled = false
                txtInfo.text = "Starting conversion..."

                val result = convertSparseToRawInternal(srcUri, outUri) { progressMessage ->
                    withContext(Dispatchers.Main) {
                        txtInfo.text = progressMessage
                    }
                }

                txtInfo.text = result
                btnConvert.isEnabled = true
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnOpen = findViewById<Button>(R.id.btnOpen)
        txtInfo = findViewById(R.id.txtInfo)
        btnConvert = findViewById(R.id.btnConvert)

        btnOpen.setOnClickListener {
            openFile.launch(arrayOf("*/*"))
        }

        btnConvert.setOnClickListener {
            val uri = currentUri ?: return@setOnClickListener
            pendingSparseUri = uri

            val df = DocumentFile.fromSingleUri(this, uri)
            val name = (df?.name ?: "image") + ".raw.img"
            createRawDoc.launch(name)
        }

        // Handle VIEW intents from file managers
        if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
            currentUri = intent.data
            analyze(intent.data!!)
        }
    }

    private fun analyze(uri: Uri) {
        val df = DocumentFile.fromSingleUri(this, uri)
        val name = df?.name ?: "unknown.img"
        val size = df?.length() ?: -1

        val header = readFirstBytes(uri, 28)
        if (header != null && SparseImageParser.isSparse(header)) {
            val info = SparseImageParser.parseHeader(header)
            sparseInfo = info
            val rawSize = info.blockSize.toLong() * info.totalBlocks.toLong()
            txtInfo.text = buildString {
                appendLine("File: $name")
                appendLine("Size: $size bytes")
                appendLine("Type: ANDROID SPARSE IMAGE")
                appendLine("Block size: ${info.blockSize}")
                appendLine("Total blocks: ${info.totalBlocks}")
                appendLine("Total chunks: ${info.totalChunks}")
                appendLine("Raw image size: $rawSize bytes")
            }
            btnConvert.visibility = Button.VISIBLE
        } else {
            txtInfo.text = buildString {
                appendLine("File: $name")
                appendLine("Size: $size bytes")
                appendLine("Type: RAW IMAGE (not sparse or unknown)")
            }
            btnConvert.visibility = Button.GONE
        }
    }

    private suspend fun convertSparseToRawInternal(
        src: Uri,
        outUri: Uri,
        onProgressUpdate: suspend (String) -> Unit
    ): String {
        return withContext(Dispatchers.IO) {
            val inputStream = contentResolver.openInputStream(src)
                ?: return@withContext "Error: Unable to open source file."

            try {
                inputStream.use { input ->
                    val outputStream = contentResolver.openOutputStream(outUri)
                        ?: return@use "Error: Unable to open destination file."
                    
                    outputStream.use { output ->
                        var lastUpdateTime = System.currentTimeMillis()
                        SparseImageParser.convertToRaw(input, output) { written ->
                            // Throttle progress updates to avoid excessive context switching
                            val now = System.currentTimeMillis()
                            if (now - lastUpdateTime >= 100) { // Update at most every 100ms
                                lastUpdateTime = now
                                onProgressUpdate("Writing RAW… $written bytes")
                            }
                        }
                    }
                    "Saved RAW image."
                }
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
        }
    }

    private fun readFirstBytes(uri: Uri, n: Int): ByteArray? {
        return try {
            contentResolver.openInputStream(uri)?.use { input ->
                val buf = ByteArray(n)
                val read = input.read(buf)
                if (read >= n) buf else null
            }
        } catch (e: Exception) {
            null
        }
    }
}

data class SparseImageInfo(
    val magic: Int,
    val major: Short,
    val minor: Short,
    val fileHeaderSize: Short,
    val chunkHeaderSize: Short,
    val blockSize: Int,
    val totalBlocks: Int,
    val totalChunks: Int,
    val imageChecksum: Int
)

object SparseImageParser {
    private const val SPARSE_MAGIC = 0xED26FF3A.toInt()

    fun isSparse(first28: ByteArray): Boolean {
        val bb = ByteBuffer.wrap(first28).order(ByteOrder.LITTLE_ENDIAN)
        val magic = bb.int
        return magic == SPARSE_MAGIC
    }

    fun parseHeader(first28: ByteArray): SparseImageInfo {
        val bb = ByteBuffer.wrap(first28).order(ByteOrder.LITTLE_ENDIAN)
        val magic = bb.int
        val major = bb.short
        val minor = bb.short
        val fileHeaderSize = bb.short
        val chunkHeaderSize = bb.short
        val blockSize = bb.int
        val totalBlocks = bb.int
        val totalChunks = bb.int
        val imageChecksum = bb.int
        return SparseImageInfo(
            magic,
            major,
            minor,
            fileHeaderSize,
            chunkHeaderSize,
            blockSize,
            totalBlocks,
            totalChunks,
            imageChecksum
        )
    }

    // Streaming sparse → raw converter.
    suspend fun convertToRaw(
        input: InputStream,
        output: java.io.OutputStream,
        progress: suspend (Long) -> Unit = {}
    ) {
        val header = ByteArray(28)
        if (input.read(header) != 28 || !isSparse(header)) {
            // Not sparse: just copy as-is
            val buf = ByteArray(1 shl 20)
            var total = 0L
            while (true) {
                val r = input.read(buf)
                if (r <= 0) break
                output.write(buf, 0, r)
                total += r
                progress(total)
            }
            return
        }

        val info = parseHeader(header)
        if (info.fileHeaderSize > 28) {
            input.skip((info.fileHeaderSize - 28).toLong())
        }

        val CHUNK_TYPE_RAW = 0xCAC1
        val CHUNK_TYPE_FILL = 0xCAC2
        val CHUNK_TYPE_DONT_CARE = 0xCAC3
        val CHUNK_TYPE_CRC32 = 0xCAC4

        val blockSize = info.blockSize
        val zeroBlock = ByteArray(blockSize)
        val hdr = ByteArray(info.chunkHeaderSize.toInt())
        val buf = ByteArray(1 shl 20)

        var written: Long = 0

        for (i in 0 until info.totalChunks) {
            if (input.read(hdr, 0, info.chunkHeaderSize.toInt()) != info.chunkHeaderSize.toInt()) break
            val bb = ByteBuffer.wrap(hdr).order(ByteOrder.LITTLE_ENDIAN)
            val chunkType = bb.short.toInt() and 0xFFFF
            bb.short // reserved
            val chunkBlocks = bb.int
            val chunkBytes = bb.int

            when (chunkType) {
                CHUNK_TYPE_RAW -> {
                    var remain = chunkBytes - info.chunkHeaderSize
                    val toWrite = chunkBlocks * blockSize
                    var copied = 0

                    while (remain > 0) {
                        val r = input.read(buf, 0, minOf(remain, buf.size))
                        if (r <= 0) break
                        output.write(buf, 0, r)
                        remain -= r
                        copied += r
                        written += r
                        progress(written)
                    }

                    val pad = toWrite - copied
                    if (pad > 0) {
                        output.write(ByteArray(pad))
                        written += pad
                        progress(written)
                    }
                }

                CHUNK_TYPE_FILL -> {
                    val fillBytes = ByteArray(4)
                    input.read(fillBytes)
                    val fillVal = ByteBuffer.wrap(fillBytes).order(ByteOrder.LITTLE_ENDIAN).int

                    val block = ByteArray(blockSize)
                    val fillBlock = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(fillVal).array()
                    var idx = 0
                    while (idx < block.size) {
                        val len = minOf(4, block.size - idx)
                        System.arraycopy(fillBlock, 0, block, idx, len)
                        idx += len
                    }

                    val totalBytes = chunkBlocks * blockSize
                    var left = totalBytes
                    while (left > 0) {
                        val w = minOf(left, block.size)
                        output.write(block, 0, w)
                        left -= w
                        written += w.toLong()
                        progress(written)
                    }
                }

                CHUNK_TYPE_DONT_CARE -> {
                    val totalBytes = chunkBlocks * blockSize
                    var left = totalBytes
                    while (left > 0) {
                        val w = minOf(left, zeroBlock.size)
                        output.write(zeroBlock, 0, w)
                        left -= w
                        written += w.toLong()
                        progress(written)
                    }
                }

                CHUNK_TYPE_CRC32 -> {
                    val toSkip = chunkBytes - info.chunkHeaderSize
                    if (toSkip > 0) {
                        input.skip(toSkip.toLong())
                    }
                }

                else -> {
                    val toSkip = chunkBytes - info.chunkHeaderSize
                    if (toSkip > 0) {
                        input.skip(toSkip.toLong())
                    }
                }
            }
        }
    }
}
