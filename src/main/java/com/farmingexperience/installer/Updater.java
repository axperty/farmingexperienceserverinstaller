package com.farmingexperience.installer;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class Updater {
    private Updater() {}

    public static void backupWorld(String dstPath, Installer.StatusFunc updateStatus, Utils.LogFunc logFunc) {
        updateStatus.update("Backing up World...", 0.0);
        String worldName = "world";
        Path propsPath = Path.of(dstPath, "server.properties");

        if (Files.exists(propsPath)) {
            try {
                // this just in case the world name isn't "world"
                for (String line : Files.readAllLines(propsPath, StandardCharsets.UTF_8)) {
                    if (line.strip().startsWith("level-name=")) {
                        worldName = line.strip().split("=", 2)[1];
                        break;
                    }
                }
            } catch (Exception e) {
                logFunc.log("Failed to read server.properties: " + e.getMessage(), true);
            }
        }

        Path worldDir = Path.of(dstPath, worldName);
        if (!Files.exists(worldDir)) {
            logFunc.log("World folder '" + worldName + "' not found. Skipping backup.");
            return;
        }

        Path backupsDir = Path.of(dstPath, "backups");
        try {
            Files.createDirectories(backupsDir);
        } catch (IOException e) {
            logFunc.log("Failed to create backups folder: " + e.getMessage(), true);
            return;
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String backupFilename = worldName + "_" + timestamp;
        Path backupZip = backupsDir.resolve(backupFilename + ".zip");

        try {
            logFunc.log("Backing up world '" + worldName + "' to backups/" + backupFilename + ".zip...");
            zipDirectory(worldDir, backupZip);
            logFunc.log("World backup complete.");
        } catch (Exception e) {
            logFunc.log("World backup failed: " + e.getMessage(), true);
        }
    }

    private static void zipDirectory(Path sourceDir, Path zipPath) throws IOException {
        try (OutputStream fos = Files.newOutputStream(zipPath);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            addFolderToZip(sourceDir, sourceDir, zos);
        }
    }

    private static void addFolderToZip(Path rootDir, Path currentDir, ZipOutputStream zos) throws IOException {
        File[] children = currentDir.toFile().listFiles();
        if (children == null) {
            return;
        }

        for (File child : children) {
            Path childPath = child.toPath();
            if (child.isDirectory()) {
                addFolderToZip(rootDir, childPath, zos);
            } else {
                String entryName = rootDir.relativize(childPath).toString().replace('\\', '/');
                zos.putNextEntry(new ZipEntry(entryName));
                Files.copy(childPath, zos);
                zos.closeEntry();
            }
        }
    }

    public static void runUpdate(String srcPath, String dstPath, Installer.StatusFunc updateStatus,
                                  Utils.LogFunc logFunc, Runnable onSuccess, Installer.FailureCallback onFailure) {
        try {
            backupWorld(dstPath, updateStatus, logFunc);
            Installer.copyInstanceFiles(srcPath, dstPath, updateStatus, logFunc);
            updateStatus.update("Complete!", 100.0);
            onSuccess.run();
        } catch (Exception e) {
            logFunc.log("CRASH: " + Utils.stackTraceToString(e), true);
            onFailure.onFailure(e.getMessage());
        }
    }

    public static class UpdateApp {
        private final JFrame frame;
        private final List<String> logBuffer = new ArrayList<>();
        private final String srcPath;
        private final String dstPath;

        private JPanel contentArea;
        private JLabel lblReady;
        private JButton btnUpdate;
        private JPanel progressContainer;
        private JLabel lblStatus;
        private JProgressBar progressBar;

        public UpdateApp(JFrame frame, String srcPath, String dstPath) {
            this.frame = frame;
            this.srcPath = srcPath;
            this.dstPath = dstPath;

            frame.setTitle("Update Server - " + Config.APP_NAME + " Server Installer");
            Image icon = Utils.loadAppIcon();
            if (icon != null) {
                frame.setIconImage(icon);
            }

            int w = 460, h = 340;
            Utils.centerWindow(frame, w, h);
            frame.setResizable(false);
            frame.setLayout(new BorderLayout());

            log("Updater started.");

            JPanel headerFrame = new JPanel();
            headerFrame.setBackground(Config.HEADER_BG);
            headerFrame.setLayout(new BoxLayout(headerFrame, BoxLayout.Y_AXIS));
            headerFrame.setBorder(BorderFactory.createEmptyBorder(20, 0, 20, 0));

            JLabel lblTitle = UI.label(Config.APP_NAME, Config.FONT_TITLE, Color.WHITE);
            lblTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

            JLabel lblSubtitle = UI.label("Update Server", Config.FONT_SUBTITLE, Config.HEADER_SUBTITLE_FG);
            lblSubtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
            lblSubtitle.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

            headerFrame.add(lblTitle);
            headerFrame.add(lblSubtitle);
            frame.add(headerFrame, BorderLayout.NORTH);

            contentArea = new JPanel();
            contentArea.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            contentArea.setLayout(new BoxLayout(contentArea, BoxLayout.Y_AXIS));
            frame.add(contentArea, BorderLayout.CENTER);

            if (srcPath == null || srcPath.isEmpty() || dstPath == null || dstPath.isEmpty()) {
                JLabel lblError = UI.label("Invalid parameters passed to updater.", Config.FONT_BODY, Config.ERROR_FG);
                contentArea.add(lblError);
                frame.setVisible(true);
                return;
            }

            lblReady = UI.label("Ready to update your server mods and configs?", Config.FONT_BODY, Config.TEXT_DARK);
            lblReady.setAlignmentX(Component.CENTER_ALIGNMENT);
            lblReady.setBorder(BorderFactory.createEmptyBorder(0, 0, 24, 0));
            contentArea.add(lblReady);

            btnUpdate = UI.primaryButton("UPDATE NOW");
            btnUpdate.setAlignmentX(Component.CENTER_ALIGNMENT);
            btnUpdate.addActionListener(e -> startUpdate());
            contentArea.add(btnUpdate);

            frame.setVisible(true);
        }

        private void log(String message) {
            Utils.logMessage(logBuffer, message, false);
        }

        private void updateStatus(String text, Double percent, String subText) {
            SwingUtilities.invokeLater(() -> {
                lblStatus.setText(text);
                if (percent != null) {
                    progressBar.setValue(percent.intValue());
                }
            });
        }

        private void startUpdate() {
            contentArea.remove(btnUpdate);
            contentArea.remove(lblReady);

            progressContainer = new JPanel();
            progressContainer.setLayout(new BoxLayout(progressContainer, BoxLayout.Y_AXIS));
            progressContainer.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));

            lblStatus = UI.label("Preparing...", Config.FONT_BODY, Config.TEXT_DARK);
            lblStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
            lblStatus.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));

            progressBar = new JProgressBar(0, 100);
            progressBar.setAlignmentX(Component.LEFT_ALIGNMENT);
            progressBar.setPreferredSize(new Dimension(Integer.MAX_VALUE, 20));
            progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

            progressContainer.add(lblStatus);
            progressContainer.add(progressBar);
            contentArea.add(progressContainer);
            contentArea.revalidate();
            contentArea.repaint();

            Thread thread = new Thread(() -> runUpdate(
                    srcPath, dstPath,
                    this::updateStatus,
                    (msg, err) -> Utils.logMessage(logBuffer, msg, err),
                    this::onUpdateSuccess,
                    this::onUpdateFailure
            ));
            thread.setDaemon(true);
            thread.start();
        }

        private void onUpdateSuccess() {
            SwingUtilities.invokeLater(this::showSuccessUi);
            Utils.flushLogs(Path.of(dstPath, "serverinstaller").toString(), logBuffer, "update");
        }

        private void onUpdateFailure(String errorMessage) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(frame,
                        "Update Failed.\nCheck the logs folder.\n\n" + errorMessage,
                        "Error", JOptionPane.ERROR_MESSAGE);
                frame.dispose();
            });
            Utils.flushLogs(Path.of(dstPath, "serverinstaller").toString(), logBuffer, "update");
        }

        private void showSuccessUi() {
            contentArea.remove(progressContainer);

            JLabel lblDone = UI.label("Update Complete!", Config.FONT_HEADING, Config.HEADER_BG);
            lblDone.setAlignmentX(Component.CENTER_ALIGNMENT);
            lblDone.setBorder(BorderFactory.createEmptyBorder(12, 0, 24, 0));
            contentArea.add(lblDone);

            JButton btnClose = UI.primaryButton("CLOSE");
            btnClose.setAlignmentX(Component.CENTER_ALIGNMENT);
            btnClose.addActionListener(e -> frame.dispose());
            contentArea.add(btnClose);

            contentArea.revalidate();
            contentArea.repaint();
        }
    }
}
