import os
import shutil
import subprocess
import requests
import traceback
import fnmatch
import struct
import sys

# Local imports
import config
import utils

def run_installation(src_path, dst_path, create_shortcut_flag, update_status, log_func, on_success, on_failure):
    """
    Main orchestration function running in a separate thread.
    """
    try:
        # Prepare Destination
        if not os.path.exists(dst_path):
            os.makedirs(dst_path)

        # Copy Mods
        update_status("Copying Mods...", 0)
        src_mods = os.path.join(src_path, "mods")
        dst_mods = os.path.join(dst_path, "mods")

        if os.path.exists(dst_mods):
            shutil.rmtree(dst_mods)
        os.makedirs(dst_mods)

        all_files = os.listdir(src_mods)
        total_files = len(all_files)

        log_func(f"--- STARTING MOD COPY ({total_files} items) ---")

        for i, item in enumerate(all_files):
            full_src = os.path.join(src_mods, item)
            full_dst = os.path.join(dst_mods, item)

            # Check exclusions
            if os.path.isdir(full_src):
                if item in config.EXCLUDED_FOLDERS:
                    log_func(f"[SKIP] Directory excluded: {item}")
                    continue
                log_func(f"[COPY] Directory: {item}")
                shutil.copytree(full_src, full_dst)
            else:
                is_excluded = False
                for pattern in config.EXCLUDED_MODS:
                    if fnmatch.fnmatch(item.lower(), pattern.lower()):
                        is_excluded = True
                        break

                if is_excluded:
                    log_func(f"[SKIP] File excluded: {item}")
                    continue

                log_func(f"[COPY] File: {item}")
                shutil.copy2(full_src, full_dst)

            # Update Progress Bar (0% to 40%)
            pct = (i / total_files) * 40
            if i % 5 == 0:
                update_status(f"Copying mods ({int((i/total_files)*100)}%)...", pct)

        log_func("--- MOD COPY FINISHED ---")

        # Copy Configs
        update_status("Copying Configs...", 40)
        src_cfg = os.path.join(src_path, "config")
        dst_cfg = os.path.join(dst_path, "config")

        if os.path.exists(src_cfg):
            if os.path.exists(dst_cfg):
                shutil.rmtree(dst_cfg)
            shutil.copytree(src_cfg, dst_cfg)
            log_func("Configs copied.")

        # Download & Convert Icon
        update_status("Downloading Server Icon...", 50)
        png_path = os.path.join(dst_path, "server-icon.png")
        ico_path = os.path.join(dst_path, "server-icon.ico")
        try:
            download_file(config.ICON_URL, png_path, log_func)
            png_to_ico(png_path, ico_path, log_func)
        except Exception as e:
            log_func(f"Icon processing failed: {e}", error=True)

        # Generate server.properties
        update_status("Configuring Server...", 55)
        props_file = os.path.join(dst_path, "server.properties")
        with open(props_file, "w") as f:
            f.write("#Minecraft Server Properties\n")
            for k, v in config.SERVER_PROPS.items():
                f.write(f"{k}={v}\n")
        log_func("server.properties generated.")

        # Download NeoForge Installer
        update_status("Downloading NeoForge...", 60)
        installer_jar = os.path.join(dst_path, "installer.jar")
        download_file(config.NEOFORGE_INSTALLER_URL, installer_jar, log_func)

        # Install Server Loader
        update_status("Installing Server Loader...", 75, sub_text="(This might take a few minutes...)")
        log_func("Running NeoForge installer...")

        # Determine creation flags to hide console window on Windows
        creation_flags = subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0

        subprocess.run(
            ["java", "-jar", "installer.jar", "--installServer"],
            cwd=dst_path,
            check=True,
            creationflags=creation_flags
        )

        # Cleanup
        if os.path.exists(installer_jar):
            os.remove(installer_jar)
        if os.path.exists(os.path.join(dst_path, "installer.jar.log")):
            os.remove(os.path.join(dst_path, "installer.jar.log"))

        # Patch run.bat for --nogui
        run_bat_path = os.path.join(dst_path, "run.bat")
        if os.path.exists(run_bat_path):
            try:
                with open(run_bat_path, "r") as f:
                    content = f.read()

                # Check if we need to patch
                if "%*" in content:
                    content = content.replace("%*", "--nogui %*")
                else:
                    content += " --nogui"

                with open(run_bat_path, "w") as f:
                    f.write(content)
                log_func("run.bat patched for nogui.")
            except Exception as e:
                log_func(f"Failed to patch run.bat: {e}", error=True)

        # Agree to EULA
        with open(os.path.join(dst_path, "eula.txt"), "w") as f:
            f.write("eula=true\n")

        # Create Shortcut
        if create_shortcut_flag:
            update_status("Creating Shortcut...", 90, sub_text="")
            create_shortcut(os.path.join(dst_path, "run.bat"), ico_path, log_func)

        # Fetch IPs
        update_status("Fetching IP...", 95)
        public_ip = utils.get_public_ip(log_func)
        local_ip = utils.get_local_ip(log_func)

        # Complete
        update_status("Complete!", 100)
        on_success(public_ip, local_ip)

    except Exception as e:
        trace = traceback.format_exc()
        log_func(f"CRASH: {trace}", error=True)
        on_failure(str(e))

def download_file(url, filepath, log_func):
    """Helper to download a file with stream."""
    log_func(f"Downloading {url}")
    try:
        response = requests.get(url, headers=config.HEADERS, stream=True)
        response.raise_for_status()
        with open(filepath, 'wb') as f:
            for chunk in response.iter_content(chunk_size=8192):
                f.write(chunk)
    except Exception as e:
        log_func(f"Download error: {e}", error=True)
        raise e

def png_to_ico(png_path, ico_path, log_func):
    """Helper to convert PNG to ICO manually."""
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
        log_func(f"Failed to convert PNG to ICO: {e}", error=True)
        return False

def create_shortcut(target_bat, icon_path, log_func):
    """Helper to create Windows Shortcut via VBScript."""
    try:
        desktop = os.path.join(os.environ['USERPROFILE'], 'Desktop')
        shortcut_path = os.path.join(desktop, "Start Farming Experience Server.lnk")
        
        vbs_script = f"""
        Set oWS = WScript.CreateObject("WScript.Shell")
        Set oLink = oWS.CreateShortcut("{shortcut_path}")
        oLink.TargetPath = "{target_bat}"
        oLink.WorkingDirectory = "{os.path.dirname(target_bat)}"
        oLink.IconLocation = "{icon_path}"
        oLink.Save
        """
        vbs_file = os.path.join(os.path.dirname(target_bat), "create_shortcut.vbs")
        with open(vbs_file, "w") as f:
            f.write(vbs_script)
            
        creation_flags = subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0
        subprocess.run(["cscript", "//Nologo", vbs_file], check=True, creationflags=creation_flags)
        
        os.remove(vbs_file)
    except Exception as e:
        log_func(f"Shortcut failed: {e}", error=True)