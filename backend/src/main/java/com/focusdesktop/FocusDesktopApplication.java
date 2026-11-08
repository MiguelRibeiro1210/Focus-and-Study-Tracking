package com.focusdesktop;

import com.focusdesktop.infrastructure.config.LocalDataDirectory;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FocusDesktopApplication {

    public static void main(String[] args) {
        var dataDirectory = LocalDataDirectory.resolve();
        try {
            Files.createDirectories(dataDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not create the FocusDesktop data directory: " + dataDirectory,
                    exception);
        }

        var application = new SpringApplication(FocusDesktopApplication.class);
        application.setDefaultProperties(Map.of(
                "focusdesktop.data-dir", dataDirectory.toString().replace('\\', '/')));
        application.run(args);
    }
}
