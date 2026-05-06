package com.ppiyaki.user;

import com.ppiyaki.common.entity.CreatedTimeEntity;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Getter
@Entity
@Table(name = "invite_codes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InviteCode extends CreatedTimeEntity {

    private static final int CODE_LENGTH = 6;
    private static final long EXPIRY_MINUTES = 5;
    private static final String CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "senior_id", nullable = false)
    private Long seniorId;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    private InviteCode(final Long seniorId, final String codeHash, final LocalDateTime expiresAt) {
        this.seniorId = Objects.requireNonNull(seniorId, "seniorId must not be null");
        this.codeHash = Objects.requireNonNull(codeHash, "codeHash must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    public static InviteCodeWithRaw create(final Long seniorId, final LocalDateTime now) {
        Objects.requireNonNull(seniorId, "seniorId must not be null");
        Objects.requireNonNull(now, "now must not be null");
        final String rawCode = generateCode();
        final String hash = ENCODER.encode(rawCode);
        final InviteCode inviteCode = new InviteCode(seniorId, hash, now.plusMinutes(EXPIRY_MINUTES));
        return new InviteCodeWithRaw(inviteCode, rawCode);
    }

    public boolean matches(final String rawCode) {
        return ENCODER.matches(rawCode, this.codeHash);
    }

    public boolean isExpired(final LocalDateTime now) {
        return now.isAfter(this.expiresAt);
    }

    public boolean isUsed() {
        return this.usedAt != null;
    }

    public void markUsed(final LocalDateTime now) {
        this.usedAt = now;
    }

    private static String generateCode() {
        final StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }

    public record InviteCodeWithRaw(
            InviteCode inviteCode,
            String rawCode
    ) {
    }
}
