package com.relayo.data.audio

import android.content.Context
import android.media.MediaRecorder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceRecorderController @Inject constructor(
    @ApplicationContext private val context:Context
) {

    private var recorder:MediaRecorder? = null
    private var outputFile:File? = null
    private var startTimeMillis = 0L
    private var tickerJob:Job? = null

    private val _elapsedMillis = MutableStateFlow(0L)
    val elapsedMillis:StateFlow<Long> = _elapsedMillis.asStateFlow()

    fun start(scope:CoroutineScope) {
        val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        outputFile = file

        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }

        startTimeMillis = System.currentTimeMillis()
        _elapsedMillis.value = 0L

        tickerJob = scope.launch {
            while(true) {
                delay(200)
                val elapsed = System.currentTimeMillis() - startTimeMillis
                _elapsedMillis.value = elapsed
                if(elapsed >= MAX_DURATION_MILLIS) break
            }
        }
    }

    fun stop():Pair<String, Long> {
        tickerJob?.cancel()
        val finalElapsed = _elapsedMillis.value
        val minValidDurationMillis = 400L

        if(finalElapsed < minValidDurationMillis) {
            try {
                recorder?.reset()
            } catch(e:RuntimeException) {
            }
            recorder?.release()
            recorder = null
            outputFile?.delete()
            return "" to finalElapsed
        }

        try {
            recorder?.stop()
        } catch(e:RuntimeException) {
            outputFile?.delete()
            recorder?.release()
            recorder = null
            return "" to finalElapsed
        }
        recorder?.release()
        recorder = null
        val path = outputFile?.absolutePath.orEmpty()
        return path to finalElapsed
    }

    companion object {
        const val MAX_DURATION_MILLIS = 60_000L
    }
}