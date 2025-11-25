import tkinter as tk
from gui import ServerInstallerApp

def main():
    root = tk.Tk()
    app = ServerInstallerApp(root)
    root.mainloop()

if __name__ == "__main__":
    main()