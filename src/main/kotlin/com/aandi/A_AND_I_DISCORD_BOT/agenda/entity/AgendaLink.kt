package com.aandi.A_AND_I_DISCORD_BOT.agenda.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(
    name = "agenda_links",
    uniqueConstraints = [UniqueConstraint(name = "uq_agenda_links_guild_date", columnNames = ["guild_id", "date_local"])],
)
class AgendaLink(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Column(name = "guild_id", nullable = false)
    var guildId: Long = 0,

    @Column(name = "date_local", nullable = false)
    var dateLocal: LocalDate = LocalDate.now(),

    @Column(name = "title", nullable = false, length = 255)
    var title: String = "",

    @Column(name = "url", nullable = false)
    var url: String = "",

    @Column(name = "created_by", nullable = false)
    var createdBy: Long = 0,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
