package com.farmingexperience.installer;

import java.awt.Color;
import java.awt.Font;
import java.util.List;
import java.util.Map;

public final class Config {
    private Config() {}

    // Modpack Identity Section

    // Server Installer Name
    public static final String APP_NAME = "Farming Experience";

    // Modpack Instance Name
    public static final String INSTANCE_NAME = "Farming Experience";

    // Server Default Folder Installation
    public static final String SERVER_FOLDER_NAME = "Farming_Experience_Server";

    // NeoForge Server Version and URL
    public static final String NEOFORGE_VERSION = "21.1.248";
    public static final String NEOFORGE_INSTALLER_URL = "https://maven.neoforged.net/releases/net/neoforged/neoforge/" + NEOFORGE_VERSION + "/neoforge-" + NEOFORGE_VERSION + "-installer.jar";

    // Server Icon
    public static final String ICON_URL = "https://i.imgur.com/d8yZ7JF.png";

    // App/Window Icon (downloaded at startup, PNG format)
    public static final String APP_ICON_URL = "https://raw.githubusercontent.com/axperty/farmingexperienceserverinstaller/java-port/src/main/resources/app-icon.png";

    // Server Installer Repository
    public static final String REPO_URL = "https://github.com/axperty/farmingexperienceserverinstaller";

    // Wiki Link (leave URL blank to disable)
    public static final String WIKI_URL = "https://axperty.github.io/farmingexperience";

    // Donate Button (leave URL blank to disable)
    public static final String DONATE_URL = "https://www.paypal.me/kevgelhorn";

    // Excluded Mods List
    public static final List<String> EXCLUDED_MODS = List.of(
            "fast-ip-ping*",
            "entity_model_features*",
            "entity_texture_features*",
            "lambdynamiclights*",
            "sodium*",
            "holdmyitemsnf*",
            "fog*",
            "raised*"
    );

    // Excluded Folders List
    public static final List<String> EXCLUDED_FOLDERS = List.of(
            ".connector"
    );

    // Server Properties File Pre-Configuration
    public static final Map<String, String> SERVER_PROPS = buildServerProps();

    private static Map<String, String> buildServerProps() {
        Map<String, String> props = new java.util.LinkedHashMap<>();
        props.put("motd", "\\u00A7l\\u00A72Online \\u00A7r\\u00A77- " + APP_NAME + " Server");
        props.put("bug-report-link", REPO_URL);
        props.put("difficulty", "normal");
        props.put("view-distance", "20");
        props.put("enable-command-block", "false");
        return props;
    }

    // Minecraft EULA URL
    public static final String EULA_URL = "https://aka.ms/MinecraftEULA";

    // Java SDK Download URL
    public static final String JAVA_DOWNLOAD_URL = "https://adoptium.net/temurin/releases/?version=21";

    // Browser Agent
    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    // UI type scale, not sure if segoe ui font will work on macOS or linux.
    private static final String UI_FONT_FAMILY = "Segoe UI";

    public static final Font FONT_TITLE = new Font(UI_FONT_FAMILY, Font.BOLD, 22);
    public static final Font FONT_SUBTITLE = new Font(UI_FONT_FAMILY, Font.PLAIN, 13);
    public static final Font FONT_HEADING = new Font(UI_FONT_FAMILY, Font.BOLD, 14);
    public static final Font FONT_BODY = new Font(UI_FONT_FAMILY, Font.PLAIN, 14);
    public static final Font FONT_SMALL = new Font(UI_FONT_FAMILY, Font.PLAIN, 12);
    public static final Font FONT_BUTTON = new Font(UI_FONT_FAMILY, Font.BOLD, 14);
    public static final Font FONT_BUTTON_LARGE = new Font(UI_FONT_FAMILY, Font.BOLD, 16);
    public static final Font FONT_MONO = new Font("Consolas", Font.BOLD, 16);

    // Theme colors
    public static final Color PRIMARY_BG = new Color(0x4C, 0xAF, 0x50);
    public static final Color HEADER_BG = new Color(0x2E, 0x7D, 0x32);
    public static final Color HEADER_SUBTITLE_FG = new Color(0xE8, 0xF5, 0xE9);

    public static final Color TEXT_DARK = new Color(0x33, 0x33, 0x33);
    public static final Color TEXT_MEDIUM = new Color(0x55, 0x55, 0x55);
    public static final Color TEXT_MUTED = new Color(0x66, 0x66, 0x66);

    public static final Color SUCCESS_FG = new Color(0x2E, 0x7D, 0x32);
    public static final Color ERROR_FG = new Color(0xC6, 0x28, 0x28);
    public static final Color WARNING_FG = new Color(0xE6, 0x51, 0x00);
    public static final Color LINK_FG = new Color(0x00, 0x66, 0xCC);

    public static final Color FIELD_BG = new Color(0xF5, 0xF5, 0xF5);
    public static final Color BORDER = new Color(0xCC, 0xCC, 0xCC);
}
