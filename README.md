# Farming Experience Server Installer

[![CurseForge Downloads](https://img.shields.io/curseforge/dt/974008?style=flat&logo=curseforge&logoColor=%23F16436&label=CurseForge&labelColor=%232D2C2C&color=%23F16436)](https://www.curseforge.com/minecraft/modpacks/farming-experience)
[![Discord](https://img.shields.io/discord/1194733791818821663?style=flat&logo=discord&logoColor=%23FFFFFF&label=Discord&labelColor=2D2C2C&color=%234e992e)](https://discord.gg/e2BQx4bbsU)
[![PayPal](https://img.shields.io/badge/Donate%20on%20PayPal-0079C1?style=flat&logo=paypal)](https://paypal.me/kevgelhorn)

![Farming Experience](https://i.imgur.com/TmwuZvQ.png)
***

### Overview
A plug-and-play installer for creating a server for the Farming Experience modpack so you can play with your friends.

### Features
- Automatically finds your local CurseForge installation of the Farming Experience modpack to copy the necessary files.
- Downloads the specific NeoForge loader and installs it silently.

### Prerequisites
Before running the installer, ensure you have:

- **Java 21 Installed:** Required to run the NeoForge installer and server. [Download Java 21](https://adoptium.net/).
- **Farming Experience:** You must have the **Farming Experience** modpack installed via the CurseForge App.
- **Port Forwarding:** For your friends to connect to your server, your internet router must allow the connection. If you have already opened port `25565`, the server will work immediately, otherwise you will need to log into your router and port forward port `25565` (TCP/UDP) to your computer.

### Installation
1.  Download **`Farming Experience Server Installer`** from the [releases](https://github.com/axperty/farmingexperienceserverinstaller/releases/tag/release) page.
2.  Place it in an empty folder (recommended) or anywhere you want, the installer will later create a separate folder for the server contents.
3.  Run the **`Farming Experience Server Installer`** executable.
4.  The app will try to detect your modpack folder. If it fails, click **Browse** to select your `Farming Experience` instance folder.
5.  Click **Create Server**, by clicking this button you will agree to [Minecraft's EULA](https://aka.ms/MinecraftEULA).
6.  Once finished, run the server, copy the IP address, and send it to your friends!

### Building from Source
If you want to modify the code or compile it yourself, follow these steps.

**Requirements:**
- Python 3.10+
- pip package manager

**Install Dependencies:**
```bash
pip install requests pyinstaller
```

**App Icon (Optional):**
Ensure you have the icon file **`app.ico`** in the same directory as the script. The script handles PNG to ICO conversion for the shortcut, but the EXE file icon requires a pre-made .ico file for compilation.

**Compile:**
Run this command in your terminal to build the standalone executable with the icon embedded:

```powershell
python -m PyInstaller --noconsole --onefile --icon="app.ico" --add-data "app.ico;." --name="Farming Experience Server Installer" installer_gui.py
```

The output file will be located in the `dist/` folder.
