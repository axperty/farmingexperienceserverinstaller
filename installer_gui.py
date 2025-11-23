"""
Farming Experience Server Installer
Version: 25.11.23
Author: Axperty
License: MIT
"""

import tkinter as tk
from tkinter import ttk, filedialog, messagebox
import os
import shutil
import subprocess
import threading
import requests
import fnmatch
import webbrowser
import traceback
import struct
import sys
from datetime import datetime

# --- CONFIGURATION ---
NEOFORGE_VERSION = "21.1.209"
NEOFORGE_INSTALLER_URL = f"https://maven.neoforged.net/releases/net/neoforged/neoforge/{NEOFORGE_VERSION}/neoforge-{NEOFORGE_VERSION}-installer.jar"
ICON_URL = "https://i.imgur.com/d8yZ7JF.png"
EULA_URL = "https://aka.ms/MinecraftEULA"

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
}

EXCLUDED_MODS = [
    "fast-ip-ping*", "entity_model_features*", "entity_texture_features*", "lambdynamiclights*"
]
EXCLUDED_FOLDERS = [".connector"]

SERVER_PROPS = {
    "motd": r"\u00A7l\u00A72Online \u00A7r\u00A77- Farming Experience Server",
    "bug-report-link": "https://github.com/axperty/farmingexperienceserverinstaller",
    "difficulty": "normal",
    "view-distance": "23",
    "enable-command-block": "false"
}

def resource_path(relative_path):
    """ Get absolute path to resource, works for dev and for PyInstaller """
    try:
        # PyInstaller creates a temp folder and stores path in _MEIPASS
        base_path = sys._MEIPASS
    except Exception:
        base_path = os.path.abspath(".")

    return os.path.join(base_path, relative_path)

class ServerInstallerApp:
    def __init__(self, root):
        self.root = root
        self.root.title("Farming Experience Installer")
        
        # --- SET WINDOW ICON ---
        try:
            # This loads the icon bundled inside the exe
            self.root.iconbitmap(resource_path("app.ico"))
        except:
            pass # Fail silently if icon is missing in dev mode
        
        # Window Dimensions & Centering
        w, h = 500, 420
        screen_width = root.winfo_screenwidth()
        screen_height = root.winfo_screenheight()
        x = (screen_width - w) // 2
        y = (screen_height - h) // 2
        self.root.geometry(f"{w}x{h}+{x}+{y}")
        self.root.resizable(False, False)
        
        self.log_buffer = []
        self.log("Installer initialized.")

        style = ttk.Style()
        try: style.theme_use('vista')
        except: style.theme_use('clam')
        style.configure("TProgressbar", thickness=15)

        # Header
        header_frame = tk.Frame(root, bg="#2E7D32")
        header_frame.pack(fill="x", side="top")
        
        lbl_title = tk.Label(header_frame, text="Farming Experience", font=("Segoe UI", 18, "bold"), bg="#2E7D32", fg="white")
        lbl_title.pack(pady=(15, 2))
        
        lbl_subtitle = tk.Label(header_frame, text="Server Installer & Setup", font=("Segoe UI", 10), bg="#2E7D32", fg="#E8F5E9")
        lbl_subtitle.pack(pady=(0, 15))

        self.content_area = tk.Frame(root)
        self.content_area.pack(fill="both", expand=True)

        # Screen 1: Inputs
        self.frame_inputs = tk.Frame(self.content_area, padx=20, pady=20)
        self.frame_inputs.pack(fill="both", expand=True)

        tk.Label(self.frame_inputs, text="Modpack Instance Location:", font=("Segoe UI", 9, "bold")).pack(anchor="w")
        frame_src = tk.Frame(self.frame_inputs)
        frame_src.pack(fill="x", pady=(2, 10))
        
        self.src_var = tk.StringVar(value=self.find_default_modpack_path())
        self.entry_src = ttk.Entry(frame_src, textvariable=self.src_var)
        self.entry_src.pack(side="left", fill="x", expand=True, padx=(0, 5))
        self.btn_src = ttk.Button(frame_src, text="Browse", width=8, command=self.browse_src)
        self.btn_src.pack(side="right")

        tk.Label(self.frame_inputs, text="Install Server To:", font=("Segoe UI", 9, "bold")).pack(anchor="w")
        frame_dst = tk.Frame(self.frame_inputs)
        frame_dst.pack(fill="x", pady=(2, 10))
        
        self.dst_var = tk.StringVar(value=os.path.join(os.getcwd(), "Farming_Experience_Server"))
        self.entry_dst = ttk.Entry(frame_dst, textvariable=self.dst_var)
        self.entry_dst.pack(side="left", fill="x", expand=True, padx=(0, 5))
        self.btn_dst = ttk.Button(frame_dst, text="Browse", width=8, command=self.browse_dst)
        self.btn_dst.pack(side="right")

        self.autorun_var = tk.BooleanVar(value=True)
        self.shortcut_var = tk.BooleanVar(value=True)
        
        opts_frame = tk.Frame(self.frame_inputs)
        opts_frame.pack(fill="x", pady=(0, 15))
        
        ttk.Checkbutton(opts_frame, text="Create Desktop Shortcut", variable=self.shortcut_var).pack(anchor="w")
        ttk.Checkbutton(opts_frame, text="Start server automatically when finished", variable=self.autorun_var).pack(anchor="w")

        bottom_frame = tk.Frame(self.frame_inputs)
        bottom_frame.pack(fill="x", side="bottom")

        eula_frame = tk.Frame(bottom_frame)
        eula_frame.pack(side="left", anchor="center")
        tk.Label(eula_frame, text="Clicking Create Server agrees to ", font=("Segoe UI", 8), fg="#666").pack(side="left")
        link = tk.Label(eula_frame, text="Minecraft EULA", font=("Segoe UI", 8, "underline"), fg="#0066CC", bg=root.cget("bg"), cursor="hand2")
        link.pack(side="left")
        link.bind("<Button-1>", lambda e: webbrowser.open(EULA_URL))

        self.btn_install = tk.Button(bottom_frame, text="CREATE SERVER", bg="#4CAF50", fg="white", 
                                     font=("Segoe UI", 10, "bold"), relief="flat", cursor="hand2",
                                     padx=15, pady=5, command=self.start_installation)
        self.btn_install.pack(side="right")

        # Screen 2: Progress
        self.frame_progress = tk.Frame(self.content_area, padx=40)
        
        self.progress_container = tk.Frame(self.frame_progress)
        self.progress_container.place(relx=0.5, rely=0.5, anchor="center", relwidth=1.0)

        self.lbl_status = tk.Label(self.progress_container, text="Preparing...", font=("Segoe UI", 11), fg="#333")
        self.lbl_status.pack(fill="x", pady=(0, 10))
        
        self.progress_var = tk.DoubleVar()
        self.progress_bar = ttk.Progressbar(self.progress_container, variable=self.progress_var, maximum=100)
        self.progress_bar.pack(fill="x")

        # Screen 3: Success
        self.frame_success = tk.Frame(self.content_area, padx=20, pady=20)
        
        tk.Label(self.frame_success, text="Installation Complete!", font=("Segoe UI", 16, "bold"), fg="#2E7D32").pack(pady=(20, 10))
        tk.Label(self.frame_success, text="Your Server IP Address:", font=("Segoe UI", 11)).pack()
        
        ip_frame = tk.Frame(self.frame_success)
        ip_frame.pack(pady=10)
        
        self.ip_entry = ttk.Entry(ip_frame, font=("Consolas", 12), justify="center", width=22)
        self.ip_entry.pack(side="left", padx=5)
        ttk.Button(ip_frame, text="Copy", width=8, command=self.copy_ip).pack(side="left")
        
        btn_finish_frame = tk.Frame(self.frame_success)
        btn_finish_frame.pack(pady=20)
        ttk.Button(btn_finish_frame, text="Open Folder", command=self.open_folder).pack(side="left", padx=10)
        ttk.Button(btn_finish_frame, text="Run Server Now", command=self.run_server_now).pack(side="left", padx=10)

    def log(self, message, error=False):
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        tag = "[ERROR]" if error else "[INFO]"
        self.log_buffer.append(f"{timestamp} {tag} {message}")

    def flush_logs(self, dst_folder):
        try:
            if not os.path.exists(dst_folder): os.makedirs(dst_folder)
            with open(os.path.join(dst_folder, "installer.log"), "w", encoding="utf-8") as f:
                f.write("\n".join(self.log_buffer))
        except: pass

    def find_default_modpack_path(self):
        user_home = os.path.expanduser("~")
        appdata = os.getenv("APPDATA")
        candidates = [
            os.path.join(appdata, "CurseForge", "Minecraft", "Instances", "Farming Experience"),
            os.path.join(user_home, "curseforge", "minecraft", "Instances", "Farming Experience"),
        ]
        for path in candidates:
            if os.path.exists(path): return path
        return ""

    def browse_src(self):
        f = filedialog.askdirectory(title="Select Farming Experience Instance")
        if f: self.src_var.set(f)

    def browse_dst(self):
        f = filedialog.askdirectory(title="Select Install Location")
        if f: self.dst_var.set(f)

    def update_status(self, text, percent=None):
        self.lbl_status.config(text=text)
        if percent is not None:
            self.progress_var.set(percent)
        self.root.update_idletasks()

    def copy_ip(self):
        self.root.clipboard_clear()
        self.root.clipboard_append(self.ip_entry.get())
        messagebox.showinfo("Copied", "IP Address copied!")

    def open_folder(self):
        os.startfile(self.dst_var.get())

    def run_server_now(self):
        bat = os.path.join(self.dst_var.get(), "run.bat")
        if os.path.exists(bat):
            subprocess.Popen([bat], cwd=self.dst_var.get(), creationflags=subprocess.CREATE_NEW_CONSOLE)
        else:
            messagebox.showerror("Error", "run.bat not found.")

    def start_installation(self):
        src = self.src_var.get()
        if not os.path.exists(os.path.join(src, "mods")):
            messagebox.showerror("Error", "Invalid Source: 'mods' folder missing.")
            return
        
        try:
            subprocess.run(["java", "-version"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True, creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
        except:
            messagebox.showerror("Error", "Java 21 is required but not installed.")
            return

        self.frame_inputs.pack_forget()
        self.frame_progress.pack(fill="both", expand=True)
        
        threading.Thread(target=self.run_process, args=(src, self.dst_var.get()), daemon=True).start()

    def png_to_ico(self, png_path, ico_path):
        try:
            with open(png_path, "rb") as f:
                png_data = f.read()
            
            w, h = struct.unpack(">II", png_data[16:24])
            b_w = 0 if w >= 256 else w
            b_h = 0 if h >= 256 else h
            size = len(png_data)
            
            with open(ico_path, "wb") as f:
                f.write(struct.pack("<HHH", 0, 1, 1))
                f.write(struct.pack("<BBBBHHII", b_w, b_h, 0, 0, 1, 32, size, 22))
                f.write(png_data)
            return True
        except Exception as e:
            self.log(f"Failed to convert PNG to ICO: {e}", error=True)
            return False

    def download_file(self, url, filepath):
        self.log(f"Downloading {url}")
        try:
            response = requests.get(url, headers=HEADERS, stream=True)
            response.raise_for_status()
            with open(filepath, 'wb') as f:
                for chunk in response.iter_content(chunk_size=8192):
                    f.write(chunk)
        except Exception as e:
            self.log(f"Download error: {e}", error=True)
            raise e

    def create_shortcut(self, target_bat, icon_path):
        self.log("Creating shortcut...")
        try:
            desktop = os.path.join(os.path.join(os.environ['USERPROFILE']), 'Desktop')
            shortcut_path = os.path.join(desktop, "Farming Experience Server.lnk")
            
            vbs_script = f"""
            Set oWS = WScript.CreateObject("WScript.Shell")
            Set oLink = oWS.CreateShortcut("{shortcut_path}")
            oLink.TargetPath = "{target_bat}"
            oLink.WorkingDirectory = "{os.path.dirname(target_bat)}"
            oLink.IconLocation = "{icon_path}"
            oLink.Save
            """
            vbs_file = os.path.join(os.path.dirname(target_bat), "create_shortcut.vbs")
            with open(vbs_file, "w") as f: f.write(vbs_script)
            subprocess.run(["cscript", "//Nologo", vbs_file], check=True, creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
            os.remove(vbs_file)
        except Exception as e:
            self.log(f"Shortcut failed: {e}", error=True)

    def run_process(self, src_path, dst_path):
        try:
            if not os.path.exists(dst_path): os.makedirs(dst_path)

            self.update_status("Copying Mods...", 0)
            src_mods = os.path.join(src_path, "mods")
            dst_mods = os.path.join(dst_path, "mods")
            if os.path.exists(dst_mods): shutil.rmtree(dst_mods)
            os.makedirs(dst_mods)

            all_files = os.listdir(src_mods)
            total = len(all_files)
            
            self.log(f"--- STARTING MOD COPY ({total} items) ---")
            
            for i, item in enumerate(all_files):
                full_src = os.path.join(src_mods, item)
                full_dst = os.path.join(dst_mods, item)

                if os.path.isdir(full_src):
                    if item in EXCLUDED_FOLDERS: 
                        self.log(f"[SKIP] Directory excluded: {item}")
                        continue
                    self.log(f"[COPY] Directory: {item}")
                    shutil.copytree(full_src, full_dst)
                else:
                    is_ex = False
                    for p in EXCLUDED_MODS:
                        if fnmatch.fnmatch(item.lower(), p.lower()):
                            is_ex = True
                            break
                    if is_ex: 
                        self.log(f"[SKIP] File excluded: {item}")
                        continue
                    
                    self.log(f"[COPY] File: {item}")
                    shutil.copy2(full_src, full_dst)
                
                pct = (i / total) * 40
                if i % 5 == 0: 
                     self.update_status(f"Copying mods ({int((i/total)*100)}%)...", pct)

            self.log("--- MOD COPY FINISHED ---")

            self.update_status("Copying Configs...", 40)
            src_cfg = os.path.join(src_path, "config")
            dst_cfg = os.path.join(dst_path, "config")
            if os.path.exists(src_cfg):
                if os.path.exists(dst_cfg): shutil.rmtree(dst_cfg)
                shutil.copytree(src_cfg, dst_cfg)
                self.log("Configs copied.")

            self.update_status("Downloading Server Icon...", 50)
            png_path = os.path.join(dst_path, "server-icon.png")
            ico_path = os.path.join(dst_path, "server-icon.ico")
            try: 
                self.download_file(ICON_URL, png_path)
                self.png_to_ico(png_path, ico_path)
            except: 
                self.log("Icon download/conversion failed", error=True)

            self.update_status("Configuring Server...", 55)
            props_file = os.path.join(dst_path, "server.properties")
            with open(props_file, "w") as f:
                f.write("#Minecraft Server Properties\n")
                for k, v in SERVER_PROPS.items(): f.write(f"{k}={v}\n")
            self.log("server.properties generated.")

            self.update_status("Downloading NeoForge...", 60)
            installer_jar = os.path.join(dst_path, "installer.jar")
            self.download_file(NEOFORGE_INSTALLER_URL, installer_jar)

            self.update_status("Installing Server Loader...", 75)
            self.log("Running NeoForge installer...")
            subprocess.run(["java", "-jar", "installer.jar", "--installServer"], cwd=dst_path, check=True, creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
            
            if os.path.exists(installer_jar): os.remove(installer_jar)
            if os.path.exists(os.path.join(dst_path, "installer.jar.log")): os.remove(os.path.join(dst_path, "installer.jar.log"))

            run_bat_path = os.path.join(dst_path, "run.bat")
            if os.path.exists(run_bat_path):
                try:
                    with open(run_bat_path, "r") as f: content = f.read()
                    if "%*" in content: content = content.replace("%*", "--nogui %*")
                    else: content += " --nogui"
                    with open(run_bat_path, "w") as f: f.write(content)
                    self.log("run.bat patched for nogui.")
                except: pass

            with open(os.path.join(dst_path, "eula.txt"), "w") as f: f.write("eula=true\n")

            if self.shortcut_var.get():
                self.update_status("Creating Shortcut...", 90)
                self.create_shortcut(os.path.join(dst_path, "run.bat"), ico_path)

            self.update_status("Fetching IP...", 95)
            public_ip = "Unknown"
            try: 
                public_ip = requests.get('https://api.ipify.org', headers=HEADERS).text
                self.log(f"Public IP detected: {public_ip}")
            except: pass

            self.update_status("Complete!", 100)
            self.flush_logs(dst_path)

            self.root.after(0, lambda: self.show_success_ui(public_ip, dst_path))

        except Exception as e:
            trace = traceback.format_exc()
            self.log(f"CRASH: {trace}", error=True)
            self.flush_logs(dst_path)
            messagebox.showerror("Error", f"Installation Failed.\nCheck installer.log\n\n{e}")
            self.root.after(0, self.reset_ui)

    def reset_ui(self):
        self.frame_progress.pack_forget()
        self.frame_inputs.pack(fill="both", expand=True)

    def show_success_ui(self, ip, dst_path):
        self.frame_progress.pack_forget()
        self.ip_entry.insert(0, ip)
        self.frame_success.pack(fill="both", expand=True)
        if self.autorun_var.get():
            self.run_server_now()

if __name__ == "__main__":
    root = tk.Tk()
    app = ServerInstallerApp(root)
    root.mainloop()