import os
import sys
import shutil
import subprocess
import re
import socket
import requests
from datetime import datetime
import tkinter.messagebox as messagebox
import webbrowser

import config

def resource_path(relative_path):
    try:
        base_path = sys._MEIPASS
    except Exception:
        base_path = os.path.abspath(".")

    return os.path.join(base_path, relative_path)

def center_window(root, w, h):
    screen_width = root.winfo_screenwidth()
    screen_height = root.winfo_screenheight()
    x = (screen_width - w) // 2
    y = (screen_height - h) // 2
    root.geometry(f"{w}x{h}+{x}+{y}")

def log_message(buffer, message, error=False):
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    tag = "[ERROR]" if error else "[INFO]"
    buffer.append(f"{timestamp} {tag} {message}")

def flush_logs(dst_folder, buffer, prefix="installer"):
    try:
        logs_folder = os.path.join(dst_folder, "logs")
        os.makedirs(logs_folder, exist_ok=True)

        timestamp = datetime.now().strftime("%Y-%m-%d_%H-%M-%S")
        log_path = os.path.join(logs_folder, f"{prefix}_{timestamp}.log")

        with open(log_path, "w", encoding="utf-8") as f:
            f.write("\n".join(buffer))
    except Exception:
        pass

def detect_instances():
    user_home = os.path.expanduser("~")
    appdata = os.getenv("APPDATA")
    
    cf_candidates = [
        os.path.join(appdata, "CurseForge", "Minecraft", "Instances", "Farming Experience"),
        os.path.join(user_home, "curseforge", "minecraft", "Instances", "Farming Experience"),
    ]
    
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
    try:
        result = subprocess.run(
            ["java", "-version"],
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0
        )
        output = result.stdout
        
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

    ans = messagebox.askyesno(
        "Java 21 Required", 
        "Java 21 is required to run this server but was not found (or is outdated).\n\nWould you like to download it now?",
        icon='warning'
    )
    if ans:
        webbrowser.open(config.JAVA_DOWNLOAD_URL)
    return False

def get_public_ip(log_func):
    try:
        ip = requests.get('https://api.ipify.org', headers=config.HEADERS, timeout=5).text
        log_func(f"Public IP detected: {ip}")
        return ip
    except Exception as e:
        log_func(f"Failed to get public IP: {e}", error=True)
        return "Unknown"

def get_local_ip(log_func):
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        log_func(f"Local IP detected: {ip}")
        return ip
    except Exception:
        return "127.0.0.1"