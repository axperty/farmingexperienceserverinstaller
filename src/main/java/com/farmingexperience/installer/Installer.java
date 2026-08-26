package com.farmingexperience.installer;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;

public final class Installer {
    private Installer() {}

    public interface StatusFunc {
        void update(String text, Double percent, String subText);

        default void update(String text, Double percent) {
            update(text, percent, "");
        }
    }

    public interface SuccessCallback {
        void onSuccess(String publicIp, String localIp);
    }

    public interface FailureCallback {
        void onFailure(String errorMessage);
    }

    public static void copyInstanceFiles(String srcPath, String dstPath, StatusFunc updateStatus, Utils.LogFunc logFunc) throws IOException {
        updateStatus.update("Copying Mods...", 0.0);
        Path srcMods = Path.of(srcPath, "mods");
        Path dstMods = Path.of(dstPath, "mods");

        if (Files.exists(dstMods)) {
            deleteRecursively(dstMods);
        }
        Files.createDirectories(dstMods);

        File[] modFiles = srcMods.toFile().listFiles();
        if (modFiles == null) {
            modFiles = new File[0];
        }
        int totalFiles = modFiles.length;

        logFunc.log("--- STARTING MOD COPY (" + totalFiles + " items) ---");

        for (int i = 0; i < totalFiles; i++) {
            File item = modFiles[i];
            String name = item.getName();
            Path fullDst = dstMods.resolve(name);

            if (item.isDirectory()) {
                if (isExcludedFolder(name)) {
                    logFunc.log("[SKIP] Directory excluded: " + name);
                    continue;
                }
                logFunc.log("[COPY] Directory: " + name);
                copyRecursively(item.toPath(), fullDst);
            } else {
                if (isExcludedMod(name)) {
                    logFunc.log("[SKIP] File excluded: " + name);
                    continue;
                }
                logFunc.log("[COPY] File: " + name);
                Files.copy(item.toPath(), fullDst, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
            }

            // mod copying only gets the first 40% of the overall progress bar, the rest is
            // configs/icon/neoforge install/shortcuts etc...
            double pct = ((double) i / totalFiles) * 40;
            if (i % 5 == 0) {
                updateStatus.update("Copying mods (" + (int) (((double) i / totalFiles) * 100) + "%)...", pct);
            }
        }

        logFunc.log("--- MOD COPY FINISHED ---");

        updateStatus.update("Copying Configs...", 40.0);
        Path srcCfg = Path.of(srcPath, "config");
        Path dstCfg = Path.of(dstPath, "config");

        if (Files.exists(srcCfg)) {
            if (Files.exists(dstCfg)) {
                deleteRecursively(dstCfg);
            }
            copyRecursively(srcCfg, dstCfg);
            logFunc.log("Configs copied.");
        }
    }

    private static boolean isExcludedFolder(String name) {
        String lower = name.toLowerCase();
        for (String folder : Config.EXCLUDED_FOLDERS) {
            if (lower.contains(folder.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isExcludedMod(String name) {
        String lower = name.toLowerCase();
        for (String pattern : Config.EXCLUDED_MODS) {
            String cleanPattern = pattern.replace("*", "").toLowerCase();
            if (lower.contains(cleanPattern)) {
                return true;
            }
        }
        return false;
    }

    private static void copyRecursively(Path src, Path dst) throws IOException {
        File srcFile = src.toFile();

        if (srcFile.isDirectory()) {
            Files.createDirectories(dst);
            File[] children = srcFile.listFiles();
            if (children != null) {
                for (File child : children) {
                    copyRecursively(child.toPath(), dst.resolve(child.getName()));
                }
            }
        } else {
            Files.createDirectories(dst.getParent());
            Files.copy(src, dst, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void deleteRecursively(Path path) throws IOException {
        File file = path.toFile();
        if (!file.exists()) {
            return;
        }

        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child.toPath());
                }
            }
        }

        Files.deleteIfExists(path);
    }

    // Copies this running jar into the install folder as updater.jar and makes a shortcut that runs it
    public static void setupUpdaterShortcuts(String srcPath, String dstPath, String iconPath, Utils.LogFunc logFunc) throws IOException {
        Path installerDir = Path.of(dstPath, "serverinstaller");
        Files.createDirectories(installerDir);

        String jarPath = getRunningJarPath();

        if (jarPath != null) {
            Path destJar = installerDir.resolve("updater.jar");
            Files.copy(Path.of(jarPath), destJar, StandardCopyOption.REPLACE_EXISTING);
            logFunc.log("Copied updater jar to " + destJar);

            createUpdaterShortcut(srcPath, dstPath, destJar, iconPath, logFunc);
        } else {
            logFunc.log("Run this program as a packaged jar to update server.");
        }

        createWikiShortcut(dstPath, iconPath, logFunc);
    }

    // Name of the server starter file
    private static String runScriptName() {
        return Utils.currentOs() == Utils.OsType.WINDOWS ? "run.bat" : "run.sh";
    }

    // Hide run.bat on windows, might partially work on macos and linux
    private static void prepareRunScript(String dstPath, Utils.LogFunc logFunc) {
        Path runScript = Path.of(dstPath, runScriptName());
        if (!Files.exists(runScript)) return;

        if (Utils.currentOs() == Utils.OsType.WINDOWS) {
            try {
                List<String> lines = Files.readAllLines(runScript, StandardCharsets.UTF_8);
                List<String> newLines = lines.stream()
                        .filter(line -> !line.strip().equalsIgnoreCase("pause"))
                        .map(line -> line.strip().startsWith("java ")
                                ? line.replace("java ", "start \"\" javaw ")
                                : line)
                        .toList();
                Files.writeString(runScript, String.join("\n", newLines) + "\n", StandardCharsets.UTF_8);
                logFunc.log("run.bat patched for javaw.");
            } catch (Exception e) {
                logFunc.log("Failed to patch run.bat: " + e.getMessage(), true);
            }
        } else {
            if (runScript.toFile().setExecutable(true)) {
                logFunc.log("run.sh marked executable.");
            } else {
                logFunc.log("Failed to mark run.sh executable.", true);
            }
        }
    }

    private static void createStartServerShortcut(String desktopDir, String dstPath, String iconIcoPath, Utils.LogFunc logFunc) {
        String runScript = Path.of(dstPath, runScriptName()).toString();
        String name = "Start " + Config.APP_NAME + " Server";

        if (Utils.currentOs() == Utils.OsType.WINDOWS) {
            Path shortcutPath = Path.of(desktopDir, name + ".lnk");
            createWindowsShortcut(shortcutPath.toString(), runScript, iconIcoPath, logFunc, "", dstPath);
        } else {
            createUnixLaunchShortcut(desktopDir, name, "\"" + runScript + "\"", dstPath, logFunc);
        }
    }

    private static void createUpdaterShortcut(String srcPath, String dstPath, Path updaterJar, String iconIcoPath, Utils.LogFunc logFunc) {
        if (Utils.currentOs() == Utils.OsType.WINDOWS) {
            Path shortcutPath = Path.of(dstPath, "Update Server.lnk");
            String javaExe = Path.of(System.getProperty("java.home"), "bin", "javaw.exe").toString();
            String args = "-jar \"" + updaterJar + "\" --update \"" + srcPath + "\" \"" + dstPath + "\"";
            createWindowsShortcut(shortcutPath.toString(), javaExe, iconIcoPath, logFunc, args, dstPath);
        } else {
            String javaExe = Path.of(System.getProperty("java.home"), "bin", "java").toString();
            String command = "\"" + javaExe + "\" -jar \"" + updaterJar + "\" --update \"" + srcPath + "\" \"" + dstPath + "\"";
            createUnixLaunchShortcut(dstPath, "Update Server", command, dstPath, logFunc);
        }
    }

    private static void createWikiShortcut(String dstPath, String iconIcoPath, Utils.LogFunc logFunc) {
        if (Config.WIKI_URL.isBlank()) return;

        Utils.OsType os = Utils.currentOs();
        if (os == Utils.OsType.WINDOWS) {
            createUrlShortcut(Path.of(dstPath, "Wiki.url").toString(), Config.WIKI_URL, iconIcoPath, logFunc);
        } else if (os == Utils.OsType.MAC) {
            createWeblocShortcut(Path.of(dstPath, "Wiki.webloc").toString(), Config.WIKI_URL, logFunc);
        } else {
            createDesktopUrlShortcut(Path.of(dstPath, "Wiki.desktop").toString(), Config.WIKI_URL, logFunc);
        }
    }

    // figures out the path of the jar this code is currently running from
    private static String getRunningJarPath() {
        try {
            URI uri = Installer.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path path = Path.of(uri);
            if (path.toString().endsWith(".jar")) {
                return path.toString();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public static void runInstallation(String srcPath, String dstPath, boolean createShortcutFlag,
                                        StatusFunc updateStatus, Utils.LogFunc logFunc,
                                        SuccessCallback onSuccess, FailureCallback onFailure) {
        try {
            Files.createDirectories(Path.of(dstPath));

            copyInstanceFiles(srcPath, dstPath, updateStatus, logFunc);

            updateStatus.update("Downloading Server Icon...", 50.0);
            Path pngPath = Path.of(dstPath, "server-icon.png");
            Path icoPath = Path.of(dstPath, "server-icon.ico");
            try {
                downloadFile(Config.ICON_URL, pngPath.toString(), logFunc);
                Utils.copyResourceTo("app.ico", icoPath);
            } catch (Exception e) {
                logFunc.log("Icon processing failed: " + e.getMessage(), true);
            }

            updateStatus.update("Configuring Server...", 55.0);
            Path propsFile = Path.of(dstPath, "server.properties");
            StringBuilder props = new StringBuilder("#Minecraft Server Properties\n");
            for (var entry : Config.SERVER_PROPS.entrySet()) {
                props.append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
            }
            Files.writeString(propsFile, props.toString(), StandardCharsets.UTF_8);
            logFunc.log("server.properties generated.");

            updateStatus.update("Downloading NeoForge...", 60.0);
            Path installerJar = Path.of(dstPath, "installer.jar");
            downloadFile(Config.NEOFORGE_INSTALLER_URL, installerJar.toString(), logFunc);

            updateStatus.update("Installing NeoForge Server Loader...", 75.0, "This might take a few seconds");
            logFunc.log("Running NeoForge installer...");

            // --installServer runs neoforge's installer headless, no gui popup
            ProcessBuilder pb = new ProcessBuilder("java", "-jar", "installer.jar", "--installServer");
            pb.directory(Path.of(dstPath).toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            if (!output.isEmpty()) {
                for (String line : output.split("\\R")) {
                    logFunc.log("[NeoForge] " + line);
                }
            }
            if (exitCode != 0) {
                throw new IOException("NeoForge installer exited with code " + exitCode);
            }

            Files.deleteIfExists(installerJar);
            Files.deleteIfExists(Path.of(dstPath, "installer.jar.log"));

            prepareRunScript(dstPath, logFunc);

            updateStatus.update("Setting up Updater...", 85.0);
            setupUpdaterShortcuts(srcPath, dstPath, icoPath.toString(), logFunc);

            Files.writeString(Path.of(dstPath, "eula.txt"), "eula=true\n", StandardCharsets.UTF_8);

            if (createShortcutFlag) {
                updateStatus.update("Creating Shortcut...", 90.0, "");
                Path desktop = Path.of(System.getProperty("user.home"), "Desktop");
                createStartServerShortcut(desktop.toString(), dstPath, icoPath.toString(), logFunc);
            }

            updateStatus.update("Fetching IP...", 95.0);
            String publicIp = Utils.getPublicIp(logFunc);
            String localIp = Utils.getLocalIp(logFunc);

            updateStatus.update("Complete!", 100.0);
            onSuccess.onSuccess(publicIp, localIp);

        } catch (Exception e) {
            logFunc.log("CRASH: " + Utils.stackTraceToString(e), true);
            onFailure.onFailure(e.getMessage());
        }
    }

    // Thing that basically downloads internet stuff using a user agent
    public static void downloadFile(String url, String filepath, Utils.LogFunc logFunc) throws IOException, InterruptedException {
        logFunc.log("Downloading " + url);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", Config.USER_AGENT)
                    .GET()
                    .build();
            HttpResponse<Path> response = Utils.httpClient().send(request,
                    HttpResponse.BodyHandlers.ofFile(Path.of(filepath)));
            if (response.statusCode() >= 400) {
                throw new IOException("HTTP " + response.statusCode() + " downloading " + url);
            }
        } catch (Exception e) {
            logFunc.log("Download error: " + e.getMessage(), true);
            throw e;
        }
    }

    // windows url shorcut, not sure if the icon actually works, needs testing
    private static void createWindowsShortcut(String shortcutPath, String targetPath, String iconPath,
                                               Utils.LogFunc logFunc, String args, String workingDir) {
        try {
            if (workingDir == null || workingDir.isEmpty()) {
                workingDir = Path.of(targetPath).getParent().toString();
            }

            String vbsScript = """
                    Set oWS = WScript.CreateObject("WScript.Shell")
                    Set oLink = oWS.CreateShortcut("%s")
                    oLink.TargetPath = "%s"
                    oLink.Arguments = "%s"
                    oLink.WorkingDirectory = "%s"
                    oLink.IconLocation = "%s"
                    oLink.Save
                    """.formatted(vbsEscape(shortcutPath), vbsEscape(targetPath), vbsEscape(args), vbsEscape(workingDir), vbsEscape(iconPath));

            Path vbsFile = Path.of(shortcutPath).getParent().resolve("create_shortcut.vbs");
            Files.writeString(vbsFile, vbsScript, StandardCharsets.UTF_8);

            ProcessBuilder pb = new ProcessBuilder("cscript", "//Nologo", vbsFile.toString());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            if (!output.isEmpty()) {
                for (String line : output.split("\\R")) {
                    logFunc.log("[VBScript] " + line);
                }
            }
            if (exitCode != 0) {
                throw new IOException("cscript exited with code " + exitCode);
            }

            Files.deleteIfExists(vbsFile);
        } catch (Exception e) {
            logFunc.log("Shortcut failed: " + e.getMessage(), true);
        }
    }

    // Double quote fix
    private static String vbsEscape(String text) {
        return text.replace("\"", "\"\"");
    }

    private static void createUrlShortcut(String shortcutPath, String url, String iconPath, Utils.LogFunc logFunc) {
        try {
            String content = "[InternetShortcut]\nURL=" + url + "\nIconFile=" + iconPath + "\nIconIndex=0\n";
            Files.writeString(Path.of(shortcutPath), content, StandardCharsets.UTF_8);
            logFunc.log("URL Shortcut created: " + shortcutPath);
        } catch (Exception e) {
            logFunc.log("URL Shortcut failed: " + e.getMessage(), true);
        }
    }

    // macos url shorcut, needs testing
    private static void createWeblocShortcut(String shortcutPath, String url, Utils.LogFunc logFunc) {
        try {
            String content = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                    <plist version="1.0">
                    <dict>
                        <key>URL</key>
                        <string>%s</string>
                    </dict>
                    </plist>
                    """.formatted(url);
            Files.writeString(Path.of(shortcutPath), content, StandardCharsets.UTF_8);
            logFunc.log("Webloc shortcut created: " + shortcutPath);
        } catch (Exception e) {
            logFunc.log("Webloc shortcut failed: " + e.getMessage(), true);
        }
    }

    // linux url shorcut, needs testing
    private static void createDesktopUrlShortcut(String shortcutPath, String url, Utils.LogFunc logFunc) {
        try {
            String content = """
                    [Desktop Entry]
                    Type=Link
                    Name=%s Wiki
                    URL=%s
                    Icon=text-html
                    """.formatted(Config.APP_NAME, url);
            Path file = Path.of(shortcutPath);
            Files.writeString(file, content, StandardCharsets.UTF_8);
            file.toFile().setExecutable(true);
            logFunc.log("Desktop shortcut created: " + shortcutPath);
        } catch (Exception e) {
            logFunc.log("Desktop shortcut failed: " + e.getMessage(), true);
        }
    }

    // linux and macos support, needs to be tested yet
    private static void createUnixLaunchShortcut(String targetDir, String name, String command, String workingDir, Utils.LogFunc logFunc) {
        try {
            Files.createDirectories(Path.of(targetDir));
            boolean mac = Utils.currentOs() == Utils.OsType.MAC;
            Path file = Path.of(targetDir, name + (mac ? ".command" : ".desktop"));

            String content = mac
                    ? "#!/bin/bash\ncd \"%s\"\n%s\n".formatted(workingDir, command)
                    : """
                      [Desktop Entry]
                      Type=Application
                      Name=%s
                      Exec=bash -c 'cd "%s" && %s'
                      Path=%s
                      Terminal=true
                      """.formatted(name, workingDir, command, workingDir);

            Files.writeString(file, content, StandardCharsets.UTF_8);
            file.toFile().setExecutable(true);
            logFunc.log("Shortcut created: " + file);
        } catch (Exception e) {
            logFunc.log("Shortcut failed: " + e.getMessage(), true);
        }
    }

}
