package com.aandi.A_AND_I_DISCORD_BOT.meeting.voice.service

import com.aandi.A_AND_I_DISCORD_BOT.meeting.entity.MeetingSessionEntity
import com.aandi.A_AND_I_DISCORD_BOT.meeting.repository.MeetingSessionRepository
import com.aandi.A_AND_I_DISCORD_BOT.voice.summary.entity.VoiceSummaryStatus
import com.aandi.A_AND_I_DISCORD_BOT.voice.summary.repository.VoiceSummaryJobRepository
import com.aandi.A_AND_I_DISCORD_BOT.voice.summary.service.VoiceSummaryJobService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

@Service
class MeetingVoiceSummaryService(
    private val meetingSessionRepository: MeetingSessionRepository,
    private val voiceSummaryJobRepository: VoiceSummaryJobRepository,
    private val voiceSummaryJobService: VoiceSummaryJobService,
    private val clock: Clock,
    @Value("\${voice.summary.enabled:false}")
    private val enabled: Boolean,
    @Value("\${voice.summary.max-minutes:120}")
    private val maxMinutes: Int,
    @Value("\${voice.summary.data-dir:/data/voice}")
    private val dataDir: String,
) {

    @Transactional
    fun start(guildId: Long, meetingRefId: Long, voiceChannelId: Long, createdBy: Long): StartResult {
        if (!enabled) {
            return StartResult.Disabled(policyMessage())
        }

        val meeting = resolveMeeting(guildId, meetingRefId) ?: return StartResult.MeetingNotFound
        val existing = voiceSummaryJobRepository.findFirstByGuildIdAndMeetingThreadIdOrderByIdDesc(
            guildId = guildId,
            meetingThreadId = meeting.threadId,
        )
        if (existing != null && existing.status == VoiceSummaryStatus.RECORDING) {
            return StartResult.InvalidState("이미 RECORDING 상태의 작업이 있습니다.", existing.toView())
        }

        val job = voiceSummaryJobService.createJob(
            guildId = guildId,
            voiceChannelId = voiceChannelId,
            meetingThreadId = meeting.threadId,
            createdBy = createdBy,
            nowUtc = Instant.now(clock),
        )
        val started = voiceSummaryJobService.start(job.id, Instant.now(clock))
        if (started is VoiceSummaryJobService.TransitionResult.Success) {
            return StartResult.Accepted(notImplementedMessage(), started.job)
        }
        if (started is VoiceSummaryJobService.TransitionResult.InvalidState) {
            return StartResult.InvalidState("상태 전이에 실패했습니다.", started.job)
        }
        return StartResult.InvalidState("작업을 찾을 수 없습니다.", job)
    }

    @Transactional
    fun stop(guildId: Long, meetingRefId: Long): StopResult {
        if (!enabled) {
            return StopResult.Disabled(policyMessage())
        }

        val meeting = resolveMeeting(guildId, meetingRefId) ?: return StopResult.MeetingNotFound
        val existing = voiceSummaryJobRepository.findFirstByGuildIdAndMeetingThreadIdOrderByIdDesc(
            guildId = guildId,
            meetingThreadId = meeting.threadId,
        ) ?: return StopResult.JobNotFound

        val stopped = voiceSummaryJobService.stop(existing.id ?: return StopResult.JobNotFound, Instant.now(clock))
        if (stopped is VoiceSummaryJobService.TransitionResult.Success) {
            return StopResult.Accepted(notImplementedMessage(), stopped.job)
        }
        if (stopped is VoiceSummaryJobService.TransitionResult.InvalidState) {
            return StopResult.InvalidState("RECORDING 상태가 아니어서 종료할 수 없습니다.", stopped.job)
        }
        return StopResult.JobNotFound
    }

    @Transactional(readOnly = true)
    fun status(guildId: Long, meetingRefId: Long): StatusResult {
        if (!enabled) {
            return StatusResult.Disabled(policyMessage())
        }

        val meeting = resolveMeeting(guildId, meetingRefId) ?: return StatusResult.MeetingNotFound
        val existing = voiceSummaryJobRepository.findFirstByGuildIdAndMeetingThreadIdOrderByIdDesc(
            guildId = guildId,
            meetingThreadId = meeting.threadId,
        ) ?: return StatusResult.Ready("아직 음성요약 작업이 없습니다.")

        return StatusResult.Ready(
            message = "음성요약 Skeleton 상태입니다. 실제 음성 연결/전사는 TODO입니다.",
            job = existing.toView(),
        )
    }

    private fun resolveMeeting(guildId: Long, meetingRefId: Long): MeetingSessionEntity? {
        val byMeetingId = meetingSessionRepository.findById(meetingRefId).orElse(null)
        if (byMeetingId != null && byMeetingId.guildId == guildId) {
            return byMeetingId
        }
        return meetingSessionRepository.findByGuildIdAndThreadId(guildId, meetingRefId)
    }

    private fun policyMessage(): String {
        return "현재 음성요약 기능은 비활성화 상태입니다(관리자 설정 필요)."
    }

    private fun notImplementedMessage(): String {
        return "VOICE_SUMMARY_ENABLED=true 이지만 현재 빌드는 skeleton만 포함합니다. 실제 오디오 연결/녹음/전사는 TODO입니다."
    }

    private fun com.aandi.A_AND_I_DISCORD_BOT.voice.summary.entity.VoiceSummaryJobEntity.toView():
        VoiceSummaryJobService.VoiceSummaryJobView = VoiceSummaryJobService.VoiceSummaryJobView(
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

    sealed interface StartResult {
        data class Disabled(val message: String) : StartResult
        data class Accepted(
            val message: String,
            val job: VoiceSummaryJobService.VoiceSummaryJobView,
        ) : StartResult

        data class InvalidState(
            val message: String,
            val job: VoiceSummaryJobService.VoiceSummaryJobView,
        ) : StartResult

        data object MeetingNotFound : StartResult
    }

    sealed interface StopResult {
        data class Disabled(val message: String) : StopResult
        data class Accepted(
            val message: String,
            val job: VoiceSummaryJobService.VoiceSummaryJobView,
        ) : StopResult

        data class InvalidState(
            val message: String,
            val job: VoiceSummaryJobService.VoiceSummaryJobView,
        ) : StopResult

        data object MeetingNotFound : StopResult
        data object JobNotFound : StopResult
    }

    sealed interface StatusResult {
        data class Disabled(val message: String) : StatusResult
        data class Ready(
            val message: String,
            val job: VoiceSummaryJobService.VoiceSummaryJobView? = null,
        ) : StatusResult

        data object MeetingNotFound : StatusResult
    }

    fun summaryConfig(): SummaryConfig = SummaryConfig(
        enabled = enabled,
        maxMinutes = maxMinutes,
        dataDir = dataDir,
    )

    data class SummaryConfig(
        val enabled: Boolean,
        val maxMinutes: Int,
        val dataDir: String,
    )
}
