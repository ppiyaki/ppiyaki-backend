package com.ppiyaki.user.domain;

import java.time.ZoneId;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SupportedTimezone {

    SEOUL("Asia/Seoul", "서울"),
    TOKYO("Asia/Tokyo", "도쿄"),
    SHANGHAI("Asia/Shanghai", "상하이"),
    HONG_KONG("Asia/Hong_Kong", "홍콩"),
    TAIPEI("Asia/Taipei", "타이베이"),
    SINGAPORE("Asia/Singapore", "싱가포르"),
    BANGKOK("Asia/Bangkok", "방콕"),
    JAKARTA("Asia/Jakarta", "자카르타"),
    MANILA("Asia/Manila", "마닐라"),
    HANOI("Asia/Ho_Chi_Minh", "호치민"),
    KOLKATA("Asia/Kolkata", "뉴델리"),
    DUBAI("Asia/Dubai", "두바이"),
    MOSCOW("Europe/Moscow", "모스크바"),
    ISTANBUL("Europe/Istanbul", "이스탄불"),
    BERLIN("Europe/Berlin", "베를린"),
    PARIS("Europe/Paris", "파리"),
    MADRID("Europe/Madrid", "마드리드"),
    ROME("Europe/Rome", "로마"),
    LONDON("Europe/London", "런던"),
    NEW_YORK("America/New_York", "뉴욕"),
    TORONTO("America/Toronto", "토론토"),
    CHICAGO("America/Chicago", "시카고"),
    DENVER("America/Denver", "덴버"),
    LOS_ANGELES("America/Los_Angeles", "로스앤젤레스"),
    VANCOUVER("America/Vancouver", "밴쿠버"),
    MEXICO_CITY("America/Mexico_City", "멕시코시티"),
    SAO_PAULO("America/Sao_Paulo", "상파울루"),
    SYDNEY("Australia/Sydney", "시드니"),
    AUCKLAND("Pacific/Auckland", "오클랜드");

    private final String zoneIdName;
    private final String cityLabel;

    public ZoneId toZoneId() {
        return ZoneId.of(this.zoneIdName);
    }

    public static boolean isSupported(final String zoneIdName) {
        return Arrays.stream(values())
                .anyMatch(supportedTimezone -> supportedTimezone.zoneIdName.equals(zoneIdName));
    }
}
