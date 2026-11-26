package com.focusdesktop.domain.model;

import java.util.Objects;

/** Durable observation facts only; no OS handles, URL queries or browser credentials. */
public sealed interface ObservedDistraction
        permits ObservedDistraction.Application, ObservedDistraction.Website {
    static Application application(AppIdentifier identifier) { return new Application(identifier); }

    static Website website(String host) { return new Website(host); }

    record Application(AppIdentifier identifier) implements ObservedDistraction {
        public Application { Objects.requireNonNull(identifier, "identifier"); }
    }

    record Website(String host) implements ObservedDistraction {
        public Website { host = WebsiteTarget.normalizeDomain(host); }
    }
}
