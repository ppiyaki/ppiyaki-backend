package com.ppiyaki.notification;

import com.ppiyaki.user.User;
import com.ppiyaki.user.UserRole;
import com.ppiyaki.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class SeniorActivityInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(SeniorActivityInterceptor.class);
    private static final long THROTTLE_SECONDS = 60;

    private final UserRepository userRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final ConcurrentMap<Long, LocalDateTime> lastTouchInMemory = new ConcurrentHashMap<>();

    public SeniorActivityInterceptor(
            final UserRepository userRepository,
            final PlatformTransactionManager transactionManager,
            final Clock clock
    ) {
        this.userRepository = userRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Override
    public boolean preHandle(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final Object handler
    ) {
        final Long userId = resolveAuthenticatedUserId();
        if (userId == null) {
            return true;
        }
        final LocalDateTime now = LocalDateTime.now(clock);
        final LocalDateTime cached = lastTouchInMemory.get(userId);
        if (cached != null && cached.plusSeconds(THROTTLE_SECONDS).isAfter(now)) {
            return true;
        }
        lastTouchInMemory.put(userId, now);
        try {
            transactionTemplate.executeWithoutResult(status -> {
                final User user = userRepository.findById(userId).orElse(null);
                if (user != null && user.getRole() == UserRole.SENIOR) {
                    user.touchActiveAt(now);
                }
            });
        } catch (final RuntimeException ex) {
            log.warn("SeniorActivityInterceptor failed to touch lastActiveAt for userId={}", userId, ex);
        }
        return true;
    }

    private Long resolveAuthenticatedUserId() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        final Object principal = auth.getPrincipal();
        if (principal instanceof Long longId) {
            return longId;
        }
        return null;
    }
}
