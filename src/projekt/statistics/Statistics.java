package projekt.statistics;

import projekt.region.RegionAmbassador;

import java.util.Arrays;

public class Statistics {

    private int numOfRides = 0;
    private int waitingPassengers = 0;
    private int[] passengerQueueLengths;
    private int[] taxiesQueueLengths;
    private int[] passengerQueueMaxLength;
    private int[] taxiesQueueMaxLength;
    private double previousPassengerQueueChangeTime = 0;
    private double[] waitingPassengersTimes = new double[100];

    public Statistics() {
        passengerQueueLengths = new int[RegionAmbassador.NUM_OF_REGIONS];
        taxiesQueueLengths = new int[RegionAmbassador.NUM_OF_REGIONS];
        passengerQueueMaxLength = new int[RegionAmbassador.NUM_OF_REGIONS];
        taxiesQueueMaxLength = new int[RegionAmbassador.NUM_OF_REGIONS];

        Arrays.fill(passengerQueueLengths, 0);
        Arrays.fill(taxiesQueueLengths, 0);
        Arrays.fill(passengerQueueMaxLength, 0);
        Arrays.fill(taxiesQueueMaxLength, 0);
    }

    public void addCompletedRide(double time) {
        numOfRides++;
    }

    public void addPassengerToQueue(int regionId, double time) {
        waitingPassengersTimes[waitingPassengers] += time - previousPassengerQueueChangeTime;

        passengerQueueLengths[regionId]++;
        waitingPassengers++;

        if(passengerQueueLengths[regionId] > passengerQueueMaxLength[regionId]) {
            passengerQueueMaxLength[regionId] = passengerQueueLengths[regionId];
        }

        previousPassengerQueueChangeTime = time;
    }

    public void addTaxiToQueue(int regionId, double time) {


        taxiesQueueLengths[regionId]++;

        if(taxiesQueueLengths[regionId] > taxiesQueueMaxLength[regionId]) {
            taxiesQueueMaxLength[regionId] = taxiesQueueLengths[regionId];
        }

    }

    public void removePassengerFromQueue(int regionId, double time) {
        waitingPassengersTimes[waitingPassengers] += time - previousPassengerQueueChangeTime;

        passengerQueueLengths[regionId]--;
        waitingPassengers--;

        previousPassengerQueueChangeTime = time;
    }

    public void removeTaxiFromQueue(int regionId, double time) {
        taxiesQueueLengths[regionId]--;

    }

    public void printStats(double time) {
        System.out.println("Time: " + time);
        System.out.println("Finished rides: " + numOfRides);
        System.out.println("Daily rides estimate: " + (86400.0/(time / (double) numOfRides)));
        printAverageMoreTaxiesNeeded(time);
        System.out.println("------------Passenger queues-----------------");
        for(int i=0; i<passengerQueueLengths.length; i++) {
            System.out.println("Regionid: " + i + " current passengers: " + passengerQueueLengths[i] + " max: " + passengerQueueMaxLength[i]);
        }
        System.out.println("------------Taxies queues-----------------");
        for(int i=0; i<taxiesQueueLengths.length; i++) {
            System.out.println("Regionid: " + i + " current taxies: " + taxiesQueueLengths[i] + " max " + taxiesQueueMaxLength[i]);
        }
        System.out.println();
    }

    public void printAverageMoreTaxiesNeeded(double time) {
        double averageMoreTaxies = 0;
        System.out.println("------------Waiting passengers times-----------------");
        for (int i=0;i<waitingPassengersTimes.length;i++) {
            if(waitingPassengersTimes[i] > 0) {
                averageMoreTaxies += i * (waitingPassengersTimes[i]/time);
                System.out.println("Waiting passengers: " + i + " time: " + waitingPassengersTimes[i] + "s. probability: " + (waitingPassengersTimes[i]/time));
            }
        }
        System.out.println("------------Avg. taxies needed-----------------");
        System.out.println("Average more taxies needed: " + averageMoreTaxies);
    }

    public void summary(double time) {
        printStats(time);
    }
}
