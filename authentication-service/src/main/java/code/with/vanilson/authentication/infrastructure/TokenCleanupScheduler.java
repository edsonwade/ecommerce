package code.with.vanilson.authentication.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * TokenCleanupScheduler — Infrastructure Layer
 * <p>
 * Daily scheduled job that removes expired and revoked tokens older than 30 days.
 * Prevents unbounded growth of the token table.
 * Runs at 02:00 every day (low-traffic window).
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenCleanupScheduler {

    private final TokenRepository tokenRepository;
    private final MessageSource   messageSource;

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        log.info(messageSource.getMessage(
                "auth.log.token.cleanup", new Object[]{cutoff}, LocaleContextHolder.getLocale()));
        tokenRepository.deleteExpiredTokensBefore(cutoff);
    }
}
