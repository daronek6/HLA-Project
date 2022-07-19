package projekt.region;

import java.util.LinkedList;
import java.util.Queue;

public class Region {

    private int id;
    private Queue<Integer> passengersQueue = new LinkedList<>();
    private Queue<Integer> taxiesQueue = new LinkedList<>();

    public Region(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }


    public void addPassengerToQueue(int id) {
        passengersQueue.add(id);
    }

    public void addTaxiToQueue(int id) {
        taxiesQueue.add(id);
    }

    public int getPassengerQueueLength() {
        return passengersQueue.size();
    }

    public int getTaxiesQueueLength() {
        return taxiesQueue.size();
    }

    public int getPassengerFromQueue() {
        return passengersQueue.remove();
    }

    public int getTaxiFromQueue() {
        return taxiesQueue.remove();
    }
}
