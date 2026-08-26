package com.farmingexperience.installer;

import javax.swing.*;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;

public final class UI {
    private UI() {}

    public static JLabel label(String text, Font font, Color fg) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(fg);
        return label;
    }

    public static JButton button(String text, Color bg, Font font) {
        JButton button = new JButton(text);
        button.setUI(new BasicButtonUI());
        button.setBackground(bg);
        button.setForeground(Color.WHITE);
        button.setFont(font);
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 18));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    public static JButton primaryButton(String text) {
        return button(text, Config.PRIMARY_BG, Config.FONT_BUTTON_LARGE);
    }
}
