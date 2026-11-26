package com.focusdesktop.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class WebsiteTargetTest {
    @ParameterizedTest
    @ValueSource(strings = {"youtube.com", "www.youtube.com", "music.youtube.com", "a.b.youtube.com"})
    void matchesTheConfiguredDomainAndEverySubdomain(String host) {
        assertTrue(new WebsiteTarget("youtube.com").matchesHost(host));
    }

    @ParameterizedTest
    @ValueSource(strings = {"notyoutube.com", "youtube.com.example.org", "youtubecom", "example.org"})
    void rejectsHostsThatOnlyContainSimilarText(String host) {
        assertFalse(new WebsiteTarget("youtube.com").matchesHost(host));
    }

    @Test
    void normalizesCaseTerminalDnsDotAndInternationalizedNames() {
        WebsiteTarget target = new WebsiteTarget("YouTube.COM.");
        assertEquals("youtube.com", target.domain());
        assertEquals(target, new WebsiteTarget("youtube.com"));
        assertTrue(target.matchesHost("Music.YouTube.COM."));

        WebsiteTarget international = new WebsiteTarget("bücher.example");
        assertEquals("xn--bcher-kva.example", international.domain());
        assertEquals(international, new WebsiteTarget("XN--BCHER-KVA.EXAMPLE."));
        assertTrue(international.matchesHost("shop.bücher.example"));
        assertEquals(international, new WebsiteTarget("bücher.example\u3002"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", " ", "https://youtube.com", "youtube.com/watch", "youtube.com:443",
            "*.youtube.com", "user@youtube.com", "youtube.com?next=example.org", "youtube.com#home",
            "127.0.0.1", "[::1]", "bad..example", "-bad.example", "bad-.example", "bad host.example"
    })
    void rejectsConfigurationThatIsNotABareDnsDomain(String value) {
        assertThrows(IllegalArgumentException.class, () -> new WebsiteTarget(value));
    }

    @Test
    void rejectsNullAndOversizedDnsLabels() {
        assertThrows(NullPointerException.class, () -> new WebsiteTarget(null));
        assertThrows(IllegalArgumentException.class, () -> new WebsiteTarget("a".repeat(64) + ".example"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "https://youtube.com", "youtube.com/watch", "youtube.com:443"})
    void invalidObservationsCannotBecomeDomainMatches(String host) {
        // Missing or malformed adapter data must not accidentally select a blocking rule.
        assertFalse(new WebsiteTarget("youtube.com").matchesHost(host));
    }
}
