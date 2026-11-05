import java.awt.Toolkit;
import javax.swing.*;
import java.awt.event.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.Scanner;

class Remotev2 {
    private static volatile String rokuIp = null;
    private static volatile boolean isStopMedia = false;
    private static final Scanner input = new Scanner(System.in);

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(2000))
            .build();

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
        System.out.println("Search for IP address automatically? (y/n) ");
        if (input.nextLine().toLowerCase().contains("y")) {
            discoverRoku();
        } else {
            System.out.println("Complete the IP address: 192.168.68.");
            rokuIp = "192.168.68." + input.nextLine();
        }

        SwingUtilities.invokeLater(() -> {
            final JFrame frame = new JFrame("Remote Control");
            frame.setUndecorated(true);
            frame.setSize(1, 1);
            frame.setLocationRelativeTo(null);

            frame.addKeyListener(new KeyAdapter() {
                @Override
                    public void keyPressed(KeyEvent e) {
                    if (rokuIp == null || rokuIp.isEmpty()) {
                        System.err.println("Roku IP not found yet! Please wait or check your connection.");
                        return;
                    }

                    Controls command = null;
                    switch (e.getKeyCode()) { //keys to control the tv
                        case KeyEvent.VK_W, KeyEvent.VK_UP -> command = Controls.UP;
                        case KeyEvent.VK_S, KeyEvent.VK_DOWN -> command = Controls.DOWN;
                        case KeyEvent.VK_A, KeyEvent.VK_LEFT -> command = Controls.LEFT;
                        case KeyEvent.VK_D, KeyEvent.VK_RIGHT -> command = Controls.RIGHT;
                        case KeyEvent.VK_X -> {
                            System.out.println("User Ending Session: Turn TV off? (y/n)");
                            if (input.nextLine().toLowerCase().contains("y")) command = Controls.OFF;
                            System.exit(0);
                        }
                        case KeyEvent.VK_SPACE -> command = Controls.SELECT;
                        case KeyEvent.VK_E, KeyEvent.VK_SLASH -> command = Controls.ON;
                        case KeyEvent.VK_Q, KeyEvent.VK_PERIOD  -> command = Controls.OFF;
                        case KeyEvent.VK_2, KeyEvent.VK_BACK_SPACE -> command = Controls.VOLUME_MUTE;
                        case KeyEvent.VK_3, KeyEvent.VK_EQUALS -> command = Controls.VOLUME_UP;
                        case KeyEvent.VK_1, KeyEvent.VK_MINUS -> command = Controls.VOLUME_DOWN;
                        case KeyEvent.VK_SHIFT, KeyEvent.VK_B -> command = Controls.HOME;
                        case KeyEvent.VK_C, KeyEvent.VK_COMMA -> command = Controls.BACK;
                        case KeyEvent.VK_CAPS_LOCK -> { //This way the user can easily see the value of isStopMedia
                            boolean capState = Toolkit.getDefaultToolkit().getLockingKeyState(KeyEvent.VK_CAPS_LOCK);
                            if (capState && !isStopMedia) {
                                isStopMedia = true;
                                triggerStopMediaPlay();
                            } else if (!capState) {
                                isStopMedia = false;
                                System.out.println("Exiting Stop Media...");
                            }
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
        while (isStopMedia) {
            System.out.println("Executing triggerStopMediaPlay");
            new Thread(() -> {
                Controls[] stopMediaPlay = {
                        Controls.OFF, Controls.ON,
                };
                for (Controls command : stopMediaPlay) {
                    if (!isStopMedia) break;
                    try {
                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(command.getUrl()))
                                .POST(HttpRequest.BodyPublishers.noBody())
                                .build();
                        httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                        Thread.sleep(700); //ensures there is enough time between commands so the tv can process them correctly
                    } catch (Exception e) { System.err.println("stopMediaPlay error: " + e.getMessage()); }
                }
                System.out.println("stopMediaPlay stopped");
            }).start();
        }
    }

    private static void discoverRoku() {
        System.out.println("Scanning local network for Roku...");
        new Thread(() -> {
            //searches for any of the possible IP addresses for the Roku TVs
            HttpClient scan = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(300))
                    .build();
            for (int i = 1; i < 255; i++) {
                if (rokuIp != null) break;
                String testIp = "192.168.68." + i;
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("http://" + testIp + ":8060/query/device-info"))
                            .timeout(Duration.ofMillis(300)).GET().build();
                    HttpResponse<String> response = scan.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 200) {
                        rokuIp = testIp;
                        System.out.println("\n[Success] Found Roku at: " + rokuIp);
                        return;
                    }
                } catch (Exception ignored) {}
            }
            if (rokuIp == null) System.err.println("\n [Error] Failed to automatically find Roku.");
        }).start();
    }

    private static void sendCommand(Controls command) {
        new Thread(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(command.getUrl()))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build();
                HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() != 200) System.err.println("Failed to send command. HTTP Error: " + response.statusCode());
            } catch (Exception e) {  System.err.println("Error executing command: " + e.getMessage());  }
        }).start();
    }
}
