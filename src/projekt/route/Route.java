package projekt.route;

import java.util.Random;

public class Route {

    private final double MIN_DISTANCE = 10.0;
    private final double MAX_DISTANCE = 40.0;
    private static int idCounter = 0;
    private int id;

    private int startRegionId;
    private int endRegionId;

    private double distance;

    public Route(int startRegionId, int endRegionId) {
        id = idCounter;

        this.startRegionId = startRegionId;
        this.endRegionId = endRegionId;

        distance = new Random().nextDouble() * (MAX_DISTANCE - MIN_DISTANCE) + MIN_DISTANCE;

        idCounter++;
    }

    public int getId() {
        return id;
    }

    public boolean sameRoute(int startRegion, int endRegion) {
        if((startRegionId == startRegion && endRegionId == endRegion) || (startRegionId == endRegion && endRegionId == startRegion))
            return true;

        return false;
    }

    public double getDistance() {
        return distance;
    }

    @Override
    public String toString() {
        return "start: " + startRegionId + ", end: " + endRegionId + ",dist: " + distance;
    }
}
