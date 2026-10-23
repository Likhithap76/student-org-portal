package com.studentorgs.portal.notification.domain;

import java.time.Instant;

import com.studentorgs.portal.common.client.NotificationKind;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One in-app notification for one recipient ({@code notification}). Unread while {@code read_at} is null. */
@Entity
@Table(name = "notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Convert(converter = NotificationKindConverter.class)
    @Column(name = "kind", nullable = false, length = 30)
    private NotificationKind kind;

    @Column(name = "message", nullable = false, length = 500)
    private String message;

    @Column(name = "reference_table", nullable = false, length = 50)
    private String referenceTable;

    @Column(name = "reference_id", nullable = false)
    private Long referenceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    public Notification(Long recipientId, NotificationKind kind, String message, String referenceTable,
            Long referenceId, Instant createdAt) {
        this.recipientId = recipientId;
        this.kind = kind;
        this.message = message;
        this.referenceTable = referenceTable;
        this.referenceId = referenceId;
        this.createdAt = createdAt;
    }

    /** Marks as read; reading again keeps the first read time. */
    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }
}
