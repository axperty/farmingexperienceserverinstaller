import os
import shutil
import subprocess
import requests
import traceback
import sys

import config
import utils

NO_WINDOW = subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0

def copy_instance_files(src_path, dst_path, update_status, log_func):
    update_status("Copying Mods...", 0)
    src_mods = os.path.join(src_path, "mods")
    dst_mods = os.path.join(dst_path, "mods")

    if os.path.exists(dst_mods):
        shutil.rmtree(dst_mods)
    os.makedirs(dst_mods)

    def is_excluded_folder(name):
        return any(folder.lower() in name.lower() for folder in config.EXCLUDED_FOLDERS)

    def is_excluded_mod(name):
        return any(p.replace('*', '').lower() in name.lower() for p in config.EXCLUDED_MODS)

    all_files = os.listdir(src_mods)
    total_files = len(all_files)

    log_func(f"--- STARTING MOD COPY ({total_files} items) ---")

    for i, item in enumerate(all_files):
        full_src = os.path.join(src_mods, item)
        full_dst = os.path.join(dst_mods, item)

        if os.path.isdir(full_src):
            if is_excluded_folder(item):
                log_func(f"[SKIP] Directory excluded: {item}")
                continue
            log_func(f"[COPY] Directory: {item}")
            shutil.copytree(full_src, full_dst)
        else:
            if is_excluded_mod(item):
                log_func(f"[SKIP] File excluded: {item}")
                continue
            log_func(f"[COPY] File: {item}")
            shutil.copy2(full_src, full_dst)

        pct = (i / total_files) * 40
        if i % 5 == 0:
            update_status(f"Copying mods ({int((i/total_files)*100)}%)...", pct)

    log_func("--- MOD COPY FINISHED ---")

    update_status("Copying Configs...", 40)
    src_cfg = os.path.join(src_path, "config")
    dst_cfg = os.path.join(dst_path, "config")

    if os.path.exists(src_cfg):
        if os.path.exists(dst_cfg):
            shutil.rmtree(dst_cfg)
        shutil.copytree(src_cfg, dst_cfg)
        log_func("Configs copied.")

def setup_updater_shortcuts(src_path, dst_path, icon_path, log_func):
    installer_dir = os.path.join(dst_path, "serverinstaller")
    if not os.path.exists(installer_dir):
        os.makedirs(installer_dir)
    
    is_compiled_exe = getattr(sys, 'frozen', False)
    
    if is_compiled_exe:
        old_updater = os.path.join(installer_dir, "updater.exe")
        if os.path.exists(old_updater):
            try:
                os.remove(old_updater)
            except Exception:
                pass

        original_name = os.path.basename(sys.executable)
        dest_exe = os.path.join(installer_dir, original_name)
        shutil.copy2(sys.executable, dest_exe)
        log_func(f"Copied updater executable to {dest_exe}")
        
        shortcut_path = os.path.join(dst_path, "Update Server.lnk")
        args = f'--update "{src_path}" "{dst_path}"'
        create_shortcut(shortcut_path, dest_exe, icon_path, log_func, args=args, working_dir=dst_path)
    else:
        log_func("Run this program as an executable to update server.")
        
    # Create Wiki shortcut
    wiki_path = os.path.join(dst_path, "Wiki.url")
    create_url_shortcut(wiki_path, "https://axperty.github.io/farmingexperience", icon_path, log_func)


def run_installation(src_path, dst_path, create_shortcut_flag, update_status, log_func, on_success, on_failure):
    try:
        if not os.path.exists(dst_path):
            os.makedirs(dst_path)

        copy_instance_files(src_path, dst_path, update_status, log_func)

        update_status("Downloading Server Icon...", 50)
        png_path = os.path.join(dst_path, "server-icon.png")
        ico_path = os.path.join(dst_path, "server-icon.ico")
        try:
            download_file(config.ICON_URL, png_path, log_func)
            shutil.copy2(utils.resource_path("app.ico"), ico_path)
        except Exception as e:
            log_func(f"Icon processing failed: {e}", error=True)

        update_status("Configuring Server...", 55)
        props_file = os.path.join(dst_path, "server.properties")
        with open(props_file, "w") as f:
            f.write("#Minecraft Server Properties\n")
            for k, v in config.SERVER_PROPS.items():
                f.write(f"{k}={v}\n")
        log_func("server.properties generated.")

        update_status("Downloading NeoForge...", 60)
        installer_jar = os.path.join(dst_path, "installer.jar")
        download_file(config.NEOFORGE_INSTALLER_URL, installer_jar, log_func)

        update_status("Installing NeoForge Server Loader...", 75, sub_text="This might take a few seconds")
        log_func("Running NeoForge installer...")

        result = subprocess.run(
            ["java", "-jar", "installer.jar", "--installServer"],
            cwd=dst_path,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            creationflags=NO_WINDOW
        )
        if result.stdout:
            for line in result.stdout.splitlines():
                log_func(f"[NeoForge] {line}")
        if result.returncode != 0:
            raise subprocess.CalledProcessError(result.returncode, result.args, output=result.stdout)

        for path in [installer_jar, os.path.join(dst_path, "installer.jar.log")]:
            if os.path.exists(path):
                os.remove(path)

        run_bat_path = os.path.join(dst_path, "run.bat")
        if os.path.exists(run_bat_path):
            try:
                with open(run_bat_path, "r") as f:
                    lines = f.read().splitlines()

                new_lines = [
                    line.replace("java ", 'start "" javaw ') if line.strip().startswith("java ")
                    else line
                    for line in lines
                    if line.strip().lower() != "pause"
                ]

                with open(run_bat_path, "w") as f:
                    f.write("\n".join(new_lines) + "\n")
                log_func("run.bat patched for javaw.")
            except Exception as e:
                log_func(f"Failed to patch run.bat: {e}", error=True)

        update_status("Setting up Updater...", 85)
        setup_updater_shortcuts(src_path, dst_path, ico_path, log_func)

        with open(os.path.join(dst_path, "eula.txt"), "w") as f:
            f.write("eula=true\n")

        if create_shortcut_flag:
            update_status("Creating Shortcut...", 90, sub_text="")
            desktop = os.path.join(os.environ['USERPROFILE'], 'Desktop')
            desktop_shortcut = os.path.join(desktop, "Start Farming Experience Server.lnk")
            create_shortcut(desktop_shortcut, os.path.join(dst_path, "run.bat"), ico_path, log_func, working_dir=dst_path)

        update_status("Fetching IP...", 95)
        public_ip = utils.get_public_ip(log_func)
        local_ip = utils.get_local_ip(log_func)

        update_status("Complete!", 100)
        on_success(public_ip, local_ip)

    except Exception as e:
        trace = traceback.format_exc()
        log_func(f"CRASH: {trace}", error=True)
        on_failure(str(e))

def download_file(url, filepath, log_func):
    log_func(f"Downloading {url}")
    try:
        response = requests.get(url, headers=config.HEADERS, stream=True)
        response.raise_for_status()
        with open(filepath, 'wb') as f:
            for chunk in response.iter_content(chunk_size=8192):
                f.write(chunk)
    except Exception as e:
        log_func(f"Download error: {e}", error=True)
        raise

# Create shortcuts using standard built-in Windows VBScript
# This avoids adding heavy external dependencies like pywin32 to our executable
def create_shortcut(shortcut_path, target_path, icon_path, log_func, args="", working_dir=""):
    try:
        if not working_dir:
            working_dir = os.path.dirname(target_path)

        vbs_args = args.replace('"', '""')
            
        vbs_script = f"""
        Set oWS = WScript.CreateObject("WScript.Shell")
        Set oLink = oWS.CreateShortcut("{shortcut_path}")
        oLink.TargetPath = "{target_path}"
        oLink.Arguments = "{vbs_args}"
        oLink.WorkingDirectory = "{working_dir}"
        oLink.IconLocation = "{icon_path}"
        oLink.Save
        """
        vbs_file = os.path.join(os.path.dirname(shortcut_path), "create_shortcut.vbs")
        with open(vbs_file, "w") as f:
            f.write(vbs_script)
            
        result = subprocess.run(
            ["cscript", "//Nologo", vbs_file],
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            creationflags=NO_WINDOW
        )
        if result.stdout:
            for line in result.stdout.splitlines():
                log_func(f"[VBScript] {line}")
        if result.returncode != 0:
            raise subprocess.CalledProcessError(result.returncode, result.args, output=result.stdout)
        
        os.remove(vbs_file)
    except Exception as e:
        log_func(f"Shortcut failed: {e}", error=True)

def create_url_shortcut(shortcut_path, url, icon_path, log_func):
    try:
        content = f"[InternetShortcut]\nURL={url}\nIconFile={icon_path}\nIconIndex=0\n"
        with open(shortcut_path, "w") as f:
            f.write(content)
        log_func(f"URL Shortcut created: {shortcut_path}")
    except Exception as e:
        log_func(f"URL Shortcut failed: {e}", error=True)