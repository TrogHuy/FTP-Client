package com;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.List;

public class FTPClientGUI extends JFrame {
    private FTPClient ftp;
    private JTextField hostField, portField, userField, passField;
    private JCheckBox anonymousCheck;
    private JButton connectBtn, disconnectBtn;
    private JTextArea statusArea;
    
    // Local file browser
    private JList<String> localList;
    private DefaultListModel<String> localListModel;
    private File currentLocalDir;
    private JLabel localPathLabel;
    
    // Remote file browser
    private JList<String> remoteList;
    private DefaultListModel<String> remoteListModel;
    private JLabel remotePathLabel;
    
    private JButton upLocalBtn, uploadBtn, downloadBtn, deleteBtn, mkdirBtn, rmdirBtn, refreshRemoteBtn;

    public FTPClientGUI() {
        ftp = new FTPClient();
        initUI();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 650);
        setLocationRelativeTo(null);
        setTitle("FTP Client - My Project");
    }

    private void initUI() {
        setLayout(new BorderLayout(5, 5));
        
        // Top connection panel
        JPanel connectionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        connectionPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        
        hostField = new JTextField("ftp.gnu.org", 12);
        portField = new JTextField("21", 4);
        userField = new JTextField("anonymous", 10);
        passField = new JPasswordField("", 10);
        anonymousCheck = new JCheckBox("Anonymous");
        connectBtn = new JButton("Connect");
        disconnectBtn = new JButton("Disconnect");
        disconnectBtn.setEnabled(false);
        
        connectionPanel.add(new JLabel("Host:"));
        connectionPanel.add(hostField);
        connectionPanel.add(new JLabel("Port:"));
        connectionPanel.add(portField);
        connectionPanel.add(new JLabel("User:"));
        connectionPanel.add(userField);
        connectionPanel.add(new JLabel("Pass:"));
        connectionPanel.add(passField);
        connectionPanel.add(anonymousCheck);
        connectionPanel.add(connectBtn);
        connectionPanel.add(disconnectBtn);
        
        add(connectionPanel, BorderLayout.NORTH);
        
        // Center split pane: Local (left) | Remote (right)
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.5);
        splitPane.setDividerLocation(450);
        
        // Left: Local files
        JPanel localPanel = new JPanel(new BorderLayout());
        localPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        localPathLabel = new JLabel("Local: ");
        localPathLabel.setFont(new Font("Monospaced", Font.PLAIN, 12));
        localListModel = new DefaultListModel<>();
        localList = new JList<>(localListModel);
        localList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        localList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int index = localList.locationToIndex(e.getPoint());
                    if (index >= 0) {
                        String selected = localListModel.get(index);
                        File selectedFile = new File(currentLocalDir, selected);
                        if (selectedFile.isDirectory()) {
                            browseLocal(selectedFile);
                        }
                    }
                }
            }
        });
        
        upLocalBtn = new JButton("Up");
        upLocalBtn.addActionListener(e -> {
            if (currentLocalDir.getParentFile() != null) {
                browseLocal(currentLocalDir.getParentFile());
            }
        });
        uploadBtn = new JButton("Upload Selected to Remote");
        uploadBtn.addActionListener(e -> uploadSelected());
        
        JPanel localButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        localButtonPanel.add(upLocalBtn);
        localButtonPanel.add(uploadBtn);
        
        localPanel.add(localPathLabel, BorderLayout.NORTH);
        localPanel.add(new JScrollPane(localList), BorderLayout.CENTER);
        localPanel.add(localButtonPanel, BorderLayout.SOUTH);
        
        // Right: Remote files
        JPanel remotePanel = new JPanel(new BorderLayout());
        remotePanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        remotePathLabel = new JLabel("Remote: ");
        remotePathLabel.setFont(new Font("Monospaced", Font.PLAIN, 12));
        remoteListModel = new DefaultListModel<>();
        remoteList = new JList<>(remoteListModel);
        remoteList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        remoteList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int index = remoteList.locationToIndex(e.getPoint());
                    if (index >= 0) {
                        String selected = remoteListModel.get(index);
                        // Heuristic: if it ends with "/" or looks like dir (FTP LIST may not show /)
                        // We'll try to CWD; if fails, ignore.
                        cwdRemote(selected);
                    }
                }
            }
        });
        
        downloadBtn = new JButton("Download Selected");
        deleteBtn = new JButton("Delete Selected");
        mkdirBtn = new JButton("Make Dir");
        rmdirBtn = new JButton("Remove Dir");
        refreshRemoteBtn = new JButton("Refresh");
        
        downloadBtn.addActionListener(e -> downloadSelected());
        deleteBtn.addActionListener(e -> deleteSelected());
        mkdirBtn.addActionListener(e -> mkdirRemote());
        rmdirBtn.addActionListener(e -> rmdirRemote());
        refreshRemoteBtn.addActionListener(e -> refreshRemote());
        
        JPanel remoteButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        remoteButtonPanel.add(downloadBtn);
        remoteButtonPanel.add(deleteBtn);
        remoteButtonPanel.add(mkdirBtn);
        remoteButtonPanel.add(rmdirBtn);
        remoteButtonPanel.add(refreshRemoteBtn);
        
        remotePanel.add(remotePathLabel, BorderLayout.NORTH);
        remotePanel.add(new JScrollPane(remoteList), BorderLayout.CENTER);
        remotePanel.add(remoteButtonPanel, BorderLayout.SOUTH);
        
        splitPane.setLeftComponent(localPanel);
        splitPane.setRightComponent(remotePanel);
        add(splitPane, BorderLayout.CENTER);
        
        // Bottom status area
        statusArea = new JTextArea(8, 60);
        statusArea.setEditable(false);
        statusArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        JScrollPane statusScroll = new JScrollPane(statusArea);
        statusScroll.setBorder(new EmptyBorder(5, 5, 5, 5));
        add(statusScroll, BorderLayout.SOUTH);
        
        // Connect/Disconnect actions
        connectBtn.addActionListener(e -> doConnect());
        disconnectBtn.addActionListener(e -> doDisconnect());
        anonymousCheck.addActionListener(e -> {
            boolean anon = anonymousCheck.isSelected();
            userField.setEnabled(!anon);
            passField.setEnabled(!anon);
            if (anon) {
                userField.setText("anonymous");
                passField.setText("");
            }
        });
        
        // Initial local browse
        browseLocal(new File(System.getProperty("user.home")));
    }
    
    private void appendStatus(String text) {
        SwingUtilities.invokeLater(() -> {
            statusArea.append(text + "\n");
            statusArea.setCaretPosition(statusArea.getDocument().getLength());
        });
    }
    
    private void browseLocal(File dir) {
        if (!dir.isDirectory()) return;
        currentLocalDir = dir;
        localPathLabel.setText("Local: " + dir.getAbsolutePath());
        localListModel.clear();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    localListModel.addElement(f.getName() + "/");
                } else {
                    localListModel.addElement(f.getName());
                }
            }
        }
    }
    
    private void doConnect() {
        String host = hostField.getText().trim();
        int port = Integer.parseInt(portField.getText().trim());
        String userRaw = userField.getText().trim();
        String passRaw = passField.getText().trim();
        boolean anon = anonymousCheck.isSelected();
        String user, pass;
        
        if (anon) {
            user = "anonymous";
            pass = "guest@";
        }
        else {
        	user = userRaw;
        	pass = passRaw;
        }
        
        appendStatus(">>> Connecting to " + host + ":" + port);
        new Thread(new Runnable() {
            public void run() {
                try {
                    ftp.connect(host, port);
                    appendStatus("<<< Connected");
                    ftp.login(user, pass);
                    appendStatus("<<< Login successful");
                    String pwd = ftp.pwd();
                    appendStatus("<<< PWD: " + pwd);
                    SwingUtilities.invokeLater(() -> {
                        connectBtn.setEnabled(false);
                        disconnectBtn.setEnabled(true);
                        refreshRemote();
                    });
                } catch (Exception e) {
                    appendStatus("!!! Error: " + e.getMessage());
                }
            }
        }).start();
    }
    
    private void doDisconnect() {
        appendStatus(">>> Disconnecting...");
        new Thread(new Runnable() {
            public void run() {
                try {
                    ftp.quit();
                    appendStatus("<<< Disconnected");
                } catch (Exception e) {
                    appendStatus("!!! Error during disconnect: " + e.getMessage());
                } finally {
                    SwingUtilities.invokeLater(() -> {
                        connectBtn.setEnabled(true);
                        disconnectBtn.setEnabled(false);
                        remoteListModel.clear();
                        remotePathLabel.setText("Remote: ");
                    });
                }
            }
        }).start();
    }
    
    private void refreshRemote() {
        if (!ftp.isConnected()) {
            appendStatus("Not connected");
            return;
        }
        appendStatus(">>> Refreshing remote listing...");
        new Thread(new Runnable() {
            public void run() {
                try {
                    String pwd = ftp.pwd();
                    List<String> files = ftp.listFiles();
                    SwingUtilities.invokeLater(() -> {
                        remotePathLabel.setText("Remote: " + pwd);
                        remoteListModel.clear();
                        for (String f : files) {
                            remoteListModel.addElement(f);
                        }
                        appendStatus("<<< Listing complete, " + files.size() + " items");
                    });
                } catch (Exception e) {
                    appendStatus("!!! Refresh failed: " + e.getMessage());
                }
            }
        }).start();
    }
    
    private void cwdRemote(String dir) {
        if (!ftp.isConnected()) return;
        appendStatus(">>> CWD " + dir);
        new Thread(new Runnable() {
            public void run() {
                try {
                    ftp.cwd(dir);
                    appendStatus("<<< CWD success");
                    refreshRemote();
                } catch (Exception e) {
                    appendStatus("!!! CWD failed: " + e.getMessage());
                }
            }
        }).start();
    }
    
    private void downloadSelected() {
        String selected = remoteList.getSelectedValue();
        if (selected == null) {
            appendStatus("No remote file selected");
            return;
        }
        JFileChooser chooser = new JFileChooser(currentLocalDir);
        chooser.setSelectedFile(new File(selected));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        String localPath = chooser.getSelectedFile().getAbsolutePath();
        appendStatus(">>> Downloading " + selected + " -> " + localPath);
        new Thread(new Runnable() {
            public void run() {
                try {
                    ftp.download(selected, localPath);
                    appendStatus("<<< Download complete");
                    SwingUtilities.invokeLater(() -> browseLocal(currentLocalDir));
                } catch (Exception e) {
                    appendStatus("!!! Download failed: " + e.getMessage());
                }
            }
        }).start();
    }
    
    private void uploadSelected() {
        String selected = localList.getSelectedValue();
        if (selected == null) {
            appendStatus("No local file selected");
            return;
        }
        File localFile = new File(currentLocalDir, selected);
        if (!localFile.isFile()) {
            appendStatus("Selected item is not a regular file");
            return;
        }
        String remoteName = JOptionPane.showInputDialog(this, "Remote filename:", localFile.getName());
        if (remoteName == null || remoteName.trim().isEmpty()) return;
        appendStatus(">>> Uploading " + localFile.getAbsolutePath() + " -> " + remoteName);
        new Thread(new Runnable() {
            public void run() {
                try {
                    ftp.upload(localFile.getAbsolutePath(), remoteName);
                    appendStatus("<<< Upload complete");
                    refreshRemote();
                } catch (Exception e) {
                    appendStatus("!!! Upload failed: " + e.getMessage());
                }
            }
        }).start();
    }
    
    private void deleteSelected() {
        String selected = remoteList.getSelectedValue();
        if (selected == null) {
            appendStatus("No remote file selected");
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this, "Delete " + selected + "?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        appendStatus(">>> Deleting " + selected);
        new Thread(new Runnable() {
            public void run() {
                try {
                    ftp.delete(selected);
                    appendStatus("<<< Deleted");
                    refreshRemote();
                } catch (Exception e) {
                    appendStatus("!!! Delete failed: " + e.getMessage());
                }
            }
        }).start();
    }
    
    private void mkdirRemote() {
        String name = JOptionPane.showInputDialog(this, "New directory name:");
        if (name == null || name.trim().isEmpty()) return;
        appendStatus(">>> MKD " + name);
        new Thread(new Runnable() {
            public void run() {
                try {
                    ftp.mkdir(name);
                    appendStatus("<<< Directory created");
                    refreshRemote();
                } catch (Exception e) {
                    appendStatus("!!! MKD failed: " + e.getMessage());
                }
            }
        }).start();
    }
    
    private void rmdirRemote() {
        String selected = remoteList.getSelectedValue();
        if (selected == null) {
            appendStatus("No remote directory selected");
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this, "Remove directory " + selected + "?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        appendStatus(">>> RMD " + selected);
        new Thread(new Runnable() {
            public void run() {
                try {
                    ftp.rmdir(selected);
                    appendStatus("<<< Directory removed");
                    refreshRemote();
                } catch (Exception e) {
                    appendStatus("!!! RMD failed: " + e.getMessage());
                }
            }
        }).start();
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new FTPClientGUI().setVisible(true));
    }
}