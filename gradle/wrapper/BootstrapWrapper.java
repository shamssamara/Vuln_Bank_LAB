package org.gradle.wrapper;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Self-contained lab distribution bootstrap. Source included in this project.
 * This intentionally is not the official Gradle wrapper implementation.
 * Once downloaded, run: ./gradlew wrapper --gradle-version 8.14
 * to replace with the official wrapper.
 */
public final class BootstrapWrapper {
    private BootstrapWrapper() { }
    public static void main(String[] args) throws Exception {
        Path root = Paths.get("").toAbsolutePath().normalize();
        Path propsPath = root.resolve("gradle/wrapper/gradle-wrapper.properties");
        if (!Files.isRegularFile(propsPath)) {
            throw new IOException("Run gradlew from VulnBankLab project root (missing " + propsPath + ")");
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(propsPath)) { props.load(in); }
        String url = props.getProperty("distributionUrl");
        if (url == null || !url.startsWith("https://services.gradle.org/distributions/")) {
            throw new IOException("Unexpected Gradle distribution URL in wrapper properties: " + url);
        }
        String version = "8.14";
        Path cache = Paths.get(System.getProperty("user.home"), ".gradle", "vulnbank-lab-distribution");
        Path installed = cache.resolve("gradle-" + version);
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        Path executable = installed.resolve("bin").resolve(windows ? "gradle.bat" : "gradle");
        if (!Files.isRegularFile(executable)) {
            Files.createDirectories(cache);
            Path zip = cache.resolve("gradle-" + version + "-bin.zip");
            System.out.println("[VulnBank] Downloading pinned Gradle distribution " + url);
            if (!Files.exists(zip)) {
                Path tmp = cache.resolve("gradle-download.tmp");
                try (InputStream in = new java.net.URL(url).openStream()) {
                    Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                }
                Files.move(tmp, zip, StandardCopyOption.REPLACE_EXISTING);
            }
            System.out.println("[VulnBank] Extracting pinned Gradle " + version);
            try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zip)))) {
                ZipEntry entry;
                byte[] buffer = new byte[32768];
                while ((entry = zis.getNextEntry()) != null) {
                    Path output = cache.resolve(entry.getName()).normalize();
                    if (!output.startsWith(cache)) throw new IOException("Zip path escape: " + entry.getName());
                    if (entry.isDirectory()) Files.createDirectories(output);
                    else {
                        Files.createDirectories(output.getParent());
                        try (java.io.OutputStream out = Files.newOutputStream(output)) {
                            int n;
                            while ((n = zis.read(buffer)) > 0) out.write(buffer, 0, n);
                        }
                    }
                    zis.closeEntry();
                }
            }
            if (!windows) executable.toFile().setExecutable(true, false);
        }
        List<String> command = new ArrayList<>();
        if (windows) {
            command.add("cmd.exe"); command.add("/c");
        }
        command.add(executable.toString());
        command.addAll(Arrays.asList(args));
        Process process = new ProcessBuilder(command).directory(root.toFile()).inheritIO().start();
        int status = process.waitFor();
        System.exit(status);
    }
}
