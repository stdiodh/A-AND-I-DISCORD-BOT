package com.aandi.A_AND_I_DISCORD_BOT.voice.summary.service

import com.aandi.A_AND_I_DISCORD_BOT.voice.summary.entity.VoiceSummaryJobEntity
import com.aandi.A_AND_I_DISCORD_BOT.voice.summary.entity.VoiceSummaryStatus
import com.aandi.A_AND_I_DISCORD_BOT.voice.summary.repository.VoiceSummaryJobRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class VoiceSummaryJobService(
    private val voiceSummaryJobRepository: VoiceSummaryJobRepository,
) {

    @Transactional
    fun createJob(
        guildId: Long,
        voiceChannelId: Long,
        meetingThreadId: Long?,
        createdBy: Long,
        nowUtc: Instant,
    ): VoiceSummaryJobView {
        val saved = voiceSummaryJobRepository.save(
            VoiceSummaryJobEntity(
                guildId = guildId,
                voiceChannelId = voiceChannelId,
                meetingThreadId = meetingThreadId,
                status = VoiceSummaryStatus.READY,
                dataDir = "/data/voice/job-$guildId-$voiceChannelId-${nowUtc.epochSecond}",
                maxMinutes = DEFAULT_MAX_MINUTES,
                createdBy = createdBy,
                createdAt = nowUtc,
                updatedAt = nowUtc,
            ),
        )
        return saved.toView()
    }

    @Transactional
    fun start(jobId: Long, nowUtc: Instant): TransitionResult {
        val job = voiceSummaryJobRepository.findById(jobId).orElse(null) ?: return TransitionResult.NotFound
        if (job.status == VoiceSummaryStatus.DISABLED) {
            return TransitionResult.InvalidState(job.toView())
        }
        if (job.status != VoiceSummaryStatus.READY) {
            return TransitionResult.InvalidState(job.toView())
        }

        job.status = VoiceSummaryStatus.RECORDING
        job.startedAt = nowUtc
        job.updatedAt = nowUtc
        val saved = voiceSummaryJobRepository.save(job)
        return TransitionResult.Success(saved.toView())
    }

    @Transactional
    fun stop(jobId: Long, nowUtc: Instant): TransitionResult {
        val job = voiceSummaryJobRepository.findById(jobId).orElse(null) ?: return TransitionResult.NotFound
        if (job.status == VoiceSummaryStatus.DISABLED) {
            return TransitionResult.InvalidState(job.toView())
        }
        if (job.status != VoiceSummaryStatus.RECORDING) {
            return TransitionResult.InvalidState(job.toView())
        }

        job.status = VoiceSummaryStatus.RECORDED
        job.endedAt = nowUtc
        job.updatedAt = nowUtc
        val saved = voiceSummaryJobRepository.save(job)
        return TransitionResult.Success(saved.toView())
    }

    @Transactional(readOnly = true)
    fun status(jobId: Long): StatusResult {
        val job = voiceSummaryJobRepository.findById(jobId).orElse(null) ?: return StatusResult.NotFound
        return StatusResult.Success(job.toView())
    }

    private fun VoiceSummaryJobEntity.toView(): VoiceSummaryJobView = VoiceSummaryJobView(
        id = requireNotNull(id),
        guildId = guildId,
        meetingThreadId = meetingThreadId,
        voiceChannelId = voiceChannelId,
        status = status,
        dataDir = dataDir,
        maxMinutes = maxMinutes,
        startedAt = startedAt,
        endedAt = endedAt,
        createdBy = createdBy,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastError = lastError,
    )

    data class VoiceSummaryJobView(
        val id: Long,
        val guildId: Long,
        val meetingThreadId: Long?,
        val voiceChannelId: Long,
        val status: VoiceSummaryStatus,
        val dataDir: String,
        val maxMinutes: Int,
        val startedAt: Instant?,
        val endedAt: Instant?,
        val createdBy: Long,
        val createdAt: Instant,
        val updatedAt: Instant,
        val lastError: String?,
    )

    sealed interface TransitionResult {
        data class Success(val job: VoiceSummaryJobView) : TransitionResult
        data class InvalidState(val job: VoiceSummaryJobView) : TransitionResult
        data object NotFound : TransitionResult
    }

    sealed interface StatusResult {
        data class Success(val job: VoiceSummaryJobView) : StatusResult
        data object NotFound : StatusResult
    }

    companion object {
        private const val DEFAULT_MAX_MINUTES = 120
    }
}

