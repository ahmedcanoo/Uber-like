package ServerSide;

public class Ride {
    public final int customerId;
    public final int driverId;
    public final String pickup;
    public final String destination;
    public String status;

    public Ride(int customerId, int driverId, String pickup, String destination, String status) {
        this.customerId = customerId;
        this.driverId = driverId;
        this.pickup = pickup;
        this.destination = destination;
        this.status = status;
    }
    public int getCustomerId() {
        return customerId;
    }

    public int getDriverId() {
        return driverId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
