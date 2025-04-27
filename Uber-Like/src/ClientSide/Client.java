package ClientSide;

import java.io.*;
import java.net.*;
import java.util.Scanner;

public class Client {

    private static volatile boolean running = true;
    private static String currentRideStatus = "No active ride";
    private static String clientType = "";

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.print("Enter server IP address (localhost): ");
        String ip = scanner.nextLine();

        System.out.print("Enter server port (5056): ");
        int port = Integer.parseInt(scanner.nextLine());

        try (
                Socket socket = new Socket(ip, port);
                DataInputStream dis = new DataInputStream(socket.getInputStream());
                DataOutputStream dos = new DataOutputStream(socket.getOutputStream())
        ) {
            // Authentication
            while (true) {
                String serverPrompt = dis.readUTF();
                System.out.println("SERVER: " + serverPrompt);

                if (serverPrompt.toLowerCase().contains("type 'login' or 'register'")) {
                    System.out.print(">> ");
                    dos.writeUTF(scanner.nextLine().trim());
                } else if (serverPrompt.toLowerCase().contains("enter username")) {
                    System.out.print("Username: ");
                    dos.writeUTF(scanner.nextLine().trim());
                } else if (serverPrompt.toLowerCase().contains("enter password")) {
                    System.out.print("Password: ");
                    dos.writeUTF(scanner.nextLine().trim());
                } else if (serverPrompt.toLowerCase().contains("enter role")) {
                    System.out.print("Role (customer/driver): ");
                    String role = scanner.nextLine().trim();
                    clientType = role.toLowerCase();
                    dos.writeUTF(role);
                } else if (serverPrompt.toLowerCase().contains("login successful")) {
                    if (serverPrompt.toLowerCase().contains("customer")) clientType = "customer";
                    else if (serverPrompt.toLowerCase().contains("driver")) clientType = "driver";
                    else if (serverPrompt.toLowerCase().contains("admin")) {
                        handleAdmin(dis, dos, scanner);
                        return;
                    }
                    System.out.println(serverPrompt);
                    break;
                } else if (serverPrompt.toLowerCase().contains("goodbye") ||
                        serverPrompt.toLowerCase().contains("connection closed")) {
                    System.out.println(serverPrompt);
                    return;
                } else {
                    System.out.println(serverPrompt);
                }
            }
            // Receiver thread
            Thread receiver = new Thread(() -> {
                try {
                    while (true) {
                        String msg = dis.readUTF();

                        if (msg.contains("Disconnected.")) {
                            System.out.println("[SERVER] " + msg);
                            running = false;
                            System.exit(0);
                            break;
                        } else if (msg.contains("You cannot disconnect")) {
                            System.out.println("[SERVER] " + msg);
                        } else {
                            System.out.println("\n[SERVER] " + msg);
                        }
                        System.out.print(">> ");
                    }
                } catch (IOException e) {
                    if (running) {
                        System.out.println("Server disconnected.");
                        running = false;
                        System.exit(0);
                    }
                }
            });
            receiver.start();
            while (true) {
                if (!running) break;
                showMenu();
                System.out.print(">> ");
                if (!running) break;
                String input = scanner.nextLine();
                if (!running) break;
                switch (input) {
                    case "1":
                        if (clientType.equals("customer")) {
                            System.out.print("Enter pickup location: ");
                            String pickup = scanner.nextLine();
                            System.out.print("Enter destination: ");
                            String destination = scanner.nextLine();
                            dos.writeUTF("ride_request:" + pickup + ":" + destination);
                        } else {
                            System.out.print("Enter customer ID to offer ride: ");
                            String customerId = scanner.nextLine();
                            System.out.print("Enter your fare offer: ");
                            String price = scanner.nextLine();
                            dos.writeUTF("offer:" + customerId + ":" + price);
                        }
                        break;
                    case "2":
                        if (clientType.equals("customer")) {
                            dos.writeUTF("status_request");
                            System.out.println("Current ride status: " + currentRideStatus);
                        } else {
                            System.out.print("Enter status update (start/finish): ");
                            String status = scanner.nextLine().trim().toLowerCase();
                            if (status.equals("start") || status.equals("finish")) {
                                dos.writeUTF("status:" + status);
                            } else {
                                System.out.println("Invalid status. Use 'start' or 'finish'.");
                            }
                        }
                        break;
                    case "3":
                        if (clientType.equals("customer")) {
                            System.out.print("Enter driver ID to respond to: ");
                            String driverId = scanner.nextLine();
                            System.out.print("Do you want to accept or reject the offer? (accept/reject): ");
                            String decision = scanner.nextLine().trim().toLowerCase();

                            if (decision.equals("accept") || decision.equals("reject")) {
                                dos.writeUTF("response:" + driverId + ":" + decision);
                            } else {
                                System.out.println("Invalid input. Please type 'accept' or 'reject'.");
                            }
                        } else {
                            dos.writeUTF("exit");
                        }
                        break;

                    case "4":
                        if (clientType.equals("customer")) {
                            dos.writeUTF("exit");
                           // running=false;
                        } else if (clientType.equals("driver")) {
                            System.out.print("How many minutes away are you? ");
                            String mins = scanner.nextLine();

                            try {
                                int minutesAway = Integer.parseInt(mins);
                                if (minutesAway < 0 || minutesAway > 60) {
                                    System.out.println("Please enter a valid number between 0 and 60.");
                                    break;
                                }
                                dos.writeUTF("location:" + minutesAway);
                            } catch (NumberFormatException e) {
                                System.out.println("Invalid input. Please enter a number.");
                            }
                        } else {
                            System.out.println("Invalid option.");
                        }
                        break;

                    case "5":
                        System.out.print("Enter Driver ID to rate: ");
                        String rateDriverId = scanner.nextLine();

                        System.out.print("Driving skill (1-5): ");
                        String driving = scanner.nextLine();

                        System.out.print("Clean (1-5): ");
                        String clean = scanner.nextLine();

                        System.out.print("Music quality (1-5): ");
                        String music = scanner.nextLine();

                        String review = String.format("rate_driver:%s:%s:%s:%s", rateDriverId, driving, clean, music);
                        dos.writeUTF(review);
                        break;

                    default:
                        System.out.println("Invalid option.");
                }
            }
            receiver.join();
            System.out.println("Disconnected. Goodbye!");
            System.exit(0);

        } catch (IOException | InterruptedException e) {
            System.out.println("ERROR: " + e.getMessage());
        }
    }

    private static void showMenu() {
        if (clientType.equals("customer")) {
            System.out.println("\n[Customer Menu]");
            System.out.println("1. Request a Ride");
            System.out.println("2. View Ride Status");
            System.out.println("3. Accept/Reject Offer from Driver");
            System.out.println("4. Disconnect");
            System.out.println("5. Rate Your Driver");
        } else if (clientType.equals("driver")) {
            System.out.println("\n[Driver Menu]");
            System.out.println("1. Offer Fare for a Ride");
            System.out.println("2. Send Ride Status Update (start/finish)");
            System.out.println("3. Disconnect");
            System.out.println("4. Send Location Update to Customer");
        }
    }

    private static void handleAdmin(DataInputStream dis, DataOutputStream dos, Scanner scanner) throws IOException {
        System.out.println("Logged in as Admin.");

        while (running) {
            String prompt = dis.readUTF();
            System.out.println(prompt);

            System.out.print(">> ");
            String input = scanner.nextLine();
            dos.writeUTF(input);

            if (input.equals("2")) {
                running = false;
                break;
            } else {
                String response = dis.readUTF();
                System.out.println(response);
            }
        }

        System.out.println("Disconnected. Goodbye!");
    }
}
