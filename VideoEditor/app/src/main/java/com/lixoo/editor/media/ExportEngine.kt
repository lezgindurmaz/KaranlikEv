package com.lixoo.editor.media

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.lixoo.editor.data.local.entity.VideoClipEntity
import android.util.Log

object ExportEngine {
    private const val TAG = "ExportEngine"

    fun exportVideo(
        clips: List<VideoClipEntity>,
        outputPath: String,
        onProgress: (Int) -> Unit,
        onComplete: (Boolean) -> Unit
    ) {
        if (clips.isEmpty()) {
            onComplete(false)
            return
        }

        // Complex filter graph for concatenation, trimming, and effects
        val command = StringBuilder()
        val filterGraph = StringBuilder()

        clips.forEachIndexed { index, _ ->
            command.append("-i \"${clips[index].videoPath}\" ")
        }

        command.append("-filter_complex \"")
        clips.forEachIndexed { index, clip ->
            val durationMs = clip.endTimeMs - clip.startTimeMs
            val startTimeS = clip.startTimeMs / 1000.0
            val durationS = durationMs / 1000.0

            // Trim, Speed, and Mute for each clip
            val videoFilter = "atrim=start=$startTimeS:end=${clip.endTimeMs/1000.0},asetpts=PTS-STARTPTS"
            val speedFilterV = "setpts=${1.0/clip.speed}*PTS"
            val speedFilterA = if (clip.speed != 1.0f) ",atempo=${clip.speed}" else ""

            filterGraph.append("[$index:v]trim=start=$startTimeS:end=${clip.endTimeMs/1000.0},setpts=PTS-STARTPTS,$speedFilterV[v$index];")
            if (clip.isMuted) {
                filterGraph.append("anullsrc=channel_layout=stereo:sample_rate=44100[a$index];")
            } else {
                filterGraph.append("[$index:a]atrim=start=$startTimeS:end=${clip.endTimeMs/1000.0},asetpts=PTS-STARTPTS$speedFilterA[a$index];")
            }
        }

        // Concatenate all
        clips.forEachIndexed { index, _ ->
            filterGraph.append("[v$index][a$index]")
        }
        filterGraph.append("concat=n=${clips.size}:v=1:a=1[outv][outa]\" ")

        command.append(filterGraph)
        command.append("-map \"[outv]\" -map \"[outa]\" -c:v libx264 -preset ultrafast \"$outputPath\" -y")

        Log.d(TAG, "FFmpeg command: $command")

        FFmpegKit.executeAsync(command.toString(), { session ->
            val returnCode = session.returnCode
            if (ReturnCode.isSuccess(returnCode)) {
                onComplete(true)
            } else {
                Log.e(TAG, "FFmpeg failed with state ${session.state} and rc $returnCode")
                onComplete(false)
            }
        }, { log ->
            Log.d(TAG, log.message)
        }, { statistics ->
            // Calculate progress based on statistics and total duration
            onProgress(0) // Simplified
        })
    }
}
