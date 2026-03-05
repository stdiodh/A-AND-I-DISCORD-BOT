package com.aandi.A_AND_I_DISCORD_BOT.dashboard.service

import com.aandi.A_AND_I_DISCORD_BOT.admin.service.GuildConfigService
import com.aandi.A_AND_I_DISCORD_BOT.agenda.service.AgendaService
import com.aandi.A_AND_I_DISCORD_BOT.assignment.service.AssignmentTaskService
import com.aandi.A_AND_I_DISCORD_BOT.common.config.FeatureFlagsProperties
import com.aandi.A_AND_I_DISCORD_BOT.common.format.DurationFormatter
import com.aandi.A_AND_I_DISCORD_BOT.common.log.StructuredLog
import com.aandi.A_AND_I_DISCORD_BOT.common.time.KstTime
import com.aandi.A_AND_I_DISCORD_BOT.common.time.PeriodType
import com.aandi.A_AND_I_DISCORD_BOT.dashboard.ui.DashboardActionIds
import com.aandi.A_AND_I_DISCORD_BOT.dashboard.ui.HomeDashboardComponentBuilder
import com.aandi.A_AND_I_DISCORD_BOT.dashboard.ui.DashboardRenderer
import com.aandi.A_AND_I_DISCORD_BOT.meeting.entity.MeetingSessionStatus
import com.aandi.A_AND_I_DISCORD_BOT.meeting.repository.MeetingSessionRepository
import com.aandi.A_AND_I_DISCORD_BOT.mogakco.service.MogakcoService
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.requests.ErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.awt.Color
import java.time.Clock
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Service
@ConditionalOnProperty(name = ["discord.enabled"], havingValue = "true", matchIfMissing = true)
class HomeDashboardService(
    private val guildConfigService: GuildConfigService,
    private val agendaService: AgendaService,
    private val assignmentTaskService: AssignmentTaskService,
    private val mogakcoService: MogakcoService,
    private val meetingSessionRepository: MeetingSessionRepository,
    private val durationFormatter: DurationFormatter,
    private val renderer: DashboardRenderer,
    private val homeDashboardComponentBuilder: HomeDashboardComponentBuilder,
    private val homeMessageManager: HomeMessageManager,
    private val featureFlags: FeatureFlagsProperties,
    private val clock: Clock,
    @Lazy private val jda: JDA,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun create(guildId: Long, guildName: String?, channelId: Long): Result {
        return ensureAndSync(
            guildId = guildId,
            guildName = guildName,
            preferredChannelId = channelId,
            allowCreate = true,
        )
    }

    @Transactional
    fun install(guildId: Long, guildName: String?, preferredChannelId: Long?): Result {
        return ensureAndSync(
            guildId = guildId,
            guildName = guildName,
            preferredChannelId = preferredChannelId,
            allowCreate = true,
        )
    }

    @Transactional
    fun refresh(guildId: Long, guildName: String?): Result {
        return ensureAndSync(
            guildId = guildId,
            guildName = guildName,
            preferredChannelId = null,
            allowCreate = false,
        )
    }

    private fun ensureAndSync(
        guildId: Long,
        guildName: String?,
        preferredChannelId: Long?,
        allowCreate: Boolean,
    ): Result {
        val operation = resolveOperationName(allowCreate)
        val eventName = "$operation.start"
        log.info(
            StructuredLog.event(
                name = eventName,
                "guildId" to guildId,
                "preferredChannelId" to preferredChannelId,
                "homeV2" to featureFlags.homeV2,
            ),
        )

        val pendingPayload = toPayload(guildId, guildName, homePinStatusLine = PIN_STATUS_CHECKING)
        val resolved = when (
            val resolvedResult = resolveHomeReference(guildId, preferredChannelId, pendingPayload, allowCreate)
        ) {
            is ResolveHomeResult.Success -> resolvedResult.resolved
            ResolveHomeResult.NotConfigured -> return failHomeOperation(operation, guildId, "NOT_CONFIGURED", Result.NotConfigured)
            ResolveHomeResult.ChannelNotFound -> {
                return failHomeOperation(operation, guildId, "CHANNEL_NOT_FOUND", Result.ChannelNotFound)
            }
            ResolveHomeResult.MessageNotFound -> {
                return failHomeOperation(operation, guildId, "MESSAGE_NOT_FOUND", Result.MessageNotFound)
            }
        }

        val pinResult = pinIfPossible(guildId, resolved.channelId, resolved.messageId)
        val finalStatus = homePinStatusLine(pinResult)
        val finalPayload = toPayload(guildId, guildName, homePinStatusLine = finalStatus)
        val finalUpdated = homeMessageManager.updateHomeMessage(guildId, finalPayload)
        if (finalUpdated !is HomeMessageManager.UpdateResult.Success) {
            return failHomeOperation(
                operation = operation,
                guildId = guildId,
                reason = "FINAL_UPDATE_${finalUpdated::class.simpleName}",
                result = mapResolveResultToResult(mapUpdateResult(finalUpdated)),
                asError = true,
            )
        }

        val doneEventName = "$operation.done"
        log.info(
            StructuredLog.event(
                name = doneEventName,
                "guildId" to guildId,
                "channelId" to resolved.channelId,
                "messageId" to resolved.messageId,
                "pinResult" to pinResult.name,
            ),
        )

        return Result.Success(
            channelId = resolved.channelId,
            messageId = resolved.messageId,
            pinResult = pinResult,
            createdNew = resolved.createdNew,
            pinStatusLine = finalStatus,
        )
    }

    private fun failHomeOperation(
        operation: String,
        guildId: Long,
        reason: String,
        result: Result,
        asError: Boolean = false,
    ): Result {
        val event = StructuredLog.event(
            name = "$operation.failed",
            "guildId" to guildId,
            "reason" to reason,
        )
        if (asError) {
            log.error(event)
            return result
        }
        log.warn(event)
        return result
    }

    private fun resolveHomeReference(
        guildId: Long,
        preferredChannelId: Long?,
        payload: HomeMessageManager.HomePayload,
        allowCreate: Boolean,
    ): ResolveHomeResult {
        if (allowCreate) {
            val ensure = homeMessageManager.ensureHomeMessage(
                guildId = guildId,
                preferredChannelId = preferredChannelId,
                payload = payload,
            )
            if (ensure is HomeMessageManager.EnsureResult.ChannelNotConfigured) {
                return ResolveHomeResult.NotConfigured
            }
            if (ensure is HomeMessageManager.EnsureResult.ChannelNotFound) {
                return ResolveHomeResult.ChannelNotFound
            }
            val success = ensure as HomeMessageManager.EnsureResult.Success
            if (success.outcome != HomeMessageManager.EnsureOutcome.CREATED) {
                val updated = homeMessageManager.updateHomeMessage(guildId, payload)
                if (updated !is HomeMessageManager.UpdateResult.Success) {
                    return mapUpdateResult(updated)
                }
            }
            return ResolveHomeResult.Success(
                resolved = ResolvedHomeRef(
                    channelId = success.channelId,
                    messageId = success.messageId,
                    createdNew = success.outcome == HomeMessageManager.EnsureOutcome.CREATED,
                ),
            )
        }

        val updated = homeMessageManager.updateHomeMessage(guildId, payload)
        if (updated !is HomeMessageManager.UpdateResult.Success) {
            return mapUpdateResult(updated)
        }
        return ResolveHomeResult.Success(
            resolved = ResolvedHomeRef(
                channelId = updated.channelId,
                messageId = updated.messageId,
                createdNew = false,
            ),
        )
    }

    private fun mapUpdateResult(result: HomeMessageManager.UpdateResult): ResolveHomeResult {
        return when (result) {
            HomeMessageManager.UpdateResult.NotConfigured -> ResolveHomeResult.NotConfigured
            HomeMessageManager.UpdateResult.ChannelNotFound -> ResolveHomeResult.ChannelNotFound
            HomeMessageManager.UpdateResult.MessageNotFound -> ResolveHomeResult.MessageNotFound
            is HomeMessageManager.UpdateResult.Success -> {
                throw IllegalArgumentException("unexpected update success mapping")
            }
        }
    }

    private fun mapResolveResultToResult(result: ResolveHomeResult): Result {
        return when (result) {
            ResolveHomeResult.NotConfigured -> Result.NotConfigured
            ResolveHomeResult.ChannelNotFound -> Result.ChannelNotFound
            ResolveHomeResult.MessageNotFound -> Result.MessageNotFound
            is ResolveHomeResult.Success -> Result.MessageNotFound
        }
    }

    private fun toPayload(guildId: Long, guildName: String?, homePinStatusLine: String): HomeMessageManager.HomePayload {
        val bundle = loadBundle(guildId, guildName, homePinStatusLine)
        return HomeMessageManager.HomePayload(embed = bundle.embed, components = bundle.components)
    }

    private fun loadBundle(guildId: Long, guildName: String?, homePinStatusLine: String): DashboardBundle {
        val boardChannels = guildConfigService.getBoardChannels(guildId)
        val agenda = agendaService.getTodayAgenda(guildId)
        val activeSession = meetingSessionRepository.findFirstByGuildIdAndStatusOrderByStartedAtDesc(
            guildId,
            MeetingSessionStatus.ACTIVE,
        )
        val lastSession = meetingSessionRepository.findFirstByGuildIdOrderByStartedAtDesc(guildId)
        val taskSnapshot = resolveTaskSnapshot(guildId)
        val top3 = mogakcoService.getLeaderboard(guildId, PeriodType.WEEK, 3).entries
            .map {
                DashboardRenderer.MogakcoSummary(
                    userId = it.userId,
                    formattedDuration = durationFormatter.toHourMinute(it.totalSeconds),
                )
            }

        val view = renderer.render(
            DashboardRenderer.DashboardInput(
                guildName = guildName,
                isMeetingActive = activeSession != null,
                lastMeetingThreadId = lastSession?.threadId,
                todayAgenda = agenda?.let { DashboardRenderer.AgendaSummary(title = it.title, url = it.url) },
                pendingCount = taskSnapshot.pendingCount,
                dueSoonTop3 = taskSnapshot.dueSoonTop3,
                weeklyTop3 = top3,
            ),
        )

        val embed = EmbedBuilder()
            .setTitle(view.title)
            .setDescription(resolveOverviewDescription(view.overview))
            .addField("홈 고정 상태", homePinStatusLine, false)
            .addField("전용 채널", resolveBoardChannelSection(boardChannels), false)
            .addField("회의 상태", view.meetingSection, false)
            .addField("마감 임박 과제", view.assignmentSection, false)
            .addField("이번 주 모각코", view.mogakcoSection, false)
            .setColor(Color(0x1F8B4C))
            .build()

        val components = buildDashboardComponents(guildId, agenda?.url, boardChannels)

        return DashboardBundle(embed = embed, components = components)
    }

    private fun buildDashboardComponents(
        guildId: Long,
        agendaUrl: String?,
        boardChannels: GuildConfigService.BoardChannelConfig,
    ): List<ActionRow> {
        if (!featureFlags.homeV2) {
            val legacyComponents = mutableListOf<ActionRow>()
            legacyComponents.add(
                ActionRow.of(
                    Button.primary(DashboardActionIds.MEETING_START, "회의"),
                    Button.secondary(DashboardActionIds.AGENDA_SET, "안건 설정"),
                    Button.success(DashboardActionIds.ASSIGNMENT_CREATE, "과제 등록"),
                ),
            )
            legacyComponents.add(
                ActionRow.of(
                    Button.primary(DashboardActionIds.ASSIGNMENT_LIST, "과제 목록"),
                    Button.primary(DashboardActionIds.MOGAKCO_RANK, "모각코 랭킹"),
                    Button.primary(DashboardActionIds.MOGAKCO_ME, "내 기록"),
                ),
            )
            if (agendaUrl != null) {
                legacyComponents.add(ActionRow.of(Button.link(agendaUrl, "오늘 안건 링크")))
            }
            return legacyComponents
        }

        return homeDashboardComponentBuilder.buildHomeV2Components(
            guildId = guildId,
            agendaUrl = agendaUrl,
            channelTargets = HomeDashboardComponentBuilder.ChannelTargets(
                meetingChannelId = boardChannels.meetingChannelId,
                assignmentChannelId = boardChannels.assignmentChannelId,
                mogakcoChannelId = boardChannels.mogakcoChannelId,
            ),
            moreMenuOptions = HomeDashboardComponentBuilder.MoreMenuOptions(
                agendaValue = HOME_MORE_AGENDA,
                mogakcoMeValue = HOME_MORE_MOGAKCO_ME,
                settingsHelpValue = HOME_MORE_SETTINGS_HELP,
            ),
        )
    }

    private fun resolveTaskSnapshot(guildId: Long): TaskSnapshot {
        return when (val result = assignmentTaskService.list(guildId, "대기")) {
            is AssignmentTaskService.ListResult.Success -> {
                val nowUtc = clock.instant()
                val nowKstDate = nowUtc.atZone(KST_ZONE_ID).toLocalDate()
                val dueSoon = result.tasks
                    .asSequence()
                    .filter { it.dueAt.isAfter(nowUtc) }
                    .sortedBy { it.dueAt }
                    .mapNotNull { task ->
                        val dueKst = task.dueAt.atZone(KST_ZONE_ID)
                        val dayDiff = ChronoUnit.DAYS.between(nowKstDate, dueKst.toLocalDate()).toInt()
                        if (dayDiff !in 0..2) {
                            return@mapNotNull null
                        }
                        DashboardRenderer.DueTaskSummary(
                            id = task.id,
                            title = task.title,
                            ddayLabel = resolveDdayLabel(dayDiff),
                            dueAtKst = KstTime.formatInstantToKst(task.dueAt),
                        )
                    }
                    .take(3)
                    .toList()
                TaskSnapshot(
                    pendingCount = result.tasks.size,
                    dueSoonTop3 = dueSoon,
                )
            }

            AssignmentTaskService.ListResult.InvalidStatus,
            AssignmentTaskService.ListResult.HiddenDeleted,
            -> TaskSnapshot(
                pendingCount = null,
                dueSoonTop3 = emptyList(),
            )
        }
    }

    private data class TaskSnapshot(
        val pendingCount: Int?,
        val dueSoonTop3: List<DashboardRenderer.DueTaskSummary>,
    )

    private fun pinIfPossible(guildId: Long, channelId: Long, messageId: Long): PinResult {
        log.info(
            StructuredLog.event(
                name = "home.pin.attempt",
                "guildId" to guildId,
                "channelId" to channelId,
                "messageId" to messageId,
            ),
        )
        val channel = jda.getTextChannelById(channelId) ?: return PinResult.FAILED
        val selfMember = channel.guild.selfMember
        if (!selfMember.hasPermission(channel, Permission.MESSAGE_MANAGE)) {
            log.warn(
                StructuredLog.event(
                    name = "home.pin.no_permission",
                    "guildId" to guildId,
                    "channelId" to channelId,
                    "messageId" to messageId,
                ),
            )
            return PinResult.NO_PERMISSION
        }

        val message = runCatching { channel.retrieveMessageById(messageId).complete() }
            .getOrNull()
            ?: return PinResult.FAILED
        if (message.isPinned) {
            log.info(
                StructuredLog.event(
                    name = "home.pin.already_pinned",
                    "guildId" to guildId,
                    "channelId" to channelId,
                    "messageId" to messageId,
                ),
            )
            return PinResult.ALREADY_PINNED
        }

        val pinResult = runCatching {
            message.pin()
                .reason("A&I 홈 대시보드")
                .complete()
        }.fold(
            onSuccess = { PinResult.PINNED },
            onFailure = { exception -> resolvePinFailure(exception) },
        )

        when (pinResult) {
            PinResult.PINNED -> {
                log.info(
                    StructuredLog.event(
                        name = "home.pin.success",
                        "guildId" to guildId,
                        "channelId" to channelId,
                        "messageId" to messageId,
                    ),
                )
            }

            PinResult.PIN_LIMIT_REACHED -> {
                log.warn(
                    StructuredLog.event(
                        name = "home.pin.limit_reached",
                        "guildId" to guildId,
                        "channelId" to channelId,
                        "messageId" to messageId,
                    ),
                )
            }

            PinResult.FAILED -> {
                log.error(
                    StructuredLog.event(
                        name = "home.pin.failed",
                        "guildId" to guildId,
                        "channelId" to channelId,
                        "messageId" to messageId,
                    ),
                )
            }

            PinResult.NO_PERMISSION,
            PinResult.ALREADY_PINNED,
            -> Unit
        }
        return pinResult
    }

    private fun homePinStatusLine(pinResult: PinResult): String {
        return when (pinResult) {
            PinResult.PINNED,
            PinResult.ALREADY_PINNED,
            -> "홈 고정 상태: ✅ 고정됨\n해결 방법: 별도 조치가 필요하지 않습니다."

            PinResult.NO_PERMISSION ->
                "홈 고정 상태: ❌ 권한 부족: 메시지 관리 권한 필요\n해결 방법: 봇 역할에 `메시지 관리(Manage Messages)` 권한을 부여해 주세요."

            PinResult.PIN_LIMIT_REACHED ->
                "홈 고정 상태: ❌ 핀 한도 초과: 해당 채널 핀 정리 필요\n해결 방법: 채널 핀을 정리한 뒤 재확인을 실행해 주세요."

            PinResult.FAILED ->
                "홈 고정 상태: ❌ 고정 실패: 채널 상태 또는 권한 확인 필요\n해결 방법: 채널 접근 권한을 확인한 뒤 다시 시도해 주세요."
        }
    }

    private fun resolveOperationName(allowCreate: Boolean): String {
        if (allowCreate) {
            return "home.ensure"
        }
        return "home.update"
    }

    private fun resolveOverviewDescription(overview: String): String {
        if (featureFlags.homeV2) {
            return "## 오늘 요약\n$overview"
        }
        return overview
    }

    private fun resolveDdayLabel(dayDiff: Int): String {
        if (dayDiff == 0) {
            return "D-DAY"
        }
        return "D-$dayDiff"
    }

    private fun resolvePinFailure(exception: Throwable): PinResult {
        if (exception is ErrorResponseException && exception.errorResponse == ErrorResponse.MAX_MESSAGE_PINS) {
            return PinResult.PIN_LIMIT_REACHED
        }
        return PinResult.FAILED
    }

    private fun resolveBoardChannelSection(config: GuildConfigService.BoardChannelConfig): String {
        val meeting = config.meetingChannelId?.let { "<#${it}>" } ?: "미설정"
        val mogakco = config.mogakcoChannelId?.let { "<#${it}>" } ?: "미설정"
        val assignment = config.assignmentChannelId?.let { "<#${it}>" } ?: "미설정"
        return "회의: $meeting\n모각코: $mogakco\n과제: $assignment"
    }

    private fun channelJumpUrl(guildId: Long, channelId: Long): String {
        return "https://discord.com/channels/$guildId/$channelId"
    }

    private data class DashboardBundle(
        val embed: net.dv8tion.jda.api.entities.MessageEmbed,
        val components: List<ActionRow>,
    )

    private data class ResolvedHomeRef(
        val channelId: Long,
        val messageId: Long,
        val createdNew: Boolean,
    )

    private sealed interface ResolveHomeResult {
        data class Success(val resolved: ResolvedHomeRef) : ResolveHomeResult
        data object NotConfigured : ResolveHomeResult
        data object ChannelNotFound : ResolveHomeResult
        data object MessageNotFound : ResolveHomeResult
    }

    sealed interface Result {
        data class Success(
            val channelId: Long,
            val messageId: Long,
            val pinResult: PinResult,
            val createdNew: Boolean,
            val pinStatusLine: String,
        ) : Result

        data object NotConfigured : Result
        data object ChannelNotFound : Result
        data object MessageNotFound : Result
    }

    enum class PinResult {
        PINNED,
        ALREADY_PINNED,
        NO_PERMISSION,
        PIN_LIMIT_REACHED,
        FAILED,
    }

    companion object {
        private val KST_ZONE_ID: ZoneId = ZoneId.of("Asia/Seoul")
        private const val PIN_STATUS_CHECKING = "홈 고정 상태: 🔄 고정 상태 확인 중...\n해결 방법: 잠시 후 결과가 자동 반영됩니다."
        const val HOME_MORE_AGENDA = "agenda_set"
        const val HOME_MORE_MOGAKCO_ME = "mogakco_me"
        const val HOME_MORE_SETTINGS_HELP = "settings_help"
    }
}
