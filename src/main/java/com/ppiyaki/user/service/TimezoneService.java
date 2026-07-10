package com.ppiyaki.user.service;

import com.ppiyaki.user.controller.dto.TimezoneResponse;
import com.ppiyaki.user.domain.SupportedTimezone;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TimezoneService {

    private final Clock clock;

    public TimezoneService(final Clock clock) {
        this.clock = clock;
    }

    public List<TimezoneResponse> readTimezones() {
        final Instant now = Instant.now(clock);
        return Arrays.stream(SupportedTimezone.values())
                .map(supportedTimezone -> toTimezoneResponse(supportedTimezone, now))
                .toList();
    }

    private TimezoneResponse toTimezoneResponse(final SupportedTimezone supportedTimezone, final Instant now) {
        final ZoneOffset zoneOffset = supportedTimezone.toZoneId().getRules().getOffset(now);
        return new TimezoneResponse(
                supportedTimezone.getZoneIdName(),
                supportedTimezone.getCityLabel() + " (UTC" + formatOffset(zoneOffset) + ")");
    }

    private String formatOffset(final ZoneOffset zoneOffset) {
        final int totalMinutes = zoneOffset.getTotalSeconds() / 60;
        final int hours = totalMinutes / 60;
        final int minutes = Math.abs(totalMinutes % 60);
        final String sign = totalMinutes < 0 ? "-" : "+";
        if (minutes == 0) {
            return sign + Math.abs(hours);
        }
        return sign + Math.abs(hours) + ":" + String.format("%02d", minutes);
    }
}
