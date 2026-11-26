package com.focusdesktop.domain.model;

/** A configured target, independent of any OS or browser integration. */
public sealed interface DistractionTarget permits ApplicationTarget, WebsiteTarget {
    boolean matches(ObservedDistraction observation);

    int specificity();
}
