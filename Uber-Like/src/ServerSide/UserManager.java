package ServerSide;

import java.io.*;
import java.util.*;

public class UserManager {
    private static final String FILE_PATH = "users.txt";
    private static Map<String, User> users = new HashMap<>();

    static {
        loadUsers();
        if (!users.containsKey("admin")) {
            users.put("admin", new User("admin", "admin123", "admin"));
            saveUser(new User("admin", "admin123", "admin"));
        }
    }

    public static synchronized boolean register(String username, String password, String role) {
        if (users.containsKey(username)) return false;

        User newUser = new User(username, password, role);
        users.put(username, newUser);
        saveUser(newUser);
        return true;
    }

    public static synchronized User login(String username, String password) {
        System.out.println("Attempting login for: " + username);

        if (!users.containsKey(username)) {
            System.out.println("Username not found.");
            return null;
        }

        User user = users.get(username);
        System.out.println("Found user: " + user.getUsername() + ", password: " + user.getPassword());

        if (user.getPassword().equals(password)) {
            System.out.println("Password matches.");
            return user;
        } else {
            System.out.println("Password does not match.");
            return null;
        }
    }
    public static void loadUsers() {
        File file = new File(FILE_PATH);
        if (!file.exists()) return;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length == 3) {
                    String username = parts[0];
                    String password = parts[1];
                    String role = parts[2];
                    users.put(username, new User(username, password, role));
                    System.out.println("Loaded user: " + username + ", role: " + role);
                }
            }
        } catch (IOException e) {
            System.out.println("Failed to load users: " + e.getMessage());
        }
    }
    private static void saveUser(User user) {
        try {
            File file = new File(FILE_PATH);
            if (!file.exists()) {
                file.createNewFile();
            }

            BufferedWriter writer = new BufferedWriter(new FileWriter(file, true));
            writer.write(user.getUsername() + "," + user.getPassword() + "," + user.getRole());
            writer.newLine();
            writer.close();

            System.out.println("User saved: " + user.getUsername());
        } catch (IOException e) {
            System.out.println("Failed to save user: " + e.getMessage());
        }
    }

}
