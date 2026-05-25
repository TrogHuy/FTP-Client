# FTP-Client
A simple FTP Client built by Java using socket communication based on FTP (RFC 959). This project includes command-line core logic and a Swing GUI.

---

## Requirements 
# JDK 11+
No third-party libraries are required. The GUI uses Java Swing, which is included in the JDK.

---

## Build
 
Open **Command Prompt** in the project root directory (the folder containing `com\`).
 
```cmd
javac com\FTPClient.java com\FTPClientGUI.java
```
 
Compiled `.class` files will be placed inside the `com\` directory.

---

## Run
 
```cmd
java com.FTPClientGUI
```
 
The GUI window will open. No arguments are required.

---

## Usage
 
1. **Connect** — Enter the server hostname (default: `ftp.gnu.org`), port (default: `21`), and credentials. Tick **Anonymous** for anonymous login. Click **Connect**.
2. **Browse** — The left panel shows your local file system; the right panel shows the remote directory. Double-click a folder to navigate into it. Click **Up** to go to the parent directory.
3. **Upload** — Select a local file, then click **Upload Selected to Remote**.
4. **Download** — Select a remote file, then click **Download Selected** and choose a save location.
5. **Delete** — Select a remote file and click **Delete Selected**.
6. **Make Dir / Remove Dir** — Use the **Make Dir** and **Remove Dir** buttons to manage remote directories.
7. **Disconnect** — Click **Disconnect** when done.
Status messages and server replies are logged in the panel at the bottom of the window.

---
 
---
