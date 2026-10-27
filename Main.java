
import java.io.BufferedReader;       // Reads text sent by a client
import java.io.InputStreamReader;    // Converts incoming bytes into characters
import java.io.PrintWriter;          // Sends text responses to a client
import java.net.ServerSocket;        // Listens for new client connections
import java.net.Socket;              // Represents one client's connection
import java.util.concurrent.ConcurrentHashMap; // Provides a shared, thread-safe key-value store
import java.util.concurrent.ConcurrentMap;     // Defines the shared map's interface

public class Main {

    // One shared store used by every client thread
    private static final ConcurrentMap<String, String> data =
            new ConcurrentHashMap<>();

    public static void main(String[] args) {
        int port = 6379; // The port our clients connect to

        // Starts listening for client connections on port 6379
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Mini Redis listening on port " + port);

            while (true) { // Keeps accepting new clients
                Socket clientSocket = serverSocket.accept();
                // Waits for a client and creates its connection socket

                System.out.println("Client connected: "
                        + clientSocket.getRemoteSocketAddress());
                // Displays which client connected

                new Thread(() -> handleClient(clientSocket)).start();
                // Gives this client its own thread so other clients can connect
            }

        } catch (Exception e) {
            e.printStackTrace(); // Displays server-level errors
        }
    }

    
private static void handleClient(Socket clientSocket) {
    // Handles commands from one client without blocking other clients.

    try (
        BufferedReader in = new BufferedReader(
            new InputStreamReader(clientSocket.getInputStream())
            // Reads incoming bytes as text, one line at a time.
        );
        PrintWriter out = new PrintWriter(
            clientSocket.getOutputStream(), true
            // Sends responses to this client and automatically flushes them.
        )
    ) {
        String inputLine; // Holds the next command received from this client.

        while ((inputLine = in.readLine()) != null) {
            // Continues reading until this client disconnects.

            inputLine = inputLine.trim(); // Removes spaces at the beginning and end.

            if (inputLine.isEmpty()) {
                // An empty line is ignored; wait for the next command.
                continue;
            }

            if (inputLine.equalsIgnoreCase("QUIT")) {
                // QUIT ends this client's session, not the entire server.
                out.println("BYE"); // Tells the client its session is ending.
                break; // Exits the loop so this client's connection can close.
            }

            String[] parts = inputLine.split("\\s+", 3);
            // Splits into at most 3 parts, preserving spaces inside a SET value.
            // Example: SET greeting hello world -> ["SET", "greeting", "hello world"]

            String command = parts[0].toUpperCase();
            // Makes command names case-insensitive.

            if (command.equals("SET") && parts.length == 3) {
                // SET requires a key and a value.

                data.put(parts[1], parts[2]);
                // Stores the value in the shared, thread-safe map.

                out.println("OK"); // Confirms that the value was stored.

            } else if (command.equals("GET") && parts.length == 2) {
                // GET requires exactly one key argument.

                String value = data.get(parts[1]);
                // Retrieves the value associated with the requested key.

                if (value == null) {
                    out.println("(nil)"); // The key does not exist.
                } else {
                    out.println(value); // Returns the stored value.
                }

            } else {
                // Handles unsupported commands and incorrectly formatted commands.
                out.println("ERR unknown command or invalid arguments");
            }
        }

    } catch (Exception e) {
        // Reports errors affecting this client's connection.
        System.err.println("Client error: " + e.getMessage());

    } finally {
        try {
            clientSocket.close();
            // Releases this client's socket when its session ends.
        } catch (Exception ignored) {
            // Ignores cleanup errors if the socket is already closed.
        }
    }
}
}
