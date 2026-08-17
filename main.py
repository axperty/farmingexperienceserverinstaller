import argparse
import tkinter as tk
from gui import ServerInstallerApp
from updater import UpdateApp

def main():
    parser = argparse.ArgumentParser(description="Farming Experience Server Installer & Updater")
    parser.add_argument("--update", nargs=2, metavar=('SRC', 'DST'), help="Run the updater with source and destination paths")
    args = parser.parse_args()

    root = tk.Tk()
    if args.update:
        src_path, dst_path = args.update
        app = UpdateApp(root, src_path, dst_path)
    else:
        app = ServerInstallerApp(root)
        
    root.mainloop()

if __name__ == "__main__":
    main()