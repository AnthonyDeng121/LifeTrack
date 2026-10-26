package com.lifetrack.entity;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.TimeZone;
import static org.junit.jupiter.api.Assertions.*;

class BusinessTimestampTest {
    @Test void entityHooksUseShanghaiEvenWhenHostUsesUtc() {
        TimeZone previous=TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            LocalDateTime before=LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusSeconds(1);
            Task t=new Task(); t.onCreate();
            User u=new User(); u.onCreate();
            UserProfile p=new UserProfile(); p.onUpdate();
            CheckInRecord c=new CheckInRecord(); c.onCreate();
            for(LocalDateTime date: java.util.List.of(t.getCreatedAt(),u.getCreatedAt(),p.getUpdatedAt(),c.getCreatedAt())) {
                assertTrue(date.isAfter(before));
                assertTrue(date.isBefore(LocalDateTime.now(ZoneId.of("Asia/Shanghai")).plusSeconds(1)));
            }
        } finally { TimeZone.setDefault(previous); }
    }
}
