NEOFORGE_VERSION = "21.1.248"
NEOFORGE_INSTALLER_URL = f"https://maven.neoforged.net/releases/net/neoforged/neoforge/{NEOFORGE_VERSION}/neoforge-{NEOFORGE_VERSION}-installer.jar"

ICON_URL = "https://i.imgur.com/d8yZ7JF.png"
EULA_URL = "https://aka.ms/MinecraftEULA"
JAVA_DOWNLOAD_URL = "https://adoptium.net/temurin/releases/?version=21"
DONATE_URL = "https://www.paypal.me/kevgelhorn"

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
}

EXCLUDED_MODS = [
    "fast-ip-ping*",
    "entity_model_features*",
    "entity_texture_features*",
    "lambdynamiclights*",
    "sodium*",
    "holdmyitemsnf*",
    "fog*",
    "raised*"
]

EXCLUDED_FOLDERS = [
    ".connector"
]

SERVER_PROPS = {
    "motd": r"\u00A7l\u00A72Online \u00A7r\u00A77- Farming Experience Server",
    "bug-report-link": "https://github.com/axperty/farmingexperienceserverinstaller",
    "difficulty": "normal",
    "view-distance": "20",
    "enable-command-block": "false"
}

PRIMARY_BTN_STYLE = {
    "bg": "#4CAF50", "fg": "white", "font": ("Segoe UI", 12, "bold"),
    "relief": "flat", "cursor": "hand2", "padx": 20, "pady": 10
}
CREATE_SERVER_BTN_STYLE = {
    "bg": "#4CAF50", "fg": "white", "font": ("Segoe UI", 10, "bold"),
    "relief": "flat", "cursor": "hand2", "padx": 15, "pady": 5
}
SECONDARY_BTN_STYLE = {
    "font": ("Segoe UI", 10, "bold"), "relief": "flat", "cursor": "hand2", "pady": 8
}
