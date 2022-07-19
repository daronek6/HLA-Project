package projekt.statistics;

import hla.rti.*;
import hla.rti.jlc.EncodingHelpers;
import hla.rti.jlc.NullFederateAmbassador;

import org.portico.impl.hla13.types.DoubleTime;
import projekt.Event;


import java.util.ArrayList;

public class StatisticsAmbassador extends NullFederateAmbassador {

    //----------------------------------------------------------
    //                    STATIC VARIABLES
    //----------------------------------------------------------

    //----------------------------------------------------------
    //                   INSTANCE VARIABLES
    //----------------------------------------------------------
    private RTIambassador rtiamb;

    // these variables are accessible in the package
    protected double federateTime        = 0.0;
    protected double grantedTime         = 0.0;
    protected double federateLookahead   = 1.0;

    protected boolean isRegulating       = false;
    protected boolean isConstrained      = false;
    protected boolean isAdvancing        = false;

    protected boolean isAnnounced        = false;
    protected boolean isReadyToRun       = false;

    protected boolean running 			 = true;

    protected boolean simulationEnded = false;
    protected int federatesEnded = 0;

    protected int receiveTaxiArrivedHandle;
    protected int receivePassengerJoinedQueueHandle;
    protected int receivePassengerEnteredTaxiHandle;
    protected int receiveSimulationEndedHandle;

    protected Statistics statistics;
    protected ArrayList<Event> events = new ArrayList<>();

    public StatisticsAmbassador(RTIambassador rtiamb) {
        this.rtiamb = rtiamb;
        statistics = new Statistics();
    }

    private double convertTime( LogicalTime logicalTime )
    {
        // PORTICO SPECIFIC!!
        return ((DoubleTime)logicalTime).getTime();
    }
    private LogicalTime convertTime( double time )
    {
        // PORTICO SPECIFIC!!
        return new DoubleTime( time );
    }

    private void log( String message )
    {
        System.out.println( "StatisticsAmbassador: " + message );
    }

    public void synchronizationPointRegistrationFailed( String label )
    {
        log( "Failed to register sync point: " + label );
    }

    public void synchronizationPointRegistrationSucceeded( String label )
    {
        log( "Successfully registered sync point: " + label );
    }

    public void announceSynchronizationPoint( String label, byte[] tag )
    {
        log( "Synchronization point announced: " + label );
        if( label.equals(StatisticsFederate.READY_TO_RUN) )
            this.isAnnounced = true;
    }

    public void federationSynchronized( String label )
    {
        log( "Federation Synchronized: " + label );
        if( label.equals(StatisticsFederate.READY_TO_RUN) )
            this.isReadyToRun = true;
    }

    /**
     * The RTI has informed us that time regulation is now enabled.
     */
    public void timeRegulationEnabled( LogicalTime theFederateTime )
    {
        this.federateTime = convertTime( theFederateTime );
        this.isRegulating = true;
    }

    public void timeConstrainedEnabled( LogicalTime theFederateTime )
    {
        this.federateTime = convertTime( theFederateTime );
        this.isConstrained = true;
    }

    public void timeAdvanceGrant( LogicalTime theTime )
    {
        this.grantedTime = convertTime( theTime );
        this.isAdvancing = false;
    }


    public void receiveInteraction( int interactionClass,
                                    ReceivedInteraction theInteraction,
                                    byte[] tag )
    {
        // just pass it on to the other method for printing purposes
        // passing null as the time will let the other method know it
        // it from us, not from the RTI
        receiveInteraction(interactionClass, theInteraction, tag, null, null);
    }

    public void receiveInteraction( int interactionClass,
                                    ReceivedInteraction theInteraction,
                                    byte[] tag,
                                    LogicalTime theTime,
                                    EventRetractionHandle eventRetractionHandle )
    {
        events.add(new Event(interactionClass, theInteraction, convertTime(theTime)));
    }

    public void summary(double time) {
        statistics.summary(time);
    }

    public void receivePassengerJoinQueue(ReceivedInteraction theInteraction, LogicalTime theTime) throws ArrayIndexOutOfBounds {
        double timeStep = convertTime(theTime);

        int passengerIdInt = EncodingHelpers.decodeInt(theInteraction.getValue(0));
        int startRegionIdInt = EncodingHelpers.decodeInt(theInteraction.getValue(1));

        //log(federateTime + " receivePassengerJoinQueue: (passengerId, startRegionId) -> ("+passengerIdInt+", "+startRegionIdInt+")");

        statistics.addPassengerToQueue(startRegionIdInt, timeStep);
        statistics.printStats(timeStep);
    }

    public void receiveTaxiArrivedToRegion(ReceivedInteraction theInteraction, LogicalTime theTime) throws ArrayIndexOutOfBounds {
        double timeStep = convertTime(theTime);

        int regionIdInt = EncodingHelpers.decodeInt(theInteraction.getValue(0));
        int taxiIdInt = EncodingHelpers.decodeInt(theInteraction.getValue(1));

        //log(federateTime + " receiveTaxiArrivedToRegion: (regionId, taxiIdI) -> ("+regionIdInt+", "+taxiIdInt+")");

        if(timeStep >= 60.0) {
            statistics.addCompletedRide(timeStep);
        }
        statistics.addTaxiToQueue(regionIdInt, timeStep);
        statistics.printStats(timeStep);
    }

    public void receivePassengerEnteredTaxi(ReceivedInteraction theInteraction, LogicalTime theTime) throws RTIexception {
        double timeStep = convertTime(theTime);

        int taxiIdInt = EncodingHelpers.decodeInt(theInteraction.getValue(0));
        int passengerIdInt = EncodingHelpers.decodeInt(theInteraction.getValue(1));
        int regionIdInt = EncodingHelpers.decodeInt(theInteraction.getValue(2));

        //log(federateTime + " receivePassengerEnteredTaxi: (taxiId, passengerId) -> ("+taxiIdInt+", "+passengerIdInt+")");

        statistics.removePassengerFromQueue(regionIdInt, timeStep);
        statistics.removeTaxiFromQueue(regionIdInt, timeStep);
        statistics.printStats(timeStep);
    }

    public void receiveSimulationEnded(ReceivedInteraction theInteraction, LogicalTime theTime) throws RTIexception {
        double timeStep = convertTime(theTime);

        String federate = EncodingHelpers.decodeString(theInteraction.getValue(0));

        log(timeStep + " Federate " + federate + " stopped!");
        federatesEnded++;

        if(federatesEnded >= 4)
            simulationEnded = true;
    }
}
