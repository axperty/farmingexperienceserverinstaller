package com.farmingexperience.installer;

import javax.swing.*;

public final class Main {
    private Main() {}

    public static void main(String[] args) {
        String updateSrc = null;
        String updateDst = null;

        for (int i = 0; i < args.length; i++) {
            if ("--update".equals(args[i]) && i + 2 < args.length) {
                updateSrc = args[i + 1];
                updateDst = args[i + 2];
                break;
            }
        }

        final String finalSrc = updateSrc;
        final String finalDst = updateDst;

        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame();
            if (finalSrc != null) {
                new Updater.UpdateApp(frame, finalSrc, finalDst);
            } else {
                new ServerInstallerApp(frame);
            }
        });
    }
}
