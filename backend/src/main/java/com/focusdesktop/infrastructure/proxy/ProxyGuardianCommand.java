package com.focusdesktop.infrastructure.proxy;

import java.io.File;
import java.util.*;

final class ProxyGuardianCommand {
    static List<String> javaCommand(Class<?> main, List<String> arguments) {
        var command = new ArrayList<String>();
        command.add(ProcessHandle.current().info().command().orElseThrow());
        String jar = System.getProperty("focusdesktop.backend-jar"), classpath = System.getProperty("java.class.path");
        if (jar == null && classpath.endsWith(".jar") && !classpath.contains(File.pathSeparator)) jar = classpath;
        if (jar != null) {
            command.add("-Dloader.main=" + main.getName()); command.add("-cp"); command.add(jar);
            command.add("org.springframework.boot.loader.launch.PropertiesLauncher");
        } else { command.add("-cp"); command.add(classpath); command.add(main.getName()); }
        command.addAll(arguments); return List.copyOf(command);
    }
    static List<String> elevated(String os, List<String> jvmCommand) {
        if (os.toLowerCase(Locale.ROOT).contains("linux")) {
            var command = new ArrayList<>(List.of("/usr/bin/pkexec")); command.addAll(jvmCommand); return command;
        }
        if (!os.toLowerCase(Locale.ROOT).contains("mac")) throw new IllegalArgumentException("Unsupported permission prompt");
        String shell = jvmCommand.stream().map(value -> "'" + value.replace("'", "'\"'\"'") + "'").collect(java.util.stream.Collectors.joining(" "));
        String apple = "with timeout of 2147483 seconds\ndo shell script \"" + shell.replace("\\", "\\\\").replace("\"", "\\\"")
                + "\" with administrator privileges\nend timeout";
        return List.of("/usr/bin/osascript", "-e", apple);
    }
}
