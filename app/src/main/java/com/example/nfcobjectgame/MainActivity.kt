package com.example.nfcobjectgame

import android.app.Activity
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import android.graphics.Color
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import java.nio.charset.Charset
import java.util.Locale

class MainActivity : AppCompatActivity(), NfcAdapter.ReaderCallback {

    private var nfcAdapter: NfcAdapter? = null
    private lateinit var statusText: TextView
    private lateinit var tagInfoText: TextView
    private lateinit var cancelWriteButton: Button
    private lateinit var inventoryText: TextView
    private lateinit var copyButton: Button
    private lateinit var writeCopyButton: Button
    private lateinit var viewDataButton: Button
    private lateinit var loadFileButton: Button
    private lateinit var saveFileButton: Button
    private lateinit var copyStatusText: TextView

    private val inventory = mutableSetOf<String>()
    private var capturedMessage: NdefMessage? = null
    private var capturedRawData: ByteArray? = null
    private var capturedRawTech: String? = null
    private var captureMode = false
    private var writeCopyMode = false

    private val openFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { loadBrickFromFile(it) }
    }

    private val saveFileLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
        uri?.let { saveBrickToFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        tagInfoText = findViewById(R.id.tagInfoText)
        cancelWriteButton = findViewById(R.id.cancelWriteButton)
        inventoryText = findViewById(R.id.inventoryText)
        copyButton = findViewById(R.id.copyButton)
        writeCopyButton = findViewById(R.id.writeCopyButton)
        viewDataButton = findViewById(R.id.viewDataButton)
        loadFileButton = findViewById(R.id.loadFileButton)
        saveFileButton = findViewById(R.id.saveFileButton)
        copyStatusText = findViewById(R.id.copyStatusText)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        if (nfcAdapter == null) {
            statusText.text = "This device has no NFC adapter"
            return
        }

        copyButton.setOnClickListener {
            captureMode = true
            statusText.text = "Copy mode: scan source tag"
            cancelWriteButton.visibility = Button.VISIBLE
        }

        writeCopyButton.setOnClickListener {
            if (capturedMessage != null || capturedRawData != null) {
                writeCopyMode = true
                statusText.text = "Clone mode: scan target tag"
                cancelWriteButton.visibility = Button.VISIBLE
            }
        }

        viewDataButton.setOnClickListener {
            showCapturedData()
        }

        loadFileButton.setOnClickListener {
            openFileLauncher.launch(arrayOf("text/plain"))
        }

        saveFileButton.setOnClickListener {
            saveFileLauncher.launch("SmartBrickDump.txt")
        }

        cancelWriteButton.setOnClickListener {
            captureMode = false
            writeCopyMode = false
            statusText.text = "Ready to scan an NFC tag"
            statusText.setTextColor(Color.BLACK) // Reset color
            cancelWriteButton.visibility = Button.GONE
        }
    }

    override fun onResume() {
        super.onResume()

        nfcAdapter?.enableReaderMode(
            this,
            this,
            NfcAdapter.FLAG_READER_NFC_V or
                    NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F,
            null
        )
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
    }

    override fun onTagDiscovered(tag: Tag) {
        if (captureMode) {
            captureTag(tag)
        } else if (writeCopyMode) {
            writeCapturedMessageToTag(tag)
        } else {
            readTag(tag)
        }
    }

    private fun captureTag(tag: Tag) {
        val ndef = Ndef.get(tag)
        var success = false
        var message: String
        
        // Reset old captures
        capturedMessage = null
        capturedRawData = null
        capturedRawTech = null

        if (ndef != null) {
            try {
                ndef.connect()
                capturedMessage = ndef.ndefMessage
                success = capturedMessage != null
                message = if (success) "NDEF captured (${capturedMessage!!.toByteArray().size} bytes)"
                else "Tag is NDEF formatted but empty."
            } catch (e: Exception) {
                message = "NDEF Capture failed: ${e.message}"
            } finally {
                ndef.close()
            }
        } else {
            // Fallback: Try Raw Technology Read (e.g. NfcV for SLIX2)
            if (tag.techList.contains("android.nfc.tech.NfcV")) {
                val nfcV = android.nfc.tech.NfcV.get(tag)
                try {
                    nfcV.connect()
                    
                    // Better ISO 15693 System Info Parsing
                    var numBlocks = 66
                    try {
                        val sysInfo = nfcV.transceive(byteArrayOf(0x02.toByte(), 0x2B.toByte()))
                        if (sysInfo != null && sysInfo[0] == 0.toByte()) {
                            val infoFlags = sysInfo[1].toInt()
                            var offset = 10 // Status(1) + Flags(1) + UID(8)
                            if ((infoFlags and 0x01) != 0) offset++ // DSFID present
                            if ((infoFlags and 0x02) != 0) offset++ // AFI present
                            
                            // If Memory Size bit is set (Bit 3)
                            if ((infoFlags and 0x04) != 0 && sysInfo.size > offset) {
                                numBlocks = (sysInfo[offset].toInt() and 0xFF) + 1
                            }
                        }
                    } catch (e: Exception) {
                        numBlocks = 128 
                    }

                    val out = java.io.ByteArrayOutputStream()
                    var blocksRead = 0
                    for (i in 0 until numBlocks) {
                        try {
                            // Command 0x20 is "Read Single Block"
                            val response = nfcV.transceive(byteArrayOf(0x02.toByte(), 0x20.toByte(), i.toByte()))
                            if (response != null && response[0] == 0.toByte()) {
                                out.write(response, 1, response.size - 1)
                                blocksRead++
                            } else break
                        } catch (e: Exception) {
                            break
                        }
                    }
                    capturedRawData = out.toByteArray()
                    capturedRawTech = "NfcV"
                    success = capturedRawData!!.isNotEmpty()
                    message = "Raw NfcV captured ($blocksRead blocks, ${capturedRawData!!.size} bytes)"
                } catch (e: Exception) {
                    message = "Raw Capture failed: ${e.message}"
                } finally {
                    nfcV.close()
                }
            } else {
                message = "Tag is not NDEF and doesn't support NfcV raw reading."
            }
        }

        runOnUiThread {
            captureMode = false
            statusText.text = if (success) "Tag Copied" else "Copy Failed"
            copyStatusText.text = message
            writeCopyButton.isEnabled = success
            viewDataButton.isEnabled = success
            saveFileButton.isEnabled = success
            cancelWriteButton.visibility = Button.GONE
        }
    }

    private fun showCapturedData() {
        val data = capturedRawData ?: capturedMessage?.toByteArray() ?: return
        val blockSize = 4
        val sb = StringBuilder()
        sb.append("Total Blocks: ${data.size / blockSize}\n\n")
        
        for (i in 0 until (data.size / blockSize)) {
            val start = i * blockSize
            if (start + blockSize > data.size) break
            val block = data.copyOfRange(start, start + blockSize)
            val hex = block.joinToString("") { "%02X".format(it) }
            sb.append("%02X: $hex\n".format(i))
            if (i > 50) { 
                sb.append("... and more blocks ...")
                break
            }
        }
        
        android.app.AlertDialog.Builder(this)
            .setTitle("Brick Data (Hex View)")
            .setMessage(sb.toString())
            .setPositiveButton("OK", null)
            .show()
    }

    private fun loadBrickFromFile(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = inputStream.bufferedReader()
                val out = java.io.ByteArrayOutputStream()
                var blocksCount = 0
                
                reader.forEachLine { line ->
                    // Matches "00: 00A9010C" or "0A: 714F6A7E"
                    val regex = Regex("^([0-9A-Fa-f]{2}):\\s*([0-9A-Fa-f]{8})")
                    val match = regex.find(line.trim())
                    if (match != null) {
                        val hexData = match.groupValues[2]
                        for (i in 0 until 4) {
                            val byteHex = hexData.substring(i * 2, i * 2 + 2)
                            out.write(byteHex.toInt(16))
                        }
                        blocksCount++
                    }
                }
                
                if (blocksCount > 0) {
                    capturedRawData = out.toByteArray()
                    capturedRawTech = "NfcV"
                    capturedMessage = null
                    
                    runOnUiThread {
                        statusText.text = "File Loaded"
                        copyStatusText.text = "Loaded $blocksCount blocks from file."
                        writeCopyButton.isEnabled = true
                        viewDataButton.isEnabled = true
                        saveFileButton.isEnabled = true
                        Toast.makeText(this, "Brick data loaded successfully", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    throw Exception("No valid block data found in file.")
                }
            }
        } catch (e: Exception) {
            runOnUiThread {
                Toast.makeText(this, "Error loading file: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun saveBrickToFile(uri: Uri) {
        val data = capturedRawData ?: return
        val blockSize = 4
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                val writer = outputStream.bufferedWriter()
                val totalBlocks = data.size / blockSize
                writer.write("# Blocks: $totalBlocks, Size: $blockSize\n")
                
                for (i in 0 until totalBlocks) {
                    val start = i * blockSize
                    val block = data.copyOfRange(start, start + blockSize)
                    val hex = block.joinToString("") { "%02X".format(it) }
                    writer.write("%02X: $hex\n".format(i))
                }
                writer.flush()
            }
            Toast.makeText(this, "File saved successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error saving file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun writeCapturedMessageToTag(tag: Tag) {
        if (capturedMessage != null) {
            writeNdefToTag(tag, capturedMessage!!)
        } else if (capturedRawData != null && capturedRawTech == "NfcV") {
            writeRawNfcVToTag(tag, capturedRawData!!)
        }
    }

    private fun writeNdefToTag(tag: Tag, message: NdefMessage) {
        var success = false
        var result: String
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            try {
                ndef.connect()
                if (ndef.isWritable && ndef.maxSize >= message.toByteArray().size) {
                    ndef.writeNdefMessage(message)
                    success = true
                    result = "Clone successful (NDEF)"
                } else {
                    result = "Tag too small or read-only"
                }
            } catch (e: Exception) {
                result = "NDEF Write failed: ${e.message}"
            } finally {
                ndef.close()
            }
        } else {
            val formatable = NdefFormatable.get(tag)
            if (formatable != null) {
                try {
                    formatable.connect()
                    formatable.format(message)
                    success = true
                    result = "Formatted and Cloned"
                } catch (e: Exception) {
                    result = "Format failed: ${e.message}"
                } finally {
                    formatable.close()
                }
            } else {
                result = "Tag not NDEF/Formatable"
            }
        }
        updateStatusAfterWrite(success, result)
    }

    private fun writeRawNfcVToTag(tag: Tag, data: ByteArray) {
        val nfcV = android.nfc.tech.NfcV.get(tag)
        val uid = tag.id
        var success = false
        var result = "Tag doesn't support NfcV"
        
        if (nfcV != null) {
            try {
                nfcV.connect()
                // nfcV.timeout = 1000 // Removed as it might be causing unresolved reference in some SDK configs
                
                val blockSize = 4
                val totalBlocks = data.size / blockSize
                
                for (i in 0 until totalBlocks) {
                    val progress = i + 1
                    runOnUiThread {
                        statusText.text = "Cloning: $progress / $totalBlocks..."
                    }

                    val blockData = data.copyOfRange(i * blockSize, (i + 1) * blockSize)
                    
                    // Addressed Write Single Block (Command 0x21, Flags 0x22)
                    val writeCmd = ByteArray(2 + 8 + 1 + blockSize)
                    writeCmd[0] = 0x22.toByte() 
                    writeCmd[1] = 0x21.toByte() 
                    System.arraycopy(uid, 0, writeCmd, 2, 8) 
                    writeCmd[10] = i.toByte() 
                    System.arraycopy(blockData, 0, writeCmd, 11, blockSize) 

                    var response = nfcV.transceive(writeCmd)
                    
                    // Fallback to Non-Addressed
                    if (response == null || response[0] != 0.toByte()) {
                        val simpleCmd = byteArrayOf(0x02.toByte(), 0x21.toByte(), i.toByte(), 
                                                  blockData[0], blockData[1], blockData[2], blockData[3])
                        response = nfcV.transceive(simpleCmd)
                    }

                    if (response == null || response[0] != 0.toByte()) {
                        val errCode = if (response != null) "%02X".format(response[0]) else "TIMEOUT"
                        throw Exception("Block $i failed (Error: $errCode)")
                    }

                    Thread.sleep(10)

                    // VERIFY with Addressed Read (Command 0x20, Flags 0x22)
                    val readCmd = ByteArray(2 + 8 + 1)
                    readCmd[0] = 0x22.toByte()
                    readCmd[1] = 0x20.toByte()
                    System.arraycopy(uid, 0, readCmd, 2, 8)
                    readCmd[10] = i.toByte()

                    var verifyResp = nfcV.transceive(readCmd)
                    
                    // Fallback to Non-Addressed Read
                    if (verifyResp == null || verifyResp[0] != 0.toByte()) {
                        verifyResp = nfcV.transceive(byteArrayOf(0x02.toByte(), 0x20.toByte(), i.toByte()))
                    }

                    if (verifyResp == null || verifyResp[0] != 0.toByte() || 
                        !verifyResp.copyOfRange(1, 5).contentEquals(blockData)) {
                        throw Exception("Verify failed at block $i")
                    }
                }
                success = true
                result = "Raw Clone successful ($totalBlocks blocks)"
            } catch (e: Exception) {
                result = "Cloning Failed: ${e.message}"
            } finally {
                nfcV.close()
            }
        }
        updateStatusAfterWrite(success, result)
    }

    private fun updateStatusAfterWrite(success: Boolean, result: String) {
        runOnUiThread {
            writeCopyMode = false
            statusText.text = if (success) "SUCCESS: Tag Cloned" else "ERROR: Write Failed"
            statusText.setTextColor(if (success) Color.parseColor("#2E7D32") else Color.RED)
            tagInfoText.text = result
            cancelWriteButton.visibility = Button.GONE
            
            Toast.makeText(this, result, Toast.LENGTH_LONG).show()
        }
    }

    private fun readTag(tag: Tag) {
        val uid = tag.id.joinToString(" ") { "%02X".format(it) }
        val technologies = tag.techList.joinToString("\n") {
            "• ${it.substringAfterLast('.')}"
        }

        var ndefText: String? = null
        var ndefStatus = "No NDEF data"

        val ndef = Ndef.get(tag)
        if (ndef != null) {
            try {
                ndef.connect()
                val message = ndef.ndefMessage
                ndefText = message?.records
                    ?.firstNotNullOfOrNull { parseTextRecord(it) }

                ndefStatus = ndefText ?: "NDEF formatted, but no text record found"
            } catch (e: Exception) {
                ndefStatus = "Could not read NDEF: ${e.message}"
            } finally {
                try {
                    ndef.close()
                } catch (_: Exception) {
                }
            }
        }

        val result = """
            UID:
            $uid

            Technologies:
            $technologies

            NDEF:
            $ndefStatus
        """.trimIndent()

        runOnUiThread {
            if (ndefText != null) {
                val isNew = inventory.add(ndefText)
                statusText.text = if (isNew) "NEW DISCOVERY: $ndefText" else "You already have: $ndefText"
                if (isNew) updateInventoryUI()
            } else {
                statusText.text = "NFC tag detected"
            }

            tagInfoText.text = result
        }
    }

    private fun updateInventoryUI() {
        if (inventory.isEmpty()) {
            inventoryText.text = "No objects found yet. Start scanning!"
        } else {
            val list = inventory.sorted().joinToString("\n") { "• $it" }
            inventoryText.text = list
        }
    }

    private fun parseTextRecord(record: NdefRecord): String? {
        if (record.tnf != NdefRecord.TNF_WELL_KNOWN) return null
        if (!record.type.contentEquals(NdefRecord.RTD_TEXT)) return null

        val payload = record.payload
        if (payload.isEmpty()) return null

        val status = payload[0].toInt()
        val languageLength = status and 0x3F
        val utf16 = status and 0x80 != 0

        if (payload.size <= 1 + languageLength) return null

        val charset: Charset =
            if (utf16) Charsets.UTF_16
            else Charsets.UTF_8

        return payload.copyOfRange(
            1 + languageLength,
            payload.size
        ).toString(charset)
    }
}
