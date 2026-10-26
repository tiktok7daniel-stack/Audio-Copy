package com.cloneLocal.app

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Decodes the audio track of any audio/video file to mono float PCM. */
object AudioDecoder {
    fun decode(ctx: Context, uri: Uri, maxSeconds: Int): Pair<FloatArray, Int> {
        val extractor = MediaExtractor()
        extractor.setDataSource(ctx, uri, null)
        var index = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                index = i
                format = f
                break
            }
        }
        if (index < 0 || format == null) {
            extractor.release()
            error("No audio track found")
        }
        extractor.selectTrack(index)
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()

        var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var out = FloatArray(rate * maxSeconds)
        var n = 0
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false

        try {
            while (!outputDone && n < out.size) {
                if (!inputDone) {
                    val ib = codec.dequeueInputBuffer(10_000)
                    if (ib >= 0) {
                        val buf = codec.getInputBuffer(ib)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(ib, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(ib, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val ob = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    ob == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val nf = codec.outputFormat
                        rate = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        if (out.size < rate * maxSeconds) out = out.copyOf(rate * maxSeconds)
                    }
                    ob >= 0 -> {
                        val buf = codec.getOutputBuffer(ob)!!
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        val shorts = buf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val frames = shorts.remaining() / channels
                        var f = 0
                        while (f < frames && n < out.size) {
                            var sum = 0f
                            for (c in 0 until channels) sum += shorts.get() / 32768f
                            out[n++] = sum / channels
                            f++
                        }
                        codec.releaseOutputBuffer(ob, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
        } finally {
            codec.stop()
            codec.release()
            extractor.release()
        }
        if (n == 0) error("Audio track decoded to nothing")
        return out.copyOf(n) to rate
    }
}

object WavWriter {
    fun write(os: OutputStream, samples: FloatArray, rate: Int) {
        val dataLen = samples.size * 2
        val bb = ByteBuffer.allocate(44 + dataLen).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray()).putInt(36 + dataLen).put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            .putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        bb.put("data".toByteArray()).putInt(dataLen)
        for (s in samples) bb.putShort((s.coerceIn(-1f, 1f) * 32767).toInt().toShort())
        os.write(bb.array())
    }
}
