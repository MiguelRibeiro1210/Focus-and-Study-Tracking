package com.focusdesktop.domain.model;

/** Stable, namespaced identities; never executable commands or filesystem paths. */
public final class ApplicationIds {
    private ApplicationIds() { }
    public static String validate(TargetPlatform platform, String value) {
        if (value == null || value.length() > 512 || !value.matches("[a-z]+:[A-Za-z0-9][A-Za-z0-9._!-]*"))
            throw new IllegalArgumentException("Invalid application identity");
        String prefix = value.substring(0, value.indexOf(':'));
        String id = value.substring(value.indexOf(':') + 1);
        boolean valid = switch (platform) {
            case WINDOWS -> prefix.equals("windows") && id.matches("[A-Za-z0-9._-]+![A-Za-z0-9._-]+");
            case MACOS -> prefix.equals("macos") && id.matches("[A-Za-z0-9._-]+");
            case LINUX -> (prefix.equals("flatpak") && id.matches("[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+){2,}"))
                    || (prefix.equals("snap") && id.matches("[a-z0-9_-]+\\.[A-Za-z0-9_-]+"));
        };
        if (!valid) throw new IllegalArgumentException("Application identity does not match its platform");
        return value;
    }
}
