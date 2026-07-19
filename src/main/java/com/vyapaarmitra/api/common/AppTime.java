package com.vyapaarmitra.api.common;

import com.vyapaarmitra.api.config.AppProperties;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Service;

/**
 * Single source of truth for "today" in the business's timezone (default
 * Asia/Kolkata). Never derive shop-local dates from UTC instants directly.
 */
@Service
public class AppTime {

    private final ZoneId zone;

    public AppTime(AppProperties props) {
        this.zone = ZoneId.of(props.timezone());
    }

    public ZoneId zone() {
        return zone;
    }

    public LocalDate today() {
        return LocalDate.now(zone);
    }

    public Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(zone).toInstant();
    }
}
