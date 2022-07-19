package projekt.region;

import hla.rti.*;
import hla.rti.jlc.EncodingHelpers;
import hla.rti.jlc.NullFederateAmbassador;
import hla.rti.jlc.RtiFactoryFactory;
import org.portico.impl.hla13.types.DoubleTime;
import projekt.Event;

import java.util.ArrayList;

public class RegionAmbassador extends NullFederateAmbassador {

    //----------------------------------------------------------
    //                    STATIC VARIABLES
    //----------------------------------------------------------
    public static final int NUM_OF_REGIONS = 6;
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

    protected int receiveTaxiArrivedToRegionHandle;
    protected int receivePassengerJoinQueueHandle;
    protected int sendPassengerEnteredTaxiHandle;
    protected double passengerEnterTaxiTime = 30.0;

    protected ArrayList<Region> regions = new ArrayList<>();
    protected ArrayList<Event> events = new ArrayList<>();

    public RegionAmbassador(RTIambassador rtiamb) {
        this.rtiamb = rtiamb;
        generateRegions();
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
        System.out.println( "RegionAmbassador: " + message );
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
        if( label.equals(RegionFederate.READY_TO_RUN) )
            this.isAnnounced = true;
    }

    public void federationSynchronized( String label )
    {
        log( "Federation Synchronized: " + label );
        if( label.equals(RegionFederate.READY_TO_RUN) )
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


    private void generateRegions() {
        for(int i=0;i<NUM_OF_REGIONS;i++) {
            regions.add(new Region(i));
        }
    }

    public Region getRegionById(int id) {
        Region region = regions.stream().filter(r -> r.getId() == id).findFirst().get();

        return region;
    }

    public void addPassengerToQueue(int regionId, int passengerId) {
        getRegionById(regionId).addPassengerToQueue(passengerId);

        log(federateTime + " addPassengerToQueue: (regionId, taxiIdI) -> ("+regionId+", "+passengerId+")");
    }

    public void addTaxiToQueue(int regionId, int taxiId) {
        getRegionById(regionId).addTaxiToQueue(taxiId);

        log(federateTime + " addTaxiToQueue: (regionId, taxiIdI) -> ("+regionId+", "+taxiId+")");
    }

    public void receiveTaxiArrivedToRegion(ReceivedInteraction theInteraction, LogicalTime theTime) throws ArrayIndexOutOfBounds {
        double timeStep = convertTime(theTime);

        int regionIdInt = EncodingHelpers.decodeInt(theInteraction.getValue(0));
        int taxiIdInt = EncodingHelpers.decodeInt(theInteraction.getValue(1));

        log(federateTime + " receiveTaxiArrivedToRegion: (regionId, taxiIdI) -> ("+regionIdInt+", "+taxiIdInt+")");

        addTaxiToQueue(regionIdInt, taxiIdInt);
    }

    public void receivePassengerJoinQueue(ReceivedInteraction theInteraction, LogicalTime theTime) throws ArrayIndexOutOfBounds {
        double timeStep = convertTime(theTime);

        int passengerIdInt = EncodingHelpers.decodeInt(theInteraction.getValue(0));
        int startRegionIdInt = EncodingHelpers.decodeInt(theInteraction.getValue(1));

        log(federateTime + " receivePassengerJoinQueue: (passengerId, startRegionId) -> ("+passengerIdInt+", "+startRegionIdInt+")");

        addPassengerToQueue(startRegionIdInt, passengerIdInt);
    }


    public void sendPassengerEnteredTaxi(int regionId, double timeStep) throws RTIexception {
        Region region = getRegionById(regionId);
        SuppliedParameters parameters =
                RtiFactoryFactory.getRtiFactory().createSuppliedParameters();

        int taxiIdInt = region.getTaxiFromQueue();
        int passengerIdInt = region.getPassengerFromQueue();

        byte[] taxiId = EncodingHelpers.encodeInt(taxiIdInt);
        byte[] passengerId = EncodingHelpers.encodeInt(passengerIdInt);
        byte[] reg = EncodingHelpers.encodeInt(regionId);

        int taxiIdHandle = rtiamb.getParameterHandle("taxiId", sendPassengerEnteredTaxiHandle);
        int passengerIdHandle = rtiamb.getParameterHandle("passengerId", sendPassengerEnteredTaxiHandle);
        int regHandle = rtiamb.getParameterHandle("regionId", sendPassengerEnteredTaxiHandle);

        parameters.add(taxiIdHandle, taxiId);
        parameters.add(passengerIdHandle, passengerId);
        parameters.add(regHandle, reg);

        LogicalTime time = convertTime( timeStep );
        log(federateTime + " sendPassengerEnteredTaxi: (taxiId, passengerId, regionId) -> (" +taxiIdInt+", "+passengerIdInt+", "+regionId+")");

        rtiamb.sendInteraction( sendPassengerEnteredTaxiHandle, parameters, "tag".getBytes(), time);
    }
}
