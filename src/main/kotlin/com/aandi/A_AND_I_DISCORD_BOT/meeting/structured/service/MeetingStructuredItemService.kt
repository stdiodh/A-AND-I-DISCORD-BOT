package com.aandi.A_AND_I_DISCORD_BOT.meeting.structured.service

import com.aandi.A_AND_I_DISCORD_BOT.meeting.structured.entity.MeetingStructuredItemEntity
import com.aandi.A_AND_I_DISCORD_BOT.meeting.structured.entity.MeetingStructuredItemType
import com.aandi.A_AND_I_DISCORD_BOT.meeting.structured.repository.MeetingStructuredItemRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate

@Service
@ConditionalOnProperty(name = ["discord.enabled"], havingValue = "true", matchIfMissing = true)
class MeetingStructuredItemService(
    private val meetingStructuredItemRepository: MeetingStructuredItemRepository,
) {

    fun addDecision(
        meetingSessionId: Long,
        guildId: Long,
        threadId: Long,
        content: String,
        createdBy: Long,
    ): MeetingStructuredItemEntity {
        val nowUtc = Instant.now()
        return meetingStructuredItemRepository.save(
            MeetingStructuredItemEntity(
                meetingSessionId = meetingSessionId,
                guildId = guildId,
                threadId = threadId,
                itemType = MeetingStructuredItemType.DECISION,
                content = content.trim(),
                createdBy = createdBy,
                createdAt = nowUtc,
                updatedAt = nowUtc,
            ),
        )
    }

    fun addAction(
        meetingSessionId: Long,
        guildId: Long,
        threadId: Long,
        content: String,
        assigneeUserId: Long?,
        dueDateLocal: LocalDate?,
        createdBy: Long,
    ): MeetingStructuredItemEntity {
        val nowUtc = Instant.now()
        return meetingStructuredItemRepository.save(
            MeetingStructuredItemEntity(
                meetingSessionId = meetingSessionId,
                guildId = guildId,
                threadId = threadId,
                itemType = MeetingStructuredItemType.ACTION,
                content = content.trim(),
                assigneeUserId = assigneeUserId,
                dueDateLocal = dueDateLocal,
                createdBy = createdBy,
                createdAt = nowUtc,
                updatedAt = nowUtc,
            ),
        )
    }

    fun addTodo(
        meetingSessionId: Long,
        guildId: Long,
        threadId: Long,
        content: String,
        createdBy: Long,
    ): MeetingStructuredItemEntity {
        val nowUtc = Instant.now()
        return meetingStructuredItemRepository.save(
            MeetingStructuredItemEntity(
                meetingSessionId = meetingSessionId,
                guildId = guildId,
                threadId = threadId,
                itemType = MeetingStructuredItemType.TODO,
                content = content.trim(),
                createdBy = createdBy,
                createdAt = nowUtc,
                updatedAt = nowUtc,
            ),
        )
    }

    fun listSummaryItems(meetingSessionId: Long): SummaryItems {
        val items = meetingStructuredItemRepository.findByMeetingSessionIdAndCanceledAtIsNullOrderByCreatedAtAsc(meetingSessionId)
        val decisions = items
            .filter { it.itemType == MeetingStructuredItemType.DECISION }
            .map { it.content.trim() }
            .filter { it.isNotBlank() }
        val actions = items
            .filter { it.itemType == MeetingStructuredItemType.ACTION }
            .map { formatActionLine(it) }
            .filter { it.isNotBlank() }
        val todos = items
            .filter { it.itemType == MeetingStructuredItemType.TODO }
            .map { it.content.trim() }
            .filter { it.isNotBlank() }
        return SummaryItems(
            decisions = decisions,
            actions = actions,
            todos = todos,
        )
    }

    fun listActiveItems(meetingSessionId: Long): List<ItemLine> {
        return meetingStructuredItemRepository.findByMeetingSessionIdAndCanceledAtIsNullOrderByCreatedAtAsc(meetingSessionId)
            .map { item ->
                val summary = when (item.itemType) {
                    MeetingStructuredItemType.DECISION -> item.content.trim()
                    MeetingStructuredItemType.ACTION -> formatActionLine(item)
                    MeetingStructuredItemType.TODO -> item.content.trim()
                }
                ItemLine(
                    id = item.id ?: 0,
                    type = item.itemType,
                    summary = summary,
                )
            }
            .filter { it.id > 0 && it.summary.isNotBlank() }
    }

    fun cancelItem(
        meetingSessionId: Long,
        itemId: Long,
        canceledBy: Long,
    ): MeetingStructuredItemEntity? {
        val item = meetingStructuredItemRepository.findByMeetingSessionIdAndIdAndCanceledAtIsNull(meetingSessionId, itemId)
            ?: return null
        val nowUtc = Instant.now()
        item.canceledBy = canceledBy
        item.canceledAt = nowUtc
        item.updatedAt = nowUtc
        return meetingStructuredItemRepository.save(item)
    }

    fun formatActionLine(
        content: String,
        assigneeUserId: Long?,
        dueDateLocal: LocalDate?,
    ): String {
        val assignee = assigneeUserId?.let { "<@$it>" } ?: "담당 미정"
        val dueDate = dueDateLocal?.toString() ?: "기한 미정"
        return "${content.trim()} (담당: $assignee, 기한: $dueDate)"
    }

    private fun formatActionLine(item: MeetingStructuredItemEntity): String {
        return formatActionLine(
            content = item.content,
            assigneeUserId = item.assigneeUserId,
            dueDateLocal = item.dueDateLocal,
        )
    }

    data class SummaryItems(
        val decisions: List<String>,
        val actions: List<String>,
        val todos: List<String>,
    )

    data class ItemLine(
        val id: Long,
        val type: MeetingStructuredItemType,
        val summary: String,
    )
}
