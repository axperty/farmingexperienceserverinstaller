import tkinter as tk
from tkinter import ttk, filedialog, messagebox
import os
import threading
import webbrowser
import subprocess

# Local imports
import config
import utils
import installer

class ServerInstallerApp:
    def __init__(self, root):
        self.root = root
        self.root.title("Farming Experience Server Installer")
        
        # --- SET WINDOW ICON ---
        try:
            self.root.iconbitmap(utils.resource_path("app.ico"))
        except:
            pass
        
        # Window Dimensions & Centering
        w, h = 500, 420
        utils.center_window(root, w, h)
        self.root.resizable(False, False)
        
        self.log_buffer = []
        self.log("Installer initialized.")

        style = ttk.Style()
        try: style.theme_use('vista')
        except: style.theme_use('clam')
        style.configure("TProgressbar", thickness=15)

        # Header (Always visible)
        header_frame = tk.Frame(root, bg="#2E7D32")
        header_frame.pack(fill="x", side="top")
        
        lbl_title = tk.Label(header_frame, text="Farming Experience", font=("Segoe UI", 18, "bold"), bg="#2E7D32", fg="white")
        lbl_title.pack(pady=(15, 2))
        
        lbl_subtitle = tk.Label(header_frame, text="Server Installer", font=("Segoe UI", 10), bg="#2E7D32", fg="#E8F5E9")
        lbl_subtitle.pack(pady=(0, 15))

        # Main Content Container
        self.content_area = tk.Frame(root)
        self.content_area.pack(fill="both", expand=True)

        # 1. Detect Instances using utils
        cf_path, mr_path = utils.detect_instances()

        # 2. Decide Initial Screen
        if cf_path and mr_path:
            # Conflict: Show in-app selection screen
            self.show_platform_selection(cf_path, mr_path)
        else:
            # No conflict: Go straight to inputs
            initial_path = cf_path or mr_path or ""
            self.show_input_screen(initial_path)

    def log(self, message, error=False):
        # Buffer logs for saving later
        utils.log_message(self.log_buffer, message, error)

    def show_platform_selection(self, cf_path, mr_path):
        """Displays the conflict resolution screen inside the main window."""
        self.frame_selection = tk.Frame(self.content_area, padx=20, pady=20)
        self.frame_selection.pack(fill="both", expand=True)
        
        tk.Label(self.frame_selection, text="Multiple Instances Found", font=("Segoe UI", 12, "bold"), fg="#333").pack(pady=(30, 10))
        tk.Label(self.frame_selection, text="We found Farming Experience installed on both\nModrinth and CurseForge.", font=("Segoe UI", 10), justify="center", fg="#555").pack(pady=(0, 25))
        
        btn_container = tk.Frame(self.frame_selection)
        btn_container.pack(fill="x", padx=50)

        # Modrinth Button
        tk.Button(btn_container, text="Use Modrinth Installation", bg="#1BD96A", fg="white", 
                  font=("Segoe UI", 10, "bold"), relief="flat", cursor="hand2", pady=8,
                  command=lambda: self.transition_to_input(mr_path)).pack(fill="x", pady=5)
        
        # CurseForge Button
        tk.Button(btn_container, text="Use CurseForge Installation", bg="#F57C00", fg="white", 
                  font=("Segoe UI", 10, "bold"), relief="flat", cursor="hand2", pady=8,
                  command=lambda: self.transition_to_input(cf_path)).pack(fill="x", pady=5)

    def transition_to_input(self, selected_path):
        """Remove selection screen and show input screen."""
        self.frame_selection.pack_forget()
        self.show_input_screen(selected_path)

    def show_input_screen(self, initial_path):
        """Builds and displays the main input form."""
        self.frame_inputs = tk.Frame(self.content_area, padx=20, pady=20)
        self.frame_inputs.pack(fill="both", expand=True)

        tk.Label(self.frame_inputs, text="Farming Experience Instance Location:", font=("Segoe UI", 9, "bold")).pack(anchor="w")
        frame_src = tk.Frame(self.frame_inputs)
        frame_src.pack(fill="x", pady=(2, 0))
        
        self.src_var = tk.StringVar(value=initial_path)
        self.src_var.trace_add("write", self.validate_src)
        
        self.entry_src = ttk.Entry(frame_src, textvariable=self.src_var)
        self.entry_src.pack(side="left", fill="x", expand=True, padx=(0, 5))
        self.btn_src = ttk.Button(frame_src, text="Browse", width=8, command=self.browse_src)
        self.btn_src.pack(side="right")

        # Validation Label
        self.lbl_src_validation = tk.Label(self.frame_inputs, text="", font=("Segoe UI", 8))
        self.lbl_src_validation.pack(anchor="w", pady=(0, 10))

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
        link = tk.Label(eula_frame, text="Minecraft EULA", font=("Segoe UI", 8, "underline"), fg="#0066CC", bg=self.root.cget("bg"), cursor="hand2")
        link.pack(side="left")
        link.bind("<Button-1>", lambda e: webbrowser.open(config.EULA_URL))

        self.btn_install = tk.Button(bottom_frame, text="CREATE SERVER", bg="#4CAF50", fg="white", 
                                     font=("Segoe UI", 10, "bold"), relief="flat", cursor="hand2",
                                     padx=15, pady=5, command=self.start_installation)
        self.btn_install.pack(side="right")
        
        # Trigger initial validation
        self.validate_src()

    def validate_src(self, *args):
        path = self.src_var.get()
        if not path:
            self.lbl_src_validation.config(text="Please select a folder.", fg="#E65100")
            return False
        
        mods_path = os.path.join(path, "mods")
        if os.path.exists(mods_path) and os.path.isdir(mods_path):
            self.lbl_src_validation.config(text="Farming Experience is installed.", fg="#2E7D32")
            return True
        else:
            self.lbl_src_validation.config(text="Farming Experience is not installed.", fg="#C62828")
            return False

    def browse_src(self):
        f = filedialog.askdirectory(title="Select Farming Experience Instance")
        if f: self.src_var.set(f)

    def browse_dst(self):
        f = filedialog.askdirectory(title="Select Install Location")
        if f: self.dst_var.set(f)

    def update_status(self, text, percent=None, sub_text=""):
        self.lbl_status.config(text=text)
        self.lbl_sub_status.config(text=sub_text)
        if percent is not None:
            self.progress_var.set(percent)
        self.root.update_idletasks()

    def start_installation(self):
        if not self.validate_src():
            messagebox.showerror("Error", "Please select a valid Farming Experience instance folder containing a 'mods' directory.")
            return
        
        # Check Java using utils
        if not utils.check_java(self.log):
            return

        self.frame_inputs.pack_forget()
        
        # --- Progress Screen ---
        self.frame_progress = tk.Frame(self.content_area, padx=40)
        self.frame_progress.pack(fill="both", expand=True)
        
        self.progress_container = tk.Frame(self.frame_progress)
        self.progress_container.place(relx=0.5, rely=0.5, anchor="center", relwidth=1.0)

        self.lbl_status = tk.Label(self.progress_container, text="Preparing...", font=("Segoe UI", 11), fg="#333")
        self.lbl_status.pack(fill="x", pady=(0, 2))
        
        self.lbl_sub_status = tk.Label(self.progress_container, text="", font=("Segoe UI", 9), fg="#666")
        self.lbl_sub_status.pack(fill="x", pady=(0, 10))
        
        self.progress_var = tk.DoubleVar()
        self.progress_bar = ttk.Progressbar(self.progress_container, variable=self.progress_var, maximum=100)
        self.progress_bar.pack(fill="x")
        
        # Start Thread
        threading.Thread(
            target=installer.run_installation,
            args=(
                self.src_var.get(),
                self.dst_var.get(),
                self.shortcut_var.get(),
                self.update_status,
                self.log,
                self.on_install_success,
                self.on_install_failure
            ),
            daemon=True
        ).start()

    def on_install_success(self, public_ip, local_ip):
        # Schedule GUI updates on the main thread
        self.root.after(0, lambda: self._show_success_ui_main_thread(public_ip, local_ip))
        utils.flush_logs(self.dst_var.get(), self.log_buffer)

    def on_install_failure(self, error_message):
        self.root.after(0, lambda: self._show_failure_ui_main_thread(error_message))
        utils.flush_logs(self.dst_var.get(), self.log_buffer)

    def _show_failure_ui_main_thread(self, error_message):
        self.frame_progress.pack_forget()
        self.show_input_screen(self.src_var.get())
        messagebox.showerror("Error", f"Installation Failed.\nCheck the installer.log file for more details.\n\n{error_message}")

    def _show_success_ui_main_thread(self, public_ip, local_ip):
        self.frame_progress.pack_forget()
        
        self.frame_success = tk.Frame(self.content_area, padx=25, pady=20)
        self.frame_success.pack(fill="both", expand=True)

        tk.Label(self.frame_success, text="Installation Complete!", font=("Segoe UI", 16, "bold"), fg="#2E7D32").pack(pady=(0, 15))
        
        self.ip_container = tk.Frame(self.frame_success)
        self.ip_container.pack(fill="x", pady=5)
        
        ip_entry_style = {
            "font": ("Consolas", 13, "bold"), "bd": 1, "relief": "solid", 
            "bg": "#F5F5F5", "fg": "#333", "readonlybackground": "#F5F5F5", "highlightthickness": 0
        }

        # Public IP
        tk.Label(self.ip_container, text="Public IP (For friends online):", font=("Segoe UI", 10)).pack(anchor="w")
        pub_frame = tk.Frame(self.ip_container, pady=5)
        pub_frame.pack(fill="x", pady=(0, 15))
        
        self.public_ip_entry = tk.Entry(pub_frame, justify="center", **ip_entry_style)
        self.public_ip_entry.pack(side="left", fill="x", expand=True, padx=(0, 5), ipady=5)
        self.public_ip_entry.insert(0, public_ip)
        self.public_ip_entry.config(state="readonly")
        ttk.Button(pub_frame, text="Copy", width=8, command=lambda: self.copy_to_clipboard(self.public_ip_entry.get())).pack(side="right", fill="y")

        # Local IP
        tk.Label(self.ip_container, text="Local IP (For same WiFi/LAN):", font=("Segoe UI", 10)).pack(anchor="w")
        loc_frame = tk.Frame(self.ip_container, pady=5)
        loc_frame.pack(fill="x")
        
        self.local_ip_entry = tk.Entry(loc_frame, justify="center", **ip_entry_style)
        self.local_ip_entry.pack(side="left", fill="x", expand=True, padx=(0, 5), ipady=5)
        self.local_ip_entry.insert(0, local_ip)
        self.local_ip_entry.config(state="readonly")
        ttk.Button(loc_frame, text="Copy", width=8, command=lambda: self.copy_to_clipboard(self.local_ip_entry.get())).pack(side="right", fill="y")
        
        self.btn_finish_frame = tk.Frame(self.frame_success)
        self.btn_finish_frame.pack(pady=25, fill="x")

        # Donate Button
        btn_donate = tk.Button(self.btn_finish_frame, text="DONATE", bg="#4CAF50", fg="white", 
                               font=("Segoe UI", 10, "bold"), relief="flat", cursor="hand2",
                               padx=15, pady=5, command=self.open_donate)
        btn_donate.pack(anchor="center")

        if self.autorun_var.get():
            self.run_server_now()

    def copy_to_clipboard(self, text):
        self.root.clipboard_clear()
        self.root.clipboard_append(text)
        messagebox.showinfo("Copied", "IP Address copied!")

    def run_server_now(self):
        bat = os.path.join(self.dst_var.get(), "run.bat")
        if os.path.exists(bat):
            subprocess.Popen([bat], cwd=self.dst_var.get(), creationflags=subprocess.CREATE_NEW_CONSOLE)
        else:
            messagebox.showerror("Error", "run.bat not found.")
            
    def open_donate(self):
        webbrowser.open(config.DONATE_URL)