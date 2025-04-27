package ServerSide;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class ClientHandler extends Thread implements Runnable {
    private Socket socket;
    private DataInputStream dis;
    private DataOutputStream dos;
    private int clientId;
    private String clientType;
    private boolean isAvailable = true;
    private String username;
    private int currentDriverId = -1;
    private int currentCustomerId = -1;
    private int pendingDriverId = -1;

    private String lastPickup = "Unknown";
    private String lastDestination = "Unknown";
    private int ratingCount = 0;
    private int totalOverall = 0, totalDriving = 0, totalCleanliness = 0, totalMusic = 0;



    public ClientHandler(Socket socket, DataInputStream dis, DataOutputStream dos, int clientId) {
        this.socket = socket;
        this.dis = dis;
        this.dos = dos;
        this.clientId = clientId;
    }

    @Override
    public void run() {
        try {
            User user = handleAuthentication();
            if (user == null) return;

            this.clientType = user.getRole();
            this.username = user.getUsername();

            if (clientType.equalsIgnoreCase("customer")) {
                Server.customers.put(clientId, this);
            } else if (clientType.equalsIgnoreCase("driver")) {
                Server.drivers.put(clientId, this);
            } else if (clientType.equalsIgnoreCase("admin")) {
                while (true) {
                    dos.writeUTF("Admin Panel:\n1. View Stats\n2. Disconnect");
                    String choice = dis.readUTF().trim();

                    if (choice.equals("1")) {
                        dos.writeUTF(getStats());
                    } else if (choice.equals("2")) {
                        dos.writeUTF("Disconnected.");
                        socket.close();
                        break;
                    } else {
                        dos.writeUTF("Invalid option.");
                    }
                }
                return;
            }

            dos.writeUTF("Connected as " + clientType + " with ID " + clientId);

            while (true) {
                String msg = dis.readUTF();
                if (msg.equalsIgnoreCase("exit") || msg.equalsIgnoreCase("disconnect")) {
                    if (isInRide()) {
                        dos.writeUTF("You cannot disconnect during an ongoing ride.");
                        continue;
                    } else {
                        dos.writeUTF("Disconnected.");
                        socket.close();
                        break;
                    }
                }
                if (clientType.equals("customer") && msg.startsWith("ride_request:")) {
                    String[] parts = msg.split(":");
                    if (parts.length < 3) {
                        dos.writeUTF("Invalid ride_request format.");
                        continue;
                    }
                    String pickup = parts[1];
                    String destination = parts[2];
                    handleRideRequest(pickup, destination);

                } else if (clientType.equals("driver") && msg.startsWith("offer:")) {
                    String[] parts = msg.split(":");
                    int customerId = Integer.parseInt(parts[1]);
                    String price = parts[2];
                    sendOfferToCustomer(customerId, price);

                }
                else if (msg.startsWith("response:")) {
                    handleCustomerResponse(msg);
                }
                else if (msg.startsWith("status:")) {
                    updateStatus(msg.split(":")[1]);
                } else if (clientType.equals("customer") && msg.startsWith("accept_offer:")) {
                    int driverId = Integer.parseInt(msg.split(":")[1]);
                    assignRide(driverId, lastPickup, lastDestination);
                }
                else if (msg.equalsIgnoreCase("status_request")) {
                    sendRideStatus();
                }
                else if (msg.startsWith("rate_driver:")) {
                    handleDriverRating(msg);
                }
                else if (msg.startsWith("location:")) {
                    handleLocationUpdate(msg.split(":")[1]);
                }
            }

        } catch (IOException e) {
            System.out.println("Client " + clientId + " (" + username + ") disconnected.");
        }
    }

    private void handleRideRequest(String pickup, String destination) throws IOException {
        System.out.println("Customer " + clientId + " requested ride from " + pickup + " to " + destination);
        this.lastPickup = pickup;
        this.lastDestination = destination;
        List<ClientHandler> highPriorityDrivers = new ArrayList<>();
        List<ClientHandler> lowPriorityDrivers = new ArrayList<>();
        for (ClientHandler driver : Server.drivers.values()) {
            if (driver.isAvailable) {
                if (driver.isLowPriority()) {
                    lowPriorityDrivers.add(driver);
                } else {
                    highPriorityDrivers.add(driver);
                }
            }
        }

        boolean sent = false;

        for (ClientHandler driver : highPriorityDrivers) {
            driver.dos.writeUTF("ride_request:" + clientId + ":" + pickup + ":" + destination);
            sent = true;
        }

        if (sent) {
            dos.writeUTF("Ride request sent to high-priority drivers.");
        } else if (!lowPriorityDrivers.isEmpty()) {
            dos.writeUTF("Waiting for high-priority drivers... you'll be matched shortly.");
        } else {
            dos.writeUTF("No drivers currently available.");
            return;
        }

        if (!lowPriorityDrivers.isEmpty()) {
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        for (ClientHandler driver : lowPriorityDrivers) {
                            driver.dos.writeUTF("ride_request:" + clientId + ":" + pickup + ":" + destination);
                        }
                        System.out.println("Low-priority drivers notified for customer " + clientId);
                    } catch (IOException e) {
                        System.out.println("Error sending to low-priority drivers: " + e.getMessage());
                    }
                }
            }, 10000);
        }
    }
    private void sendOfferToCustomer(int customerId, String price) throws IOException {
        if (!isAvailable || currentCustomerId != -1) {
            dos.writeUTF(" You are already handling a ride or waiting for response.");
            return;
        }
        ClientHandler customer = Server.customers.get(customerId);
        if (customer != null) {
            customer.pendingDriverId = this.clientId;
            customer.dos.writeUTF("offer_from_driver:" + clientId + ":" + price);
            dos.writeUTF("Offer sent to customer. Waiting for response...");
            isAvailable = false;
        } else {
            dos.writeUTF("Customer not found.");
        }
    }
    private void handleCustomerResponse(String msg) throws IOException {
        String[] parts = msg.split(":");
        if (parts.length < 3) {
            dos.writeUTF("Invalid response format.");
            return;
        }
        int driverId = Integer.parseInt(parts[1]);
        String response = parts[2]; // "accept"  "reject"

        ClientHandler driver = Server.drivers.get(driverId);
        if (driver == null) {
            dos.writeUTF("Driver not found.");
            return;
        }

        if (response.equalsIgnoreCase("accept")) {
            assignRide(driverId, this.lastPickup, this.lastDestination);
        } else if (response.equalsIgnoreCase("reject")) {
            driver.isAvailable = true;
            driver.dos.writeUTF(" Your offer was rejected by the customer.");
            dos.writeUTF("You rejected the offer from driver ID: " + driverId);
        } else {
            dos.writeUTF("Invalid response. Type 'accept' or 'reject'.");
        }
    }
    private void assignRide(int driverId, String pickup, String destination) throws IOException {
        ClientHandler driver = Server.drivers.get(driverId);
        if (driver != null) {
            driver.isAvailable = false;
            this.currentDriverId = driverId;
            driver.currentCustomerId = this.clientId;

            driver.dos.writeUTF("Your offer was accepted.");
            this.dos.writeUTF("Ride assigned to driver ID: " + driverId);

            Server.rideHistory.add(new Ride(this.clientId, driverId, pickup, destination, "pending"));
        } else {
            dos.writeUTF("Selected driver not found.");
        }
    }

    private void sendRideStatus() throws IOException {
        if (clientType.equals("customer")) {
            if (currentDriverId == -1) {
                dos.writeUTF("ride_status:No active ride");
            } else {
                for (Ride ride : Server.rideHistory) {
                    if (ride.getCustomerId() == this.clientId && ride.getDriverId() == currentDriverId) {
                        dos.writeUTF("ride_status:" + ride.getStatus());
                        return;
                    }
                }
                dos.writeUTF("ride_status:Pending");
            }
        } else {
            dos.writeUTF("ride_status:N/A");
        }
    }

    private void updateStatus(String status) throws IOException {
        if (status.equals("start")) {
            isAvailable = false;
            for (Ride ride : Server.rideHistory) {
                if (ride.getCustomerId() == currentCustomerId && ride.getDriverId() == this.clientId) {
                    ride.setStatus(status.equals("start") ? "started" : "completed");
                    break;
                }
            }
            dos.writeUTF("Ride started.");
            if (currentCustomerId != -1 && Server.customers.containsKey(currentCustomerId)) {
                Server.customers.get(currentCustomerId).dos.writeUTF("Your driver has started the ride.");
            }
        } else if (status.equals("finish")) {
            isAvailable = true;
            for (Ride ride : Server.rideHistory) {
                if (ride.getCustomerId() == currentCustomerId && ride.getDriverId() == this.clientId) {
                    ride.setStatus(status.equals("start") ? "started" : "completed");
                    break;
                }
            }
            dos.writeUTF("Ride completed. You're available for new rides.");
            if (currentCustomerId != -1 && Server.customers.containsKey(currentCustomerId)) {
                Server.customers.get(currentCustomerId).dos.writeUTF("Your ride has been completed.");
            }
            currentCustomerId = -1;
            currentDriverId = -1;
        }

        for (Ride r : Server.rideHistory) {
            if (r.customerId == this.clientId || r.driverId == this.clientId) {
                if (status.equals("start")) r.status = "started";
                if (status.equals("finish")) r.status = "completed";
                break;
            }
        }
    }

    private boolean isInRide() {
        if (clientType.equals("driver")) {
            return currentCustomerId != -1;
        } else if (clientType.equals("customer")) {
            return currentDriverId != -1;
        }
        return false;
    }
    private String getStats() {
        StringBuilder sb = new StringBuilder();
        sb.append("Total Customers: ").append(Server.customers.size()).append("\n");
        sb.append("Total Drivers: ").append(Server.drivers.size()).append("\n\n");

        sb.append("Ride History:\n");
        if (Server.rideHistory.isEmpty()) {
            sb.append("No rides recorded.\n");
        } else {
            for (Ride r : Server.rideHistory) {
                sb.append("- Customer ID: ").append(r.customerId).append(", Driver ID: ").append(r.driverId).append(", Pickup: ").append(r.pickup).append(", Destination: ").append(r.destination).append(", Status: ").append(r.status).append("\n");
            }
        }

        return sb.toString();
    }
    private User handleAuthentication() throws IOException {
        while (true) {
            dos.writeUTF("Welcome! Type 'login' or 'register':");
            String choice = dis.readUTF().trim().toLowerCase();

            if (choice.equals("register")) {
                dos.writeUTF("Enter username:");
                String username = dis.readUTF().trim();
                dos.writeUTF("Enter password:");
                String password = dis.readUTF().trim();
                dos.writeUTF("Enter role (customer/driver):");
                String role = dis.readUTF().trim().toLowerCase();
                if (!role.equals("customer") && !role.equals("driver")) {
                    dos.writeUTF("Invalid role. Only 'customer' or 'driver' allowed.");
                    continue;
                }
                boolean success = UserManager.register(username, password, role);
                if (success) {
                    dos.writeUTF("Registered successfully. Please login.");
                    continue;
                } else {
                    dos.writeUTF("Username already exists. Try logging in.");
                }
            }
            if (choice.equals("login")) {
                dos.writeUTF("Enter username:");
                String username = dis.readUTF().trim();

                dos.writeUTF("Enter password:");
                String password = dis.readUTF().trim();

                User user = UserManager.login(username, password);
                if (user != null) {
                    dos.writeUTF("Login successful. Welcome, " + user.getRole());
                    return user;
                } else {
                    dos.writeUTF("Invalid credentials. Try again.");
                }
            }

            if (choice.equals("exit")) {
                dos.writeUTF("Goodbye.");
                socket.close();
                return null;
            }
        }
    }
    private void handleDriverRating(String msg) throws IOException {
        String[] parts = msg.split(":");
        if (parts.length != 5) {
            dos.writeUTF("Invalid rating format.");
            return;
        }
        int driverId = Integer.parseInt(parts[1]);
        int driving = Integer.parseInt(parts[2]);
        int cleanliness = Integer.parseInt(parts[3]);
        int music = Integer.parseInt(parts[4]);
        int overall=(driving+cleanliness+music)/3;
        if (!isInRange(overall) || !isInRange(driving) || !isInRange(cleanliness) || !isInRange(music)) {
            dos.writeUTF("Invalid input. Ratings must be between 1 and 5.");
            return;
        }
        boolean rideFound = false;
        for (Ride ride : Server.rideHistory) {
            if (ride.getCustomerId() == this.clientId &&
                    ride.getDriverId() == driverId &&
                    ride.getStatus().equalsIgnoreCase("completed")) {
                rideFound = true;
                break;
            }
        }

        if (!rideFound) {
            dos.writeUTF("You can only rate drivers you’ve completed a ride with.");
            return;
        }
        ClientHandler driver = Server.drivers.get(driverId);
        if (driver == null) {
            dos.writeUTF("Driver not found.");
            return;
        }

        driver.addRating(driving, cleanliness, music);
        dos.writeUTF("Thank you for rating driver ID: " + driverId);
    }

    public void addRating(int driving, int cleanliness, int music) throws IOException {
        int overall = (driving + cleanliness + music) / 3;

        ratingCount++;
        totalOverall += overall;
        totalDriving += driving;
        totalCleanliness += cleanliness;
        totalMusic += music;

        double avgOverall = totalOverall * 1.0 / ratingCount;
        double avgDriving = totalDriving * 1.0 / ratingCount;
        double avgCleanliness = totalCleanliness * 1.0 / ratingCount;
        double avgMusic = totalMusic * 1.0 / ratingCount;

        try (BufferedWriter writer = new BufferedWriter(new FileWriter("driver_ratings.txt", true))) {
            writer.write("DriverID: " + clientId + " | Driving: " + driving + " | Cleanliness: " + cleanliness + " | Music: " + music + " | Computed Overall: " + overall + "\n");
        } catch (IOException e) {
            System.out.println("Failed to write rating to file: " + e.getMessage());
        }

        dos.writeUTF("You received a new rating from a customer:");
        dos.writeUTF(" Driving Skill: " + driving + "\n Cleanliness: " + cleanliness + "\n Music: " + music + "\n  Overall: " + overall);

        dos.writeUTF(String.format(" Your updated average ratings:\n- Overall: %.2f\n- Driving: %.2f\n- Cleanliness: %.2f\n- Music: %.2f", avgOverall, avgDriving, avgCleanliness, avgMusic));

        System.out.println("Driver " + clientId + " received new computed rating.");
    }


    private boolean isInRange(int rating) {
        return rating >= 1 && rating <= 5;
    }
    private boolean isLowPriority() {
        return ratingCount > 0 && (totalOverall *1 / ratingCount) < 3.0;
    }
    private void handleLocationUpdate(String minutesAwayStr) throws IOException {
        if (clientType.equals("driver")) {
            if (currentCustomerId == -1) {
                dos.writeUTF("No assigned ride to update location for.");
                return;
            }

            ClientHandler customer = Server.customers.get(currentCustomerId);
            if (customer != null) {
                customer.dos.writeUTF(" Your driver is " + minutesAwayStr + " minutes away.");
                dos.writeUTF(" Location update sent to customer.");
            } else {
                dos.writeUTF("Customer not found.");
            }
        } else {
            dos.writeUTF("Only drivers can send location updates.");
        }
    }


}