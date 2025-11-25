import os
import sys
import subprocess
import re
import socket
import requests
from datetime import datetime
import tkinter.messagebox as messagebox
import webbrowser

# Local imports
import config

def resource_path(relative_path):
    """ Get absolute path to resource, works for dev and for PyInstaller """
    try:
        # PyInstaller creates a temp folder and stores path in _MEIPASS
        base_path = sys._MEIPASS
    except Exception:
        base_path = os.path.abspath(".")

    return os.path.join(base_path, relative_path)

def center_window(root, w, h):
    """ Centers the Tkinter window on the screen. """
    screen_width = root.winfo_screenwidth()
    screen_height = root.winfo_screenheight()
    x = (screen_width - w) // 2
    y = (screen_height - h) // 2
    root.geometry(f"{w}x{h}+{x}+{y}")

def log_message(buffer, message, error=False):
    """ Appends a timestamped message to the log buffer. """
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    tag = "[ERROR]" if error else "[INFO]"
    buffer.append(f"{timestamp} {tag} {message}")

def flush_logs(dst_folder, buffer):
    """ Writes the log buffer to a file. """
    try:
        if not os.path.exists(dst_folder):
            os.makedirs(dst_folder)
        with open(os.path.join(dst_folder, "installer.log"), "w", encoding="utf-8") as f:
            f.write("\n".join(buffer))
    except Exception:
        pass

def detect_instances():
    """ 
    Searches standard Modrinth and CurseForge directories for the instance.
    Returns: (found_cf_path, found_mr_path). Elements are None if not found. 
    """
    user_home = os.path.expanduser("~")
    appdata = os.getenv("APPDATA")
    
    # Potential CurseForge Paths
    cf_candidates = [
        os.path.join(appdata, "CurseForge", "Minecraft", "Instances", "Farming Experience"),
        os.path.join(user_home, "curseforge", "minecraft", "Instances", "Farming Experience"),
    ]
    
    # Potential Modrinth Paths
    mr_candidates = [
        os.path.join(appdata, "ModrinthApp", "profiles", "Farming Experience")
    ]
    
    found_cf = None
    for path in cf_candidates:
        if os.path.exists(path) and os.path.isdir(os.path.join(path, "mods")):
            found_cf = path
            break
            
    found_mr = None
    for path in mr_candidates:
        if os.path.exists(path) and os.path.isdir(os.path.join(path, "mods")):
            found_mr = path
            break
            
    return found_cf, found_mr

def check_java(log_func):
    """ 
    Checks for Java 21. Returns True if valid, False otherwise. 
    If invalid, prompts the user to download it.
    """
    try:
        # Create flag to hide console on Windows
        creation_flags = subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0
        
        result = subprocess.run(
            ["java", "-version"], 
            stdout=subprocess.PIPE, 
            stderr=subprocess.STDOUT, 
            text=True, 
            creationflags=creation_flags
        )
        output = result.stdout
        
        # Look for version "21.x.x"
        match = re.search(r'version "(\d+)', output)
        if match:
            major_version = int(match.group(1))
            if major_version >= 21:
                return True
            else:
                log_func(f"Java version mismatch. Found: {major_version}, Required: 21+")
        else:
            log_func("Could not parse Java version from output.")

    except FileNotFoundError:
        log_func("Java executable not found in PATH.")
    except Exception as e:
        log_func(f"Java check error: {e}")

    # If we get here, Java is missing or wrong version
    ans = messagebox.askyesno(
        "Java 21 Required", 
        "Java 21 is required to run this server but was not found (or is outdated).\n\nWould you like to download it now?",
        icon='warning'
    )
    if ans:
        webbrowser.open(config.JAVA_DOWNLOAD_URL)
    return False

def get_public_ip(log_func):
    """ Fetches public IP from api.ipify.org """
    try: 
        ip = requests.get('https://api.ipify.org', headers=config.HEADERS, timeout=5).text
        log_func(f"Public IP detected: {ip}")
        return ip
    except Exception as e:
        log_func(f"Failed to get public IP: {e}", error=True)
        return "Unknown"

def get_local_ip(log_func):
    """ Fetches local LAN IP by connecting to a DNS server. """
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        # Connect to a public DNS server (doesn't send data)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        log_func(f"Local IP detected: {ip}")
        return ip
    except Exception:
        return "127.0.0.1"