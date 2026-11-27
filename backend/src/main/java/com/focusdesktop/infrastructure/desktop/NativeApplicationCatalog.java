package com.focusdesktop.infrastructure.desktop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.focusdesktop.application.dto.BlockingRuleDto;
import com.focusdesktop.application.port.ApplicationCatalog;
import com.focusdesktop.domain.model.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Read-only, bounded discovery. Launcher contents are data, never shell programs. */
@Component
public class NativeApplicationCatalog implements ApplicationCatalog {
    private final TargetPlatform platform;
    private final Path home;
    private final Map<String, String> environment;
    private final NativeCommands commands;
    private final ObjectMapper mapper;
    private static final Set<String> SHARED = Set.of("env", "sh", "bash", "dash", "zsh", "fish", "python", "python3", "perl", "ruby", "node", "java", "javaw", "flatpak", "snap", "bwrap", "xdg-open", "gio", "cmd", "powershell", "pwsh", "wscript", "cscript", "rundll32", "explorer", "applicationframehost", "update");
    @Autowired public NativeApplicationCatalog(ObjectMapper mapper) {
        this(System.getProperty("os.name"), Path.of(System.getProperty("user.home")), System.getenv(),
                args -> NativeCommands.execute(args, false, 12), mapper);
    }
    public NativeApplicationCatalog(String os, Path home, Map<String, String> environment, NativeCommands commands, ObjectMapper mapper) {
        String name = os.toLowerCase(Locale.ROOT);
        platform = name.contains("win") ? TargetPlatform.WINDOWS : name.contains("mac") ? TargetPlatform.MACOS : TargetPlatform.LINUX;
        this.home = home; this.environment = Map.copyOf(environment); this.commands = commands; this.mapper = mapper;
    }
    @Override public Scan scan() {
        var entries = new LinkedHashMap<String, Entry>(); var warnings = new LinkedHashSet<String>();
        if (platform == TargetPlatform.LINUX) linux(entries, warnings);
        else {
            try {
                String output = commands.run(platform == TargetPlatform.WINDOWS ? NativePowerShell.command(NativePowerShell.resource("list-applications.ps1"))
                        : List.of("/usr/bin/osascript", "-l", "JavaScript", "-e", NativePowerShell.resource("list-applications.js")));
                var root = mapper.readTree(output);
                if (!root.path("applications").isArray()) throw new IOException("Invalid catalog response");
                for (var item : root.path("applications")) {
                    if (entries.size() >= 4096) { warnings.add("Some applications were omitted because the discovery limit was reached."); break; }
                    String name = item.path("displayName").asText(), source = item.path("source").asText();
                    String id = item.path("applicationId").asText(""); String path = item.path("executable").asText("");
                    ApplicationTarget target = null;
                    try {
                        if (!id.isBlank()) target = new ApplicationTarget(platform, ApplicationMatchKind.APPLICATION_ID, id);
                    } catch (IllegalArgumentException ignored) { }
                    try {
                        if (target == null && !path.isBlank() && !shared(path)) target = new ApplicationTarget(platform, ApplicationMatchKind.EXECUTABLE_PATH, path);
                    } catch (IllegalArgumentException ignored) { }
                    add(entries, name, source, target, item.path("key").asText(path + id));
                }
                for (var warning : root.path("warnings")) if (warning.isTextual()) warnings.add(warning.asText());
            } catch (Exception exception) { warnings.add("Some installed applications could not be read. Refresh or add an app manually."); }
        }
        var sorted = entries.values().stream().sorted(Comparator.comparing(Entry::displayName, String.CASE_INSENSITIVE_ORDER).thenComparing(Entry::source)).toList();
        return new Scan(platform, sorted, !warnings.isEmpty(), List.copyOf(warnings), Instant.now());
    }
    private void linux(Map<String, Entry> entries, Set<String> warnings) {
        var roots = new LinkedHashSet<Path>();
        roots.add(Path.of(environment.getOrDefault("XDG_DATA_HOME", home.resolve(".local/share").toString())).resolve("applications"));
        for (String root : environment.getOrDefault("XDG_DATA_DIRS", "/usr/local/share:/usr/share").split(":")) if (!root.isBlank() && Path.of(root).isAbsolute()) roots.add(Path.of(root).resolve("applications"));
        roots.add(home.resolve(".local/share/flatpak/exports/share/applications"));
        roots.add(Path.of("/var/lib/flatpak/exports/share/applications")); roots.add(Path.of("/var/lib/snapd/desktop/applications"));
        Set<String> seen = new HashSet<>(); int visited = 0; long deadline = System.nanoTime() + 8_000_000_000L;
        for (Path root : roots) {
            if (!Files.exists(root)) continue;
            try (var files = Files.walk(root, 3)) {
                var iterator = files.iterator();
                while (iterator.hasNext()) {
                    if (++visited > 10_000 || entries.size() >= 4096 || System.nanoTime() >= deadline) { warnings.add("Some applications were omitted because the discovery limit was reached."); return; }
                    Path file = iterator.next();
                    if (!file.toString().endsWith(".desktop") || !Files.isRegularFile(file)) continue;
                    String desktopId = root.relativize(file).toString().replace('/', '-');
                    if (!seen.add(desktopId)) continue;
                    try {
                        var data = desktop(file);
                        if (!"Application".equals(data.get("Type")) || "true".equals(data.get("Hidden")) || "true".equals(data.get("NoDisplay"))) continue;
                        String name = data.getOrDefault("Name", "");
                        String source = "Desktop app"; ApplicationTarget target = null;
                        try {
                            if (data.containsKey("X-Flatpak")) {
                                source = "Flatpak"; target = new ApplicationTarget(platform, ApplicationMatchKind.APPLICATION_ID, "flatpak:" + data.get("X-Flatpak"));
                            } else {
                                var tokens = execTokens(data.getOrDefault("Exec", ""));
                                int start = 0;
                                if (!tokens.isEmpty() && base(tokens.get(0)).equals("env")) {
                                    start = 1; while (start < tokens.size() && tokens.get(start).matches("[A-Za-z_][A-Za-z0-9_]*=.*")) start++;
                                }
                                String command = start < tokens.size() ? tokens.get(start) : "";
                                // A browser web-app shortcut is not the entire browser application.
                                if (tokens.stream().anyMatch(token -> token.startsWith("--app-id=") || token.startsWith("--app=") || token.matches("[A-Za-z][A-Za-z0-9+.-]*://.*"))) command = "";
                                if (command.startsWith("/snap/bin/") && (data.containsKey("X-SnapInstanceName") || root.toString().equals("/var/lib/snapd/desktop/applications"))) {
                                    source = "Snap"; String app = base(command);
                                    String instance = data.getOrDefault("X-SnapInstanceName", app.contains(".") ? app.substring(0, app.indexOf('.')) : app);
                                    if (app.equals(instance)) app += "." + instance;
                                    if (app.startsWith(instance + ".")) target = new ApplicationTarget(platform, ApplicationMatchKind.APPLICATION_ID, "snap:" + app);
                                } else if (base(command).equals("flatpak")) {
                                    source = "Flatpak";
                                    for (String token : tokens) if (token.matches("[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+){2,}")) { target = new ApplicationTarget(platform, ApplicationMatchKind.APPLICATION_ID, "flatpak:" + token); break; }
                                } else {
                                    Path executable = executable(command);
                                    if (executable != null && !shared(executable.toString())) target = new ApplicationTarget(platform, ApplicationMatchKind.EXECUTABLE_PATH, executable.toString());
                                }
                            }
                        } catch (IllegalArgumentException ignored) { }
                        add(entries, name, source, target, file.toString());
                    } catch (IOException | RuntimeException exception) { warnings.add("Some application shortcuts could not be read. Add missing apps manually."); }
                }
            } catch (IOException | RuntimeException exception) { warnings.add("Some application folders could not be read. Add missing apps manually."); }
        }
    }
    static Map<String, String> desktop(Path file) throws IOException {
        return desktopLike(file, "Desktop Entry");
    }
    static Map<String, String> desktopLike(Path file, String sectionName) throws IOException {
        if (Files.size(file) > 65_536) throw new IOException("Oversized desktop entry");
        var values = new LinkedHashMap<String, String>(); boolean section = false;
        for (String line : Files.readAllLines(file)) {
            line = line.trim();
            if (line.startsWith("[")) { section = line.equals("[" + sectionName + "]"); continue; }
            int equals = line.indexOf('=');
            if (section && !line.startsWith("#") && equals > 0) values.put(line.substring(0, equals), line.substring(equals + 1));
        }
        return values;
    }
    static List<String> execTokens(String command) {
        var tokens = new ArrayList<String>(); var token = new StringBuilder(); boolean quoted = false, escaped = false;
        for (char value : command.toCharArray()) {
            if (escaped) { token.append(value); escaped = false; }
            else if (value == '\\') escaped = true;
            else if (value == '"') quoted = !quoted;
            else if (Character.isWhitespace(value) && !quoted) { if (!token.isEmpty()) { tokens.add(token.toString()); token.setLength(0); } }
            else token.append(value);
        }
        if (quoted || escaped) throw new IllegalArgumentException("Invalid desktop command");
        if (!token.isEmpty()) tokens.add(token.toString());
        return tokens;
    }
    private Path executable(String command) {
        if (command.isBlank() || shared(command) || command.contains("%")) return null;
        try {
            Path path = Path.of(command);
            if (!path.isAbsolute()) {
                path = null;
                for (String directory : environment.getOrDefault("PATH", "/usr/local/bin:/usr/bin:/bin").split(":")) {
                    if (directory.isBlank() || !Path.of(directory).isAbsolute()) continue;
                    Path candidate = Path.of(directory).resolve(command);
                    if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) { path = candidate; break; }
                }
            }
            if (path == null || !Files.isRegularFile(path) || !Files.isExecutable(path)) return null;
            path = path.toRealPath(); if (elf(path)) return path;
            String name = base(command);
            var candidates = new ArrayList<>(List.of("/opt/" + name + "/" + name, "/usr/lib/" + name + "/" + name, "/usr/share/" + name + "/" + name));
            if (name.startsWith("brave")) candidates.addAll(List.of("/opt/brave-bin/brave", "/opt/brave.com/brave/brave"));
            if (name.startsWith("google-chrome")) candidates.add("/opt/google/chrome/chrome");
            for (String candidate : candidates) if (Files.isExecutable(Path.of(candidate)) && elf(Path.of(candidate))) return Path.of(candidate).toRealPath();
        } catch (IOException | RuntimeException ignored) { }
        return null;
    }
    private static boolean elf(Path path) throws IOException {
        try (var input = Files.newInputStream(path)) { return Arrays.equals(input.readNBytes(4), new byte[]{127, 'E', 'L', 'F'}); }
    }
    private void add(Map<String, Entry> entries, String name, String source, ApplicationTarget target, String origin) {
        if (name == null || name.isBlank() || name.length() > 256 || name.chars().anyMatch(Character::isISOControl)) return;
        boolean self = name.trim().equalsIgnoreCase("FocusDesktop");
        if (self) target = null;
        if (!Set.of("Desktop app", "Flatpak", "Snap", "Windows app", "Microsoft Store", "macOS app").contains(source)) source = "Desktop app";
        String key = target == null ? source + ":" + origin : platform + ":" + target.matchKind() + ":" + target.canonicalValue();
        String id = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
        entries.putIfAbsent(id, new Entry(id, name.trim(), source, target == null ? null : BlockingRuleDto.Target.from(target), target != null,
                self ? "FocusDesktop cannot block itself." : target == null ? "Choose this app manually by its executable or process name." : null));
    }
    private static String base(String path) { return path.substring(Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\')) + 1); }
    private static boolean shared(String path) { String name = base(path).toLowerCase(Locale.ROOT).replaceFirst("\\.exe$", ""); return SHARED.contains(name) || name.matches("python[0-9.]*"); }
}
