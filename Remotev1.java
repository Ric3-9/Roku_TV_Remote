import javax.swing.*;
import java.awt.event.*;
import java.net.*;

class Remotev1 {
    private static String rokuIp = null;

    enum Controls {
        UP("UP"), DOWN("Down"), LEFT("Left"), RIGHT("Right"),
        SELECT("Select"), ON("PowerOn"), OFF("PowerOff"),
        VOLUME_UP("VolumeUp"), VOLUME_DOWN("VolumeDown"), VOLUME_MUTE("VolumeMute"),
        HOME("Home"), BACK("Back");

        private final String endpoint;
        Controls(String endpoint) { this.endpoint = endpoint; }
        public String getUrl() {
            return "http://" + rokuIp + ":8060/keypress/" + this.endpoint;
        }
    }

    public static void main(String[] args) {
        discoverRoku();

        SwingUtilities.invokeLater(() -> {
            final JFrame frame = new JFrame("Remote Control");
            frame.setUndecorated(true);
            frame.setSize(1, 1);
            frame.setLocationRelativeTo(null);

            frame.addKeyListener(new KeyAdapter() {
        

                public void keyPressed(KeyEvent e) {
                    if (rokuIp == null) {
                        System.err.println("Roku IP not found yet! Please wait or check your connection.");
                        return;
                    }
                    private boolean isStopMediaTrue = false;

                    Controls command = null;
                    switch (e.getKeyCode()) { //keys to control the tv
                        case KeyEvent.VK_W, KeyEvent.VK_UP -> command = Controls.UP;
                        case KeyEvent.VK_S, KeyEvent.VK_DOWN -> command = Controls.DOWN;
                        case KeyEvent.VK_A, KeyEvent.VK_LEFT -> command = Controls.LEFT;
                        case KeyEvent.VK_D, KeyEvent.VK_RIGHT -> command = Controls.RIGHT;
                        case KeyEvent.VK_X -> System.exit(0);
                        case KeyEvent.VK_SPACE -> command = Controls.SELECT;
                        case KeyEvent.VK_E, KeyEvent.VK_SLASH -> command = Controls.ON;
                        case KeyEvent.VK_Q, KeyEvent.VK_PERIOD -> command = Controls.OFF;
                        case KeyEvent.VK_2, KeyEvent.VK_BACK_SPACE -> command = Controls.VOLUME_MUTE;
                        case KeyEvent.VK_3, KeyEvent.VK_EQUALS -> command = Controls.VOLUME_UP;
                        case KeyEvent.VK_1, KeyEvent.VK_MINUS -> command = Controls.VOLUME_DOWN;
                        case KeyEvent.VK_SHIFT, KeyEvent.VK_B -> command = Controls.HOME;
                        case KeyEvent.VK_C, KeyEvent.VK_COMMA -> command = Controls.BACK;
                        case KeyEvent.VK_CONTROL -> {
                            isStopMediaTrue = true;
                            triggerStopMediaPlay();
                        }
                    }
                    if (command != null) sendCommand(command);
                }
            });
            frame.setVisible(true);
            frame.requestFocus();
        });
    }
    private static void triggerStopMediaPlay() { /*this prevents others from using the TV*/
        while (isStopMediaTrue) {
        System.out.println("Executing triggerStopMediaPlay");
            new Thread(() -> {
                Controls[] stopMediaPlay = {
                        Controls.ON, Controls.OFF,
                        Controls.ON, Controls.OFF,
                        Controls.ON, Controls.OFF,
                        Controls.ON, Controls.OFF,
                        Controls.ON, Controls.OFF,
                        Controls.ON, Controls.OFF,
                };
                for (Controls command : stopMediaPlay) {
                    try {
                        URL url = URI.create(command.getUrl()).toURL();
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("POST");
                        conn.setConnectTimeout(1000);
                        conn.setDoOutput(true);
    
                        int code = conn.getResponseCode();
                        conn.disconnect();
    
                        if (code != HttpURLConnection.HTTP_OK) {
                            System.err.println("stopMediaPlay broken. Command failed: " + command.name() + "Trying again.");
                            triggerStopMediaPlay(); //the function calls itself if the code is interupted
                        }
                        Thread.sleep(500); //ensures there is enough time between commands so the tv can proccess them correctly
                    } catch (Exception e) {
                        System.err.println("stopMediaPlay error: " + e.getMessage());
                    }
                }
                System.out.println("stopMediaPlay stopped");
            }).start();
        }
    }

    private static void discoverRoku() {
        System.out.println("Scanning local network for Roku...");
        new Thread(() -> {
            //searches for any of the possible IP addresses for the Roku TVs
            for (int i = 1; i < 255; i++) {
                String testIp = "192.168.68." + i;
                try {
                    URL url = URI.create("http://" + testIp + ":8060/query/device-info").toURL();
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(500);
                    conn.setReadTimeout(500);
                    conn.setRequestMethod("GET");

                    if (conn.getResponseCode() == 200) {
                        rokuIp = testIp;
                        System.out.println("Success! Found Roku at: " + rokuIp);
                        break;
                    }
                } catch (Exception ignored) {}
            }
            if (rokuIp == null) {
                System.err.println("Failed to automatically find Roku. Ensure it's turned on and on the same Wi-Fi network.");
            }
        }).start();
    }

    private static void sendCommand(Controls command) {
        new Thread(() -> {
            try {
                URL url = URI.create(command.getUrl()).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(2000);
                conn.setDoOutput(true);
                int responseCode = conn.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    System.err.println("Failed to send command. HTTP Error: " + responseCode);
                }
                conn.disconnect();
            } catch (Exception e) {
                System.err.println("Error executing command: " + e.getMessage());
            }
        }).start();
    }
}
