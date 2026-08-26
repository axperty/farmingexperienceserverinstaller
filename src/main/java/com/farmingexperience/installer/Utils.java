package com.farmingexperience.installer;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Utils {
    private Utils() {}

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public enum OsType { WINDOWS, MAC, LINUX }

    public interface LogFunc {
        void log(String message, boolean error);

        default void log(String message) {
            log(message, false);
        }
    }

    public static OsType currentOs() {
        String name = System.getProperty("os.name", "").toLowerCase();
        if (name.contains("win")) return OsType.WINDOWS;
        if (name.contains("mac") || name.contains("darwin")) return OsType.MAC;
        return OsType.LINUX;
    }

    public static void copyResourceTo(String resourceName, Path dest) throws IOException {
        try (InputStream in = Utils.class.getResourceAsStream("/" + resourceName)) {
            if (in == null) {
                throw new IOException("Resource not found: " + resourceName);
            }
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // Downloads icon from this GitHub repo
    public static Image loadAppIcon() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(Config.APP_ICON_URL))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
            return ImageIO.read(new ByteArrayInputStream(response.body()));
        } catch (Exception e) {
            return null;
        }
    }

    public static void centerWindow(Window window, int w, int h) {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int x = (screen.width - w) / 2;
        int y = (screen.height - h) / 2;
        window.setBounds(x, y, w, h);
    }

    public static void logMessage(List<String> buffer, String message, boolean error) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String tag = error ? "[ERROR]" : "[INFO]";
        buffer.add(timestamp + " " + tag + " " + message);
    }

    public static void flushLogs(String dstFolder, List<String> buffer) {
        flushLogs(dstFolder, buffer, "installer");
    }

    public static void flushLogs(String dstFolder, List<String> buffer, String prefix) {
        try {
            Path logsFolder = Path.of(dstFolder, "logs");
            Files.createDirectories(logsFolder);

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            Path logPath = logsFolder.resolve(prefix + "_" + timestamp + ".log");

            Files.writeString(logPath, String.join("\n", buffer), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    public static class InstancePaths {
        public final String curseForge;
        public final String modrinth;

        public InstancePaths(String curseForge, String modrinth) {
            this.curseForge = curseForge;
            this.modrinth = modrinth;
        }
    }

    public static InstancePaths detectInstances() {
        String userHome = System.getProperty("user.home");
        List<String> cfCandidates = new ArrayList<>();
        List<String> mrCandidates = new ArrayList<>();
        OsType os = currentOs();

        if (os == OsType.WINDOWS) {
            String appdata = System.getenv("APPDATA");
            if (appdata == null) {
                appdata = "";
            }
            Path appdataDir = Path.of(appdata);
            cfCandidates.add(appdataDir.resolve(Path.of("CurseForge", "Minecraft", "Instances", Config.INSTANCE_NAME)).toString());
            cfCandidates.add(Path.of(userHome, "curseforge", "minecraft", "Instances", Config.INSTANCE_NAME).toString());
            mrCandidates.add(appdataDir.resolve(Path.of("ModrinthApp", "profiles", Config.INSTANCE_NAME)).toString());
        } else if (os == OsType.MAC) {
            Path appSupport = Path.of(userHome, "Library", "Application Support");
            cfCandidates.add(appSupport.resolve(Path.of("CurseForge", "Minecraft", "Instances", Config.INSTANCE_NAME)).toString());
            mrCandidates.add(appSupport.resolve(Path.of("ModrinthApp", "profiles", Config.INSTANCE_NAME)).toString());
        } else {
            String xdgData = System.getenv("XDG_DATA_HOME");
            Path dataHome;
            if (xdgData != null) {
                dataHome = Path.of(xdgData);
            } else {
                dataHome = Path.of(userHome, ".local", "share");
            }
            cfCandidates.add(dataHome.resolve(Path.of("CurseForge", "Minecraft", "Instances", Config.INSTANCE_NAME)).toString());
            mrCandidates.add(dataHome.resolve(Path.of("ModrinthApp", "profiles", Config.INSTANCE_NAME)).toString());
        }

        String cf = firstValidInstance(cfCandidates);
        String mr = firstValidInstance(mrCandidates);
        return new InstancePaths(cf, mr);
    }

    private static String firstValidInstance(List<String> candidates) {
        for (String path : candidates) {
            if (Files.isDirectory(Path.of(path, "mods"))) {
                return path;
            }
        }
        return null;
    }

    public static boolean checkJava(LogFunc logFunc) {
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            process.waitFor();



            Matcher matcher = Pattern.compile("version \"(\\d+)").matcher(output);
            if (matcher.find()) {
                int majorVersion = Integer.parseInt(matcher.group(1));
                if (majorVersion >= 21) {
                    return true;
                } else {
                    logFunc.log("Java version mismatch. Found: " + majorVersion + ", Required: 21+");
                }
            } else {
                logFunc.log("Could not parse Java version from output.");
            }
        } catch (IOException e) {
            logFunc.log("Java executable not found in PATH.");
        } catch (Exception e) {
            logFunc.log("Java check error: " + e.getMessage());
        }

        int ans = JOptionPane.showConfirmDialog(
                null,
                "Java 21 is required to run this server but was not found (or is outdated).\n\nWould you like to download it now?",
                "Java 21 Required",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (ans == JOptionPane.YES_OPTION) {
            openUrl(Config.JAVA_DOWNLOAD_URL);
        }
        return false;
    }

    // compresses exception's stack trace into a string so it can go into the log file
    public static String stackTraceToString(Exception e) {
        java.io.StringWriter sw = new java.io.StringWriter();
        e.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }

    public static void openUrl(String url) {
        try {
            Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception ignored) {
        }
    }

    public static String getPublicIp(LogFunc logFunc) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.ipify.org"))
                    .header("User-Agent", Config.USER_AGENT)
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            String ip = response.body();
            logFunc.log("Public IP detected: " + ip);
            return ip;
        } catch (Exception e) {
            logFunc.log("Failed to get public IP: " + e.getMessage(), true);
            return "Unknown";
        }
    }

    public static String getLocalIp(LogFunc logFunc) {
        try (DatagramSocket socket = new DatagramSocket()) {
            // doesn't actually send anything to 8.8.8.8
            socket.connect(new InetSocketAddress("8.8.8.8", 80));
            String ip = socket.getLocalAddress().getHostAddress();
            logFunc.log("Local IP detected: " + ip);
            return ip;
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    public static HttpClient httpClient() {
        return HTTP_CLIENT;
    }
}
