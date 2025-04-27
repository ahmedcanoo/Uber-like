package ServerSide;

import java.io.*;
import java.net.*;
import java.util.*;
import ServerSide.Ride;


public class Server {
    public static Map<Integer, ClientHandler> customers = new HashMap<>();
    public static Map<Integer, ClientHandler> drivers = new HashMap<>();
    public static List<Ride> rideHistory = new ArrayList<>();

    private static int clientIdCounter = 1;

    public static synchronized int getNextClientId() {
        return clientIdCounter++;
    }

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(5056)) {
            System.out.println("Server is running on port 5056...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket);

                DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
                DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream());

                int clientId = getNextClientId();

                ClientHandler handler = new ClientHandler(clientSocket, dis, dos, clientId);
                handler.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
