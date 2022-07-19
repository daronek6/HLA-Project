package projekt.taxi;

import java.util.Random;

public class Taxi {

    private static int idCounter = 0;

    private final double MIN_AVG_SPEED = 30.0;
    private final double MAX_AVG_SPEED = 60.0;
    private int id;
    private double avgSpeed;
    private boolean occupied;

    public Taxi() {
        id = idCounter;
        avgSpeed = new Random().nextDouble() * (MAX_AVG_SPEED - MIN_AVG_SPEED) + MIN_AVG_SPEED;
        occupied = false;

        idCounter++;
    }
    public Taxi(int id, double avgSpeed) {
        this.id = id;
        this.avgSpeed = avgSpeed;

        occupied = false;
    }

    public int getId() {
        return id;
    }

    public double getAvgSpeed() {
        return avgSpeed;
    }

    public void passengerEnter() {
        occupied = true;
    }

    public void passengerLeave() {
        occupied = false;
    }
}
