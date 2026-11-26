package com.focusdesktop.domain.model;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Objects;

public final class ApplicationTarget implements DistractionTarget {
    private final TargetPlatform platform;
    private final ApplicationMatchKind matchKind;
    private final String value;
    private final String canonicalValue;

    public ApplicationTarget(TargetPlatform platform, ApplicationMatchKind matchKind, String value) {
        this.platform = Objects.requireNonNull(platform, "platform");
        this.matchKind = Objects.requireNonNull(matchKind, "matchKind");
        this.value = Objects.requireNonNull(value, "value").trim();
        this.canonicalValue = normalize(platform, matchKind, this.value);
    }

    static String normalize(TargetPlatform platform, ApplicationMatchKind kind, String value) {
        String normalized = Objects.requireNonNull(value, "value").trim();
        if (normalized.isEmpty() || normalized.chars().anyMatch(Character::isISOControl)
                || normalized.indexOf('*') >= 0 || normalized.indexOf('?') >= 0) {
            throw new IllegalArgumentException("Application identifier must be a nonempty literal value");
        }
        if (platform == TargetPlatform.WINDOWS) {
            String windowsValue = normalized.replace('\\', '/');
            String withoutDrive = kind == ApplicationMatchKind.EXECUTABLE_PATH
                    && windowsValue.matches("^[A-Za-z]:/.*") ? windowsValue.substring(2) : windowsValue;
            if (withoutDrive.chars().anyMatch(character -> "<>:\"|".indexOf(character) >= 0)) {
                throw new IllegalArgumentException("Invalid Windows application identifier");
            }
        }
        if (kind == ApplicationMatchKind.EXECUTABLE_PATH) {
            normalized = normalizePath(platform, normalized);
        } else if (normalized.contains("/") || normalized.contains("\\")
                || normalized.equals(".") || normalized.equals("..")) {
            throw new IllegalArgumentException("A process name must be one executable name, not a path");
        }
        if (platform == TargetPlatform.WINDOWS) {
            normalized = normalized.toLowerCase(Locale.ROOT);
        }
        return normalized;
    }

    private static String normalizePath(TargetPlatform platform, String path) {
        String remainder = path;
        String root;
        if (platform == TargetPlatform.WINDOWS) {
            remainder = remainder.replace('\\', '/');
            if (remainder.matches("^[A-Za-z]:/.*")) {
                root = remainder.substring(0, 3);
                remainder = remainder.substring(3);
            } else if (remainder.startsWith("//")) {
                String[] network = remainder.substring(2).split("/", 3);
                if (network.length != 3 || network[0].isEmpty() || network[1].isEmpty()
                        || network[0].equals(".") || network[0].equals("..")
                        || network[1].equals(".") || network[1].equals("..")) {
                    throw new IllegalArgumentException("A network executable path needs a server, share and file");
                }
                root = "//" + network[0] + "/" + network[1] + "/";
                remainder = network[2];
            } else {
                throw new IllegalArgumentException("Windows executable path must be absolute");
            }
        } else {
            if (!remainder.startsWith("/")) {
                throw new IllegalArgumentException("Executable path must be absolute");
            }
            root = "/";
            remainder = remainder.substring(1);
        }
        var segments = new ArrayDeque<String>();
        for (String segment : remainder.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".")) continue;
            if (segment.equals("..")) {
                if (segments.isEmpty()) {
                    throw new IllegalArgumentException("Executable path cannot traverse above its root");
                }
                segments.removeLast();
            } else {
                segments.addLast(segment);
            }
        }
        if (segments.isEmpty()) throw new IllegalArgumentException("Executable path must identify a file");
        return root + String.join("/", segments);
    }

    public boolean matches(AppIdentifier observation) {
        if (observation == null || observation.platform() != platform) return false;
        String identifier = matchKind == ApplicationMatchKind.EXECUTABLE_PATH
                ? observation.executablePath() : observation.processName();
        return identifier != null && canonicalValue.equals(normalize(platform, matchKind, identifier));
    }

    @Override
    public boolean matches(ObservedDistraction observation) {
        return observation instanceof ObservedDistraction.Application application && matches(application.identifier());
    }

    @Override
    public int specificity() { return matchKind == ApplicationMatchKind.EXECUTABLE_PATH ? 1 : 0; }

    public TargetPlatform platform() { return platform; }
    public ApplicationMatchKind matchKind() { return matchKind; }
    public String value() { return value; }

    @Override
    public boolean equals(Object other) {
        return other instanceof ApplicationTarget target && platform == target.platform
                && matchKind == target.matchKind && canonicalValue.equals(target.canonicalValue);
    }

    @Override
    public int hashCode() { return Objects.hash(platform, matchKind, canonicalValue); }
}
