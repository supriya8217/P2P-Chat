import java.io.*;
import java.net.*;
import java.util.Scanner;
import java.util.concurrent.*;

public class P2PChat {
    private static final int DEFAULT_PORT = 12345;
    private String username;
    private ServerSocket serverSocket;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private final Scanner scanner = new Scanner(System.in);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean isRunning = true;

    public static void main(String[] args) {
        new P2PChat().start();
    }

    public void start() {
        System.out.println("=== P2P Terminal Chat ===");
        System.out.print("Enter your username: ");
        username = scanner.nextLine().trim();
        if (username.isEmpty())
            username = "User" + (System.currentTimeMillis() % 1000);

        System.out.println("\nChoose mode:");
        System.out.println("1. Host (wait for connection)");
        System.out.println("2. Connect (connect to host)");
        System.out.print("Enter choice (1 or 2): ");
        String choice = scanner.nextLine().trim();

        try {
            if (choice.equals("1")) {
                host();
            } else if (choice.equals("2")) {
                connect();
            } else {
                System.out.println("Invalid choice. Exiting.");
                return;
            }
            startChat();
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void host() throws IOException {
        System.out.print("Enter port (default " + DEFAULT_PORT + "): ");
        int port = readPort();
        serverSocket = new ServerSocket(port);
        System.out.println("Waiting for connection on port " + port + "...");
        System.out.println("Your IP: " + getLocalIP());
        socket = serverSocket.accept();
        System.out.println("Connected to: " + socket.getInetAddress().getHostAddress());
        setupStreams();
    }

    private void connect() throws IOException {
        System.out.print("Enter host IP address: ");
        String host = scanner.nextLine().trim();
        System.out.print("Enter port (default " + DEFAULT_PORT + "): ");
        int port = readPort();
        System.out.println("Connecting to " + host + ":" + port + "...");
        socket = new Socket(host, port);
        System.out.println("Connected successfully!");
        setupStreams();
    }

    private int readPort() {
        String input = scanner.nextLine().trim();
        return input.isEmpty() ? DEFAULT_PORT : Integer.parseInt(input);
    }

    private void setupStreams() throws IOException {
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out.println("USERNAME:" + username);
    }

    private void startChat() {
        System.out.println("\n=== Chat Started ===");
        System.out.println("Commands: /quit, /help, /info");
        System.out.println("------------------------");

        executor.submit(this::receiveMessages);
        sendMessages();
    }

    private void receiveMessages() {
        try {
            String peerName = "Peer";
            String msg;
            while (isRunning && (msg = in.readLine()) != null) {
                if (msg.startsWith("USERNAME:")) {
                    peerName = msg.substring(9);
                    System.out.println("[" + peerName + " joined the chat]");
                } else if (msg.startsWith("QUIT:")) {
                    System.out.println("[" + peerName + " left the chat]");
                    break;
                } else {
                    System.out.println(peerName + ": " + msg);
                }
            }
        } catch (IOException e) {
            if (isRunning)
                System.err.println("Disconnected: " + e.getMessage());
        } finally {
            isRunning = false;
        }
    }

    private void sendMessages() {
        while (isRunning) {
            String msg = scanner.nextLine();
            if (msg.trim().isEmpty())
                continue;

            switch (msg.toLowerCase()) {
                case "/quit":
                    out.println("QUIT:" + username);
                    System.out.println("Goodbye!");
                    isRunning = false;
                    return;
                case "/help":
                    showHelp();
                    break;
                case "/info":
                    showInfo();
                    break;
                default:
                    out.println(msg);
                    System.out.println("You: " + msg);
            }
        }
    }

    private void showHelp() {
        System.out.println("\n=== Help ===");
        System.out.println("/quit - Exit chat");
        System.out.println("/help - Show commands");
        System.out.println("/info - Show connection info\n");
    }

    private void showInfo() {
        System.out.println("\n=== Connection Info ===");
        System.out.println("Username: " + username);
        System.out.println("Local IP: " + getLocalIP());
        if (socket != null) {
            System.out.println("Peer IP: " + socket.getInetAddress().getHostAddress());
            System.out.println("Local Port: " + socket.getLocalPort());
            System.out.println("Remote Port: " + socket.getPort());
        }
        System.out.println("========================\n");
    }

    private String getLocalIP() {
        try (Socket temp = new Socket()) {
            temp.connect(new InetSocketAddress("8.8.8.8", 53));
            return temp.getLocalAddress().getHostAddress();
        } catch (IOException e) {
            try {
                return InetAddress.getLocalHost().getHostAddress();
            } catch (Exception ex) {
                return "Unknown";
            }
        }
    }

    private void cleanup() {
        isRunning = false;
        try {
            if (out != null)
                out.close();
            if (in != null)
                in.close();
            if (socket != null && !socket.isClosed())
                socket.close();
            if (serverSocket != null && !serverSocket.isClosed())
                serverSocket.close();
        } catch (IOException e) {
            System.err.println("Cleanup error: " + e.getMessage());
        } finally {
            executor.shutdownNow();
        }
    }
}
