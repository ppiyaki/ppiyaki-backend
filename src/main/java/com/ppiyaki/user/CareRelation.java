package com.ppiyaki.user;

import com.ppiyaki.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "care_relations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CareRelation extends BaseTimeEntity {

    private static final int INVITE_CODE_LENGTH = 6;
    private static final long INVITE_CODE_EXPIRY_MINUTES = 5;
    private static final String INVITE_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "senior_id")
    private Long seniorId;

    @Column(name = "caregiver_id")
    private Long caregiverId;

    @Column(name = "invite_code")
    private String inviteCode;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public CareRelation(final Long seniorId, final Long caregiverId, final String inviteCode) {
        Objects.requireNonNull(seniorId, "seniorId must not be null");
        Objects.requireNonNull(caregiverId, "caregiverId must not be null");
        Objects.requireNonNull(inviteCode, "inviteCode must not be null");
        this.seniorId = seniorId;
        this.caregiverId = caregiverId;
        this.inviteCode = inviteCode;
    }

    public static CareRelation createInvite(final Long seniorId, final LocalDateTime now) {
        Objects.requireNonNull(seniorId, "seniorId must not be null");
        Objects.requireNonNull(now, "now must not be null");
        final CareRelation careRelation = new CareRelation();
        careRelation.seniorId = seniorId;
        careRelation.inviteCode = generateInviteCode();
        careRelation.expiresAt = now.plusMinutes(INVITE_CODE_EXPIRY_MINUTES);
        return careRelation;
    }

    public void acceptInvite(final Long caregiverId) {
        Objects.requireNonNull(caregiverId, "caregiverId must not be null");
        this.caregiverId = caregiverId;
        this.inviteCode = null;
        this.expiresAt = null;
    }

    public boolean isExpired(final LocalDateTime now) {
        return this.expiresAt != null && now.isAfter(this.expiresAt);
    }

    public boolean isPending() {
        return this.caregiverId == null && this.inviteCode != null;
    }

    public void softDelete(final LocalDateTime now) {
        this.deletedAt = now;
    }

    public boolean isActive() {
        return this.deletedAt == null;
    }

    private static String generateInviteCode() {
        final StringBuilder sb = new StringBuilder(INVITE_CODE_LENGTH);
        for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
            sb.append(INVITE_CODE_CHARS.charAt(RANDOM.nextInt(INVITE_CODE_CHARS.length())));
        }
        return sb.toString();
    }
}
