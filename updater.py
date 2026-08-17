import tkinter as tk
from tkinter import ttk, messagebox
import os
import threading
import shutil
import traceback
from datetime import datetime

import config
import utils
from installer import copy_instance_files

def backup_world(dst_path, update_status, log_func):
    update_status("Backing up World...", 0)
    world_name = "world"
    props_path = os.path.join(dst_path, "server.properties")
    
    if os.path.exists(props_path):
        try:
            with open(props_path, "r") as f:
                for line in f:
                    if line.strip().startswith("level-name="):
                        world_name = line.strip().split("=", 1)[1]
                        break
        except Exception as e:
            log_func(f"Failed to read server.properties: {e}", error=True)
            
    world_dir = os.path.join(dst_path, world_name)
    if not os.path.exists(world_dir):
        log_func(f"World folder '{world_name}' not found. Skipping backup.")
        return
        
    backups_dir = os.path.join(dst_path, "backups")
    if not os.path.exists(backups_dir):
        os.makedirs(backups_dir)
        
    timestamp = datetime.now().strftime("%Y-%m-%d_%H-%M-%S")
    backup_filename = f"{world_name}_{timestamp}"
    backup_path = os.path.join(backups_dir, backup_filename)
    
    try:
        log_func(f"Backing up world '{world_name}' to backups/{backup_filename}.zip...")
        shutil.make_archive(backup_path, 'zip', world_dir)
        log_func("World backup complete.")
    except Exception as e:
        log_func(f"World backup failed: {e}", error=True)

def run_update(src_path, dst_path, update_status, log_func, on_success, on_failure):
    try:
        backup_world(dst_path, update_status, log_func)
        copy_instance_files(src_path, dst_path, update_status, log_func)
        update_status("Complete!", 100)
        on_success()
    except Exception as e:
        trace = traceback.format_exc()
        log_func(f"CRASH: {trace}", error=True)
        on_failure(str(e))

class UpdateApp:
    def __init__(self, root, src_path, dst_path):
        self.root = root
        self.root.title("Update Server - Farming Experience Server Installer")
        
        try:
            self.root.iconbitmap(utils.resource_path("app.ico"))
        except Exception:
            pass
        
        utils.center_window(root, 400, 300)
        self.root.resizable(False, False)
        
        self.log_buffer = []
        self.src_path = src_path
        self.dst_path = dst_path
        
        style = ttk.Style()
        try:
            style.theme_use('vista')
        except Exception:
            style.theme_use('clam')
        style.configure("TProgressbar", thickness=15)

        header_frame = tk.Frame(root, bg="#2E7D32")
        header_frame.pack(fill="x", side="top")
        
        lbl_title = tk.Label(header_frame, text="Farming Experience", font=("Segoe UI", 18, "bold"), bg="#2E7D32", fg="white")
        lbl_title.pack(pady=(15, 2))
        
        lbl_subtitle = tk.Label(header_frame, text="Update Server", font=("Segoe UI", 10), bg="#2E7D32", fg="#E8F5E9")
        lbl_subtitle.pack(pady=(0, 15))

        self.content_area = tk.Frame(root, padx=20, pady=20)
        self.content_area.pack(fill="both", expand=True)

        if not src_path or not dst_path:
            tk.Label(self.content_area, text="Invalid parameters passed to updater.", fg="red").pack()
            return
            
        self.lbl_ready = tk.Label(self.content_area, text="Ready to update your server mods and configs?", font=("Segoe UI", 10))
        self.lbl_ready.pack(pady=(0, 20))
        
        self.btn_update = tk.Button(self.content_area, text="UPDATE NOW", **config.PRIMARY_BTN_STYLE, command=self.start_update)
        self.btn_update.pack()

    def log(self, message, error=False):
        utils.log_message(self.log_buffer, message, error)
        
    def update_status(self, text, percent=None, sub_text=""):
        self.lbl_status.config(text=text)
        if percent is not None:
            self.progress_var.set(percent)
        self.root.update_idletasks()

    def start_update(self):
        self.btn_update.pack_forget()
        self.lbl_ready.pack_forget()
        
        self.progress_container = tk.Frame(self.content_area)
        self.progress_container.pack(fill="x", pady=10)

        self.lbl_status = tk.Label(self.progress_container, text="Preparing...", font=("Segoe UI", 10), fg="#333")
        self.lbl_status.pack(fill="x", pady=(0, 5))
        
        self.progress_var = tk.DoubleVar()
        self.progress_bar = ttk.Progressbar(self.progress_container, variable=self.progress_var, maximum=100)
        self.progress_bar.pack(fill="x")
        
        threading.Thread(
            target=run_update,
            args=(
                self.src_path,
                self.dst_path,
                self.update_status,
                self.log,
                self.on_update_success,
                self.on_update_failure
            ),
            daemon=True
        ).start()

    def on_update_success(self):
        self.root.after(0, self._show_success_ui)
        utils.flush_logs(os.path.join(self.dst_path, "serverinstaller"), self.log_buffer, prefix="update")

    def on_update_failure(self, error_message):
        self.root.after(0, lambda: messagebox.showerror("Error", f"Update Failed.\nCheck the logs folder.\n\n{error_message}"))
        self.root.after(0, self.root.destroy)
        utils.flush_logs(os.path.join(self.dst_path, "serverinstaller"), self.log_buffer, prefix="update")

    def _show_success_ui(self):
        self.progress_container.pack_forget()
        tk.Label(self.content_area, text="Update Complete!", font=("Segoe UI", 14, "bold"), fg="#2E7D32").pack(pady=(10, 20))
        btn_close = tk.Button(self.content_area, text="CLOSE", **config.PRIMARY_BTN_STYLE, command=self.root.destroy)
        btn_close.pack()
