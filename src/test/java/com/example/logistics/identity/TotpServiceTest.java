package com.example.logistics.identity;

import com.example.logistics.identity.application.TotpService;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import static org.assertj.core.api.Assertions.assertThat;

class TotpServiceTest {
    @Test void verifiesRfc6238Sha1SixDigitVector() {
        var service = new TotpService(Clock.fixed(Instant.ofEpochSecond(59), ZoneOffset.UTC));
        assertThat(service.verify("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", "287082")).isTrue();
        assertThat(service.verify("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", "000000")).isFalse();
        assertThat(service.verify("bad", "12x")).isFalse();
    }
}
