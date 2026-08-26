package com.farmingexperience.installer;

import javax.swing.*;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ServerInstallerApp {
    private final JFrame frame;
    private final List<String> logBuffer = new ArrayList<>();
    private final JPanel contentArea;

    private JTextField entrySrc;
    private JTextField entryDst;
    private JLabel lblSrcValidation;
    private JCheckBox autorunCheck;
    private JCheckBox shortcutCheck;

    private JLabel lblStatus;
    private JLabel lblSubStatus;
    private JProgressBar progressBar;

    public ServerInstallerApp(JFrame frame) {
        this.frame = frame;
        frame.setTitle(Config.APP_NAME + " Server Installer");

        Image icon = Utils.loadAppIcon();
        if (icon != null) {
            frame.setIconImage(icon);
        }

        int w = 640, h = 520;
        frame.setLayout(new BorderLayout());
        Utils.centerWindow(frame, w, h);
        frame.setResizable(false);

        log("Installer started.");

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        UIManager.put("ProgressBar.horizontalSize", new Dimension(146, 20));

        JPanel headerFrame = new JPanel();
        headerFrame.setBackground(Config.HEADER_BG);
        headerFrame.setLayout(new BoxLayout(headerFrame, BoxLayout.Y_AXIS));
        headerFrame.setBorder(BorderFactory.createEmptyBorder(20, 0, 20, 0));

        JLabel lblTitle = UI.label(Config.APP_NAME, Config.FONT_TITLE, Color.WHITE);
        lblTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblSubtitle = UI.label("Server Installer", Config.FONT_SUBTITLE, Config.HEADER_SUBTITLE_FG);
        lblSubtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblSubtitle.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        headerFrame.add(lblTitle);
        headerFrame.add(lblSubtitle);
        frame.add(headerFrame, BorderLayout.NORTH);

        contentArea = new JPanel(new CardLayout());
        frame.add(contentArea, BorderLayout.CENTER);

        Utils.InstancePaths paths = Utils.detectInstances();

        if (paths.curseForge != null && paths.modrinth != null) {
            showPlatformSelection(paths.curseForge, paths.modrinth);
        } else if (paths.curseForge != null) {
            showInputScreen(paths.curseForge);
        } else if (paths.modrinth != null) {
            showInputScreen(paths.modrinth);
        } else {
            showInputScreen("");
        }

        frame.setVisible(true);
    }

    private void log(String message) {
        log(message, false);
    }

    private void log(String message, boolean error) {
        Utils.logMessage(logBuffer, message, error);
    }

    private void swapContent(JPanel newPanel) {
        contentArea.removeAll();
        contentArea.add(newPanel);
        contentArea.revalidate();
        contentArea.repaint();
    }

    // selection screen

    private void showPlatformSelection(String cfPath, String mrPath) {
        JPanel frameSelection = new JPanel();
        frameSelection.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        frameSelection.setLayout(new BoxLayout(frameSelection, BoxLayout.Y_AXIS));

        JLabel lblTitle = UI.label("Multiple Instances Found", Config.FONT_HEADING, Config.TEXT_DARK);
        lblTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblTitle.setBorder(BorderFactory.createEmptyBorder(35, 0, 12, 0));

        JLabel lblSubtitleLine1 = UI.label("We found " + Config.APP_NAME + " installed on both", Config.FONT_BODY, Config.TEXT_MEDIUM);
        lblSubtitleLine1.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblSubtitleLine2 = UI.label("Modrinth and CurseForge.", Config.FONT_BODY, Config.TEXT_MEDIUM);
        lblSubtitleLine2.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblSubtitleLine2.setBorder(BorderFactory.createEmptyBorder(0, 0, 30, 0));

        JPanel btnContainer = new JPanel();
        btnContainer.setLayout(new BoxLayout(btnContainer, BoxLayout.Y_AXIS));
        btnContainer.setBorder(BorderFactory.createEmptyBorder(0, 50, 0, 50));

        JButton btnModrinth = UI.button("Use Modrinth Installation", new Color(0x1B, 0xD9, 0x6A), Config.FONT_BUTTON);
        btnModrinth.addActionListener(e -> transitionToInput(mrPath));

        JButton btnCurseForge = UI.button("Use CurseForge Installation", new Color(0xF5, 0x7C, 0x00), Config.FONT_BUTTON);
        btnCurseForge.addActionListener(e -> transitionToInput(cfPath));

        btnModrinth.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnCurseForge.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnModrinth.setMaximumSize(new Dimension(Integer.MAX_VALUE, btnModrinth.getPreferredSize().height));
        btnCurseForge.setMaximumSize(new Dimension(Integer.MAX_VALUE, btnCurseForge.getPreferredSize().height));

        btnContainer.add(btnModrinth);
        btnContainer.add(Box.createVerticalStrut(12));
        btnContainer.add(btnCurseForge);

        frameSelection.add(lblTitle);
        frameSelection.add(lblSubtitleLine1);
        frameSelection.add(lblSubtitleLine2);
        frameSelection.add(btnContainer);

        swapContent(frameSelection);
    }

    private void transitionToInput(String selectedPath) {
        showInputScreen(selectedPath);
    }

    // input screen

    private void showInputScreen(String initialPath) {
        JPanel frameInputs = new JPanel();
        frameInputs.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        frameInputs.setLayout(new BoxLayout(frameInputs, BoxLayout.Y_AXIS));

        JLabel lblSrc = UI.label(Config.APP_NAME + " Instance Location:", Config.FONT_HEADING, Config.TEXT_DARK);
        lblSrc.setAlignmentX(Component.LEFT_ALIGNMENT);
        frameInputs.add(lblSrc);

        JPanel frameSrc = new JPanel(new BorderLayout(8, 0));
        frameSrc.setAlignmentX(Component.LEFT_ALIGNMENT);
        frameSrc.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        frameSrc.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        entrySrc = new JTextField(initialPath);
        entrySrc.setFont(Config.FONT_BODY);
        entrySrc.getDocument().addDocumentListener(new SimpleDocListener(this::validateSrc));

        JButton btnSrc = new JButton("Browse");
        btnSrc.setFont(Config.FONT_BODY);

        btnSrc.addActionListener(e -> browseSrc());

        frameSrc.add(entrySrc, BorderLayout.CENTER);
        frameSrc.add(btnSrc, BorderLayout.EAST);
        frameInputs.add(frameSrc);

        lblSrcValidation = UI.label(" ", Config.FONT_SMALL, Config.TEXT_MUTED);
        lblSrcValidation.setAlignmentX(Component.LEFT_ALIGNMENT);
        lblSrcValidation.setBorder(BorderFactory.createEmptyBorder(4, 0, 14, 0));
        frameInputs.add(lblSrcValidation);

        JLabel lblDst = UI.label("Install Server To:", Config.FONT_HEADING, Config.TEXT_DARK);
        lblDst.setAlignmentX(Component.LEFT_ALIGNMENT);
        frameInputs.add(lblDst);

        JPanel frameDst = new JPanel(new BorderLayout(8, 0));
        frameDst.setAlignmentX(Component.LEFT_ALIGNMENT);
        frameDst.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        frameDst.setBorder(BorderFactory.createEmptyBorder(4, 0, 14, 0));

        String defaultDst = Path.of(System.getProperty("user.dir"), Config.SERVER_FOLDER_NAME).toString();
        entryDst = new JTextField(defaultDst);
        entryDst.setFont(Config.FONT_BODY);

        JButton btnDst = new JButton("Browse");
        btnDst.setFont(Config.FONT_BODY);
        btnDst.addActionListener(e -> browseDst());

        frameDst.add(entryDst, BorderLayout.CENTER);
        frameDst.add(btnDst, BorderLayout.EAST);
        frameInputs.add(frameDst);

        JPanel optsFrame = new JPanel();
        optsFrame.setLayout(new BoxLayout(optsFrame, BoxLayout.Y_AXIS));
        optsFrame.setAlignmentX(Component.LEFT_ALIGNMENT);
        optsFrame.setBorder(BorderFactory.createEmptyBorder(0, 0, 20, 0));

        shortcutCheck = new JCheckBox("Create Desktop Shortcut", true);
        shortcutCheck.setFont(Config.FONT_BODY);
        shortcutCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        autorunCheck = new JCheckBox("Start server automatically when finished", true);
        autorunCheck.setFont(Config.FONT_BODY);
        autorunCheck.setAlignmentX(Component.LEFT_ALIGNMENT);

        optsFrame.add(shortcutCheck);
        optsFrame.add(Box.createVerticalStrut(4));
        optsFrame.add(autorunCheck);
        frameInputs.add(optsFrame);

        frameInputs.add(Box.createVerticalGlue());

        JPanel bottomFrame = new JPanel(new BorderLayout());
        bottomFrame.setAlignmentX(Component.LEFT_ALIGNMENT);
        bottomFrame.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));

        JPanel eulaFrame = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JLabel lblEulaText = UI.label("Clicking Create Server agrees to ", Config.FONT_SMALL, Config.TEXT_MUTED);

        JLabel link = UI.label("Minecraft EULA", Config.FONT_SMALL, Config.LINK_FG);
        link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        link.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Utils.openUrl(Config.EULA_URL);
            }
        });

        eulaFrame.add(lblEulaText);
        eulaFrame.add(link);
        bottomFrame.add(eulaFrame, BorderLayout.WEST);

        JButton btnInstall = UI.button("CREATE SERVER", Config.PRIMARY_BG, Config.FONT_BUTTON);
        btnInstall.addActionListener(e -> startInstallation());
        bottomFrame.add(btnInstall, BorderLayout.EAST);

        frameInputs.add(bottomFrame);

        swapContent(frameInputs);
        validateSrc();
    }

    private boolean validateSrc() {
        String path = entrySrc.getText();
        if (path == null || path.isBlank()) {
            lblSrcValidation.setText("Please select a folder.");
            lblSrcValidation.setForeground(Config.WARNING_FG);
            return false;
        }

        Path modsPath = Path.of(path, "mods");
        if (Files.isDirectory(modsPath)) {
            lblSrcValidation.setText(Config.APP_NAME + " is installed.");
            lblSrcValidation.setForeground(Config.SUCCESS_FG);
            return true;
        } else {
            lblSrcValidation.setText(Config.APP_NAME + " is not installed.");
            lblSrcValidation.setForeground(Config.ERROR_FG);
            return false;
        }
    }

    private void browseSrc() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select " + Config.APP_NAME + " Instance");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            entrySrc.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void browseDst() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Install Location");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            entryDst.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    // progress screen

    private void updateStatus(String text, Double percent, String subText) {
        SwingUtilities.invokeLater(() -> {
            lblStatus.setText(text);
            lblSubStatus.setText(subText == null ? "" : subText);
            if (percent != null) {
                progressBar.setValue(percent.intValue());
            }
        });
    }

    private void startInstallation() {
        if (!validateSrc()) {
            JOptionPane.showMessageDialog(frame,
                    "Please select a valid " + Config.APP_NAME + " instance folder containing a 'mods' directory.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (!Utils.checkJava(this::log)) {
            return;
        }

        JPanel frameProgress = new JPanel(new GridBagLayout());
        frameProgress.setBorder(BorderFactory.createEmptyBorder(0, 40, 0, 40));

        JPanel progressContainer = new JPanel();
        progressContainer.setLayout(new BoxLayout(progressContainer, BoxLayout.Y_AXIS));

        lblStatus = UI.label("Preparing...", Config.FONT_BODY, Config.TEXT_DARK);
        lblStatus.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblStatus.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));

        lblSubStatus = UI.label(" ", Config.FONT_SMALL, Config.TEXT_MUTED);
        lblSubStatus.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblSubStatus.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));

        progressBar = new JProgressBar(0, 100);
        progressBar.setAlignmentX(Component.CENTER_ALIGNMENT);
        progressBar.setPreferredSize(new Dimension(460, 20));
        progressBar.setMaximumSize(new Dimension(460, 20));

        progressContainer.add(lblStatus);
        progressContainer.add(lblSubStatus);
        progressContainer.add(progressBar);

        frameProgress.add(progressContainer);
        swapContent(frameProgress);

        final String srcPath = entrySrc.getText();
        final String dstPath = entryDst.getText();
        final boolean shortcutFlag = shortcutCheck.isSelected();

        Thread thread = new Thread(() -> Installer.runInstallation(
                srcPath, dstPath, shortcutFlag,
                this::updateStatus,
                this::log,
                this::onInstallSuccess,
                this::onInstallFailure
        ));
        thread.setDaemon(true);
        thread.start();
    }

    private void onInstallSuccess(String publicIp, String localIp) {
        SwingUtilities.invokeLater(() -> showSuccessUi(publicIp, localIp));
        Utils.flushLogs(Path.of(entryDst.getText(), "serverinstaller").toString(), logBuffer);
    }

    private void onInstallFailure(String errorMessage) {
        SwingUtilities.invokeLater(() -> {
            showInputScreen(entrySrc.getText());
            JOptionPane.showMessageDialog(frame,
                    "Installation Failed.\nCheck the logs folder for more details.\n\n" + errorMessage,
                    "Error", JOptionPane.ERROR_MESSAGE);
        });
        Utils.flushLogs(Path.of(entryDst.getText(), "serverinstaller").toString(), logBuffer);
    }

    // success screen

    private void showSuccessUi(String publicIp, String localIp) {
        JPanel frameSuccess = new JPanel();
        frameSuccess.setLayout(new BoxLayout(frameSuccess, BoxLayout.Y_AXIS));
        frameSuccess.setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));

        JLabel lblDone = UI.label("Installation Complete!", Config.FONT_TITLE, Config.HEADER_BG);
        lblDone.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblDone.setBorder(BorderFactory.createEmptyBorder(0, 0, 18, 0));
        frameSuccess.add(lblDone);

        JPanel ipContainer = new JPanel();
        ipContainer.setLayout(new BoxLayout(ipContainer, BoxLayout.Y_AXIS));
        ipContainer.setAlignmentX(Component.CENTER_ALIGNMENT);

        ipContainer.add(ipRow("Public IP (for your friends outside your network):", publicIp, 18));
        ipContainer.add(ipRow("Local IP (for your friends in the same network as you):", localIp, 0));

        frameSuccess.add(ipContainer);

        if (!Config.DONATE_URL.isBlank()) {
            JPanel btnFinishFrame = new JPanel();
            btnFinishFrame.setBorder(BorderFactory.createEmptyBorder(28, 0, 0, 0));

            JButton btnDonate = UI.primaryButton("DONATE");
            btnDonate.addActionListener(e -> Utils.openUrl(Config.DONATE_URL));
            btnFinishFrame.add(btnDonate);

            frameSuccess.add(btnFinishFrame);
        }

        swapContent(frameSuccess);

        if (autorunCheck.isSelected()) {
            runServerNow();
        }
    }

    private JPanel ipRow(String labelText, String value, int bottomPad) {
        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setAlignmentX(Component.LEFT_ALIGNMENT);
        container.setBorder(BorderFactory.createEmptyBorder(0, 0, bottomPad, 0));

        JLabel label = UI.label(labelText, Config.FONT_BODY, Config.TEXT_DARK);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        container.add(label);

        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        JTextField field = new JTextField(value);
        field.setEditable(false);
        field.setHorizontalAlignment(SwingConstants.CENTER);
        field.setFont(Config.FONT_MONO);
        field.setBackground(Config.FIELD_BG);
        field.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(1, 1, 1, 1, Config.BORDER),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)));

        JButton btnCopy = new JButton("Copy");
        btnCopy.setFont(Config.FONT_BODY);
        btnCopy.addActionListener(e -> copyToClipboard(value));

        row.add(field, BorderLayout.CENTER);
        row.add(btnCopy, BorderLayout.EAST);
        container.add(row);

        return container;
    }

    private void copyToClipboard(String text) {
        StringSelection selection = new StringSelection(text);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
        JOptionPane.showMessageDialog(frame, "IP Address copied!", "Copied", JOptionPane.INFORMATION_MESSAGE);
    }

    private void runServerNow() {
        boolean windows = Utils.currentOs() == Utils.OsType.WINDOWS;
        String scriptName = windows ? "run.bat" : "run.sh";
        File script = Path.of(entryDst.getText(), scriptName).toFile();

        if (!script.exists()) {
            JOptionPane.showMessageDialog(frame, scriptName + " not found.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            // launching run.bat directly like this, not 100% sure windows likes running a
            // .bat as if it were an exe, might need cmd /c in front of it if this breaks
            ProcessBuilder pb = windows
                    ? new ProcessBuilder(script.getAbsolutePath())
                    : new ProcessBuilder("sh", script.getAbsolutePath());
            pb.directory(new File(entryDst.getText()));
            pb.start();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, "Failed to start " + scriptName + ": " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // helpers

    private static class SimpleDocListener implements javax.swing.event.DocumentListener {
        private final Runnable callback;

        SimpleDocListener(Runnable callback) {
            this.callback = callback;
        }

        @Override
        public void insertUpdate(javax.swing.event.DocumentEvent e) {
            callback.run();
        }

        @Override
        public void removeUpdate(javax.swing.event.DocumentEvent e) {
            callback.run();
        }

        @Override
        public void changedUpdate(javax.swing.event.DocumentEvent e) {
            callback.run();
        }
    }
}
