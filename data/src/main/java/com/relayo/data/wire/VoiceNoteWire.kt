package com.relayo.data.wire

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

data class VoiceHeaderWire(
    val noteId:String,
    val senderId:String,
    val recipientId:String,
    val totalChunks:Int,
    val durationMillis:Long,
    val timestampEpochMillis:Long
)

object VoiceHeaderWireCodec {
    fun encode(wire:VoiceHeaderWire):ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.writeUTF(wire.noteId)
        dos.writeUTF(wire.senderId)
        dos.writeUTF(wire.recipientId)
        dos.writeInt(wire.totalChunks)
        dos.writeLong(wire.durationMillis)
        dos.writeLong(wire.timestampEpochMillis)
        dos.flush()
        return baos.toByteArray()
    }

    fun decode(bytes:ByteArray):VoiceHeaderWire? = try {
        val dis = DataInputStream(ByteArrayInputStream(bytes))
        val noteId = dis.readUTF()
        val senderId = dis.readUTF()
        val recipientId = dis.readUTF()
        val totalChunks = dis.readInt()
        val duration = dis.readLong()
        val timestamp = dis.readLong()
        VoiceHeaderWire(noteId, senderId, recipientId, totalChunks, duration, timestamp)
    } catch(e:Exception) { null }
}

data class VoiceChunkWire(
    val noteId:String,
    val chunkIndex:Int,
    val chunkBytes:ByteArray
) {
    override fun equals(other:Any?):Boolean {
        if(this === other) return true
        if(other !is VoiceChunkWire) return false
        return noteId == other.noteId && chunkIndex == other.chunkIndex && chunkBytes.contentEquals(other.chunkBytes)
    }
    override fun hashCode():Int {
        var result = noteId.hashCode()
        result = 31 * result + chunkIndex
        result = 31 * result + chunkBytes.contentHashCode()
        return result
    }
}

object VoiceChunkWireCodec {
    fun encode(wire:VoiceChunkWire):ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.writeUTF(wire.noteId)
        dos.writeInt(wire.chunkIndex)
        dos.writeInt(wire.chunkBytes.size)
        dos.write(wire.chunkBytes)
        dos.flush()
        return baos.toByteArray()
    }

    fun decode(bytes:ByteArray):VoiceChunkWire? = try {
        val dis = DataInputStream(ByteArrayInputStream(bytes))
        val noteId = dis.readUTF()
        val chunkIndex = dis.readInt()
        val size = dis.readInt()
        val chunkBytes = ByteArray(size)
        dis.readFully(chunkBytes)
        VoiceChunkWire(noteId, chunkIndex, chunkBytes)
    } catch(e:Exception) { null }
}
