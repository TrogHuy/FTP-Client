package com;

import java.io.*;
import java.net.*;
import java.util.*;

public class FTPClient {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String currentDirectory;
    private boolean connected = false;

    private static class Reply {
        String code;
        String message;
        Reply(String code, String message) {
            this.code = code;
            this.message = message;
        }
    }

    public boolean isConnected() {
        return connected;
    }

    public String getCurrentDirectory() {
        return currentDirectory;
    }
    
    private Reply readReply() throws IOException {
        String firstLine = in.readLine();
        if (firstLine == null) throw new IOException("Connection lost");
        
        String code;
        StringBuilder fullMsg = new StringBuilder(firstLine);
        
        if(firstLine.length() >= 3)
        	code = firstLine.substring(0, 3);
        else
        	code = "000";
        
        // Multiline checking
        if (firstLine.length() > 3 && firstLine.charAt(3) == '-') {
            String line;
            while ((line = in.readLine()) != null) {
                fullMsg.append("\n").append(line);
                if (line.length() >= 4 && line.startsWith(code) && line.charAt(3) == ' ') {
                    break;
                }
            }
        }
       
        return new Reply(code, fullMsg.toString());
    }
    
    private void disconnect() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            // do nothing
        }
        connected = false;
    }

    public void connect(String host, int port) throws IOException {
        if (connected) disconnect();
        socket = new Socket(host, port);
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        
        Reply welcomeRep = readReply();
        if (!welcomeRep.code.equals("220")) {
            throw new IOException("Connection refused: " + welcomeRep.message);
        }
        connected = true;
        currentDirectory = "/";
    }

    private void sendCommand(String command) {
        out.println(command);
    }
    
    public void quit() throws IOException {
        if (!connected) return;
        sendCommand("QUIT");
        readReply(); // ignore response
        disconnect();
    }
    
    public void login(String user, String pass) throws IOException {
        sendCommand("USER " + user);
        Reply userReply = readReply();
        if (userReply.code.equals("331")) {
            sendCommand("PASS " + pass);
            Reply passReply = readReply();
            if (!passReply.code.equals("230")) {
                throw new IOException("Login failed: " + passReply.message);
            }
        } else if (!userReply.code.equals("230")) {
            throw new IOException("USER command failed: " + userReply.message);
        }
    }

    public String pwd() throws IOException {
        sendCommand("PWD");
        Reply reply = readReply();
        
        if (reply.code.equals("257")) {
            String msg = reply.message;
            int firstQuote = msg.indexOf('"');
            int lastQuote = msg.lastIndexOf('"');
            
            if (firstQuote != -1 && lastQuote > firstQuote) {
                currentDirectory = msg.substring(firstQuote + 1, lastQuote);
            } else {
                currentDirectory = "/";
            }
            
            return currentDirectory;
        } 
        else {
            throw new IOException("PWD failed: " + reply.message);
        }
    }

    public void cwd(String dir) throws IOException {
        sendCommand("CWD " + dir);
        Reply reply = readReply();
        if (reply.code.equals("250")) {
            pwd();
        } else {
            throw new IOException("CWD failed: " + reply.message);
        }
    }
    
    private String sendPasv() throws IOException {
        sendCommand("PASV");
        Reply reply = readReply();
        if (!reply.code.equals("227")) {
            throw new IOException("PASV failed: " + reply.message);
        }
        return reply.message;
    }

    private String[] translatePasvRep(String response) {
        int start = response.indexOf('(');
        int end = response.indexOf(')');
        if (start == -1 || end == -1) throw new RuntimeException("Invalid PASV response");
        String[] parts = response.substring(start + 1, end).split(",");
        String host = parts[0] + "." + parts[1] + "." + parts[2] + "." + parts[3];
        int port = Integer.parseInt(parts[4]) * 256 + Integer.parseInt(parts[5]);
        return new String[]{host, String.valueOf(port)};
    }

    public List<String> listFiles() throws IOException {
        String pasvResponse = sendPasv();
        String[] hostPort = translatePasvRep(pasvResponse);
        String dataHost = hostPort[0];
        int dataPort = Integer.parseInt(hostPort[1]);

        try (Socket dataSocket = new Socket(dataHost, dataPort)) {
            sendCommand("LIST");
            Reply listReply = readReply();
            if (!listReply.code.equals("150") && !listReply.code.equals("125")) {
                throw new IOException("LIST not accepted: " + listReply.message);
            }

            // Read data from data socket
            BufferedReader dataIn = new BufferedReader(new InputStreamReader(dataSocket.getInputStream()));
            List<String> fileList = new ArrayList<>();
            String line;
            while ((line = dataIn.readLine()) != null) {
                // get last token after final space (example response: -rwxr-xr-x 1 owner group 4096 Jan 01 12:00 filename.txt)
                line = line.trim();
                if (line.isEmpty()) continue;
                int lastSpace = line.lastIndexOf(' ');
                String name;
                if(lastSpace > 0) 
                	name = line.substring(lastSpace + 1);
                else
                	name = line;
                
                fileList.add(name);
            }
            dataIn.close();

            Reply completionReply = readReply();
            if (!completionReply.code.equals("226")) {
                throw new IOException("LIST transfer failed: " + completionReply.message);
            }
            return fileList;
        }
    }

    public void download(String remoteFile, String localPath) throws IOException {
        // Set binary mode
        sendCommand("TYPE I");
        readReply(); // 200

        // Passive mode
        String pasvResponse = sendPasv();
        String[] hostPort = translatePasvRep(pasvResponse);
        String dataHost = hostPort[0];
        int dataPort = Integer.parseInt(hostPort[1]);

        try (Socket dataSocket = new Socket(dataHost, dataPort);
             OutputStream dataOut = dataSocket.getOutputStream();
             FileOutputStream fos = new FileOutputStream(localPath)) {
            
            sendCommand("RETR " + remoteFile);
            Reply retrReply = readReply();
            if (!retrReply.code.equals("150") && !retrReply.code.equals("125")) {
                throw new IOException("RETR failed: " + retrReply.message);
            }

            // Copy data from data socket to local file
            byte[] buffer = new byte[4096];
            
            InputStream dataIn = dataSocket.getInputStream();
            
            while (dataIn.read(buffer) != -1) 
                fos.write(buffer, 0, dataIn.read(buffer));
            

            Reply transferReply = readReply();
            if (!transferReply.code.equals("226")) {
                throw new IOException("Download transfer failed: " + transferReply.message);
            }
        }
    }

    public void upload(String localFile, String remoteName) throws IOException {
        File file = new File(localFile);
        if (!file.exists() || !file.isFile()) {
            throw new IOException("Local file not found: " + localFile);
        }

        // Binary mode
        sendCommand("TYPE I");
        readReply(); // 200

        // Passive mode
        String pasvResponse = sendPasv();
        String[] hostPort = translatePasvRep(pasvResponse);
        String dataHost = hostPort[0];
        int dataPort = Integer.parseInt(hostPort[1]);

        try (Socket dataSocket = new Socket(dataHost, dataPort);
             InputStream dataInFromSocket = dataSocket.getInputStream();
             OutputStream dataOut = dataSocket.getOutputStream();
             FileInputStream fis = new FileInputStream(file)) {
            
            sendCommand("STOR " + remoteName);
            Reply storReply = readReply();
            if (!storReply.code.equals("150") && !storReply.code.equals("125")) {
                throw new IOException("STOR failed: " + storReply.message);
            }

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                dataOut.write(buffer, 0, bytesRead);
            }
            dataOut.flush();
            dataSocket.shutdownOutput();


            Reply transferReply = readReply();
            if (!transferReply.code.equals("226")) {
                throw new IOException("Upload transfer failed: " + transferReply.message);
            }
        }
    }

    public void delete(String filename) throws IOException {
        sendCommand("DELE " + filename);
        Reply reply = readReply();
        if (!reply.code.equals("250")) {
            throw new IOException("DELE failed: " + reply.message);
        }
    }

    public void mkdir(String dirname) throws IOException {
        sendCommand("MKD " + dirname);
        Reply reply = readReply();
        if (!reply.code.equals("257")) {
            throw new IOException("MKD failed: " + reply.message);
        }
    }

    public void rmdir(String dirname) throws IOException {
        sendCommand("RMD " + dirname);
        Reply reply = readReply();
        if (!reply.code.equals("250")) {
            throw new IOException("RMD failed: " + reply.message);
        }
    }
}