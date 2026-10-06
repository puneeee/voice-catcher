package com.puneeee.voicecatcher

import android.content.Context
import android.media.MediaRecorder
import java.io.File

/**
 * Local-only first step. Uploading and transcription are intentionally added
 * only after the user has configured the protected backend.
 */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun start(): Result<Unit> = runCatching {
        check(recorder == null) { "A recording is already in progress." }
        val file = File(context.cacheDir, "capture-${System.currentTimeMillis()}.m4a")
        MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(file.absolutePath)
            prepare()
            start()
            recorder = this
            outputFile = file
        }
    }

    fun stop(): Result<File> = runCatching {
        val activeRecorder = checkNotNull(recorder) { "No recording is active." }
        val file = checkNotNull(outputFile)
        try {
            activeRecorder.stop()
            file
        } finally {
            activeRecorder.reset()
            activeRecorder.release()
            recorder = null
            outputFile = null
        }
    }

    fun release() {
        val activeRecorder = recorder ?: return
        runCatching {
            activeRecorder.reset()
            activeRecorder.release()
        }
        recorder = null
        outputFile = null
    }
}
