package com.focusdesktop.domain.model;

import java.net.IDN;
import java.util.Locale;
import java.util.Objects;

public record WebsiteTarget(String domain) implements DistractionTarget {
    public WebsiteTarget {
        domain = normalizeDomain(domain);
    }

    static String normalizeDomain(String value) {
        Objects.requireNonNull(value, "domain");
        String normalized = IDN.toASCII(value.trim(), IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isEmpty() || normalized.length() > 253 || normalized.endsWith(".")
                || normalized.matches("[0-9.]+")) {
            throw new IllegalArgumentException("Website target must be a DNS domain, not a URL or IP address");
        }
        for (String label : normalized.split("\\.", -1)) {
            if (label.isEmpty() || label.length() > 63) {
                throw new IllegalArgumentException("Invalid DNS domain label");
            }
        }
        return normalized;
    }

    public boolean matchesHost(String host) {
        if (host == null) return false;
        try {
            String normalized = normalizeDomain(host);
            return normalized.equals(domain) || normalized.endsWith("." + domain);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    @Override
    public boolean matches(ObservedDistraction observation) {
        return observation instanceof ObservedDistraction.Website website && matchesHost(website.host());
    }

    @Override
    public int specificity() { return domain.length(); }
}
