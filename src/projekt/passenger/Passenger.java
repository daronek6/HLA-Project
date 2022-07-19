package projekt.passenger;

import projekt.region.RegionAmbassador;

import java.util.*;

public class Passenger {

    private static int idCounter = 0;
    private int id;
    private int startRegionId;
    private int endRegionId;

    public Passenger() {
        id = idCounter;

        Random rand = new Random();
        ArrayList<Integer> availableRegions = new ArrayList<>();
        for(int i=0;i<RegionAmbassador.NUM_OF_REGIONS;i++) {
            availableRegions.add(i);
        }

        startRegionId = rand.nextInt(availableRegions.size());
        availableRegions.remove(startRegionId);
        endRegionId =  availableRegions.get(rand.nextInt(availableRegions.size()));

        idCounter++;
    }
    public Passenger(int id, int startRegionId, int endRegionId) {
        this.id = id;
        this.startRegionId = startRegionId;
        this.endRegionId = endRegionId;
    }

    public int getId() {
        return id;
    }

    public int getStartRegionId() {
        return startRegionId;
    }

    public int getEndRegionId() {
        return endRegionId;
    }
}
