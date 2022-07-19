package projekt.taxi;

import hla.rti.*;
import hla.rti.jlc.EncodingHelpers;
import hla.rti.jlc.NullFederateAmbassador;
import hla.rti.jlc.RtiFactoryFactory;
import org.portico.impl.hla13.types.DoubleTime;
import projekt.Event;

import java.util.ArrayList;

public class TaxiAmbassador extends NullFederateAmbassador {

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

    protected int receivePassengerEnteredTaxiHandle;
    protected int receiveDestinationFromPassengerHandle;
    protected int receiveDistanceHandle;
    protected int receiveTaxiStartedHandle;
    protected int sendCheckDistanceHandle;
    protected int sendTaxiStartedHandle;
    protected int sendTaxiArrivedToRegionHandle;
    protected int sendAskPassengerForDestinationHandle;
    protected double askPassengerForDestinationTime = 20.0;
    protected double checkDistanceTime = 20.0;
    protected double startTime = 30.0;
    protected double arriveToRegionTime = 30.0;

    protected ArrayList<Taxi> taxies = new ArrayList<>();
    protected ArrayList<Event> events = new ArrayList<>();

    public TaxiAmbassador(RTIambassador rtiamb) {
        this.rtiamb = rtiamb;
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
        System.out.println( "TaxiAmbassador: " + message );
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
        if( label.equals(TaxiFederate.READY_TO_RUN) )
            this.isAnnounced = true;
    }

    public void federationSynchronized( String label )
    {
        log( "Federation Synchronized: " + label );
        if( label.equals(TaxiFederate.READY_TO_RUN) )
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

    public Taxi generateTaxi() {

        Taxi taxi = new Taxi();
        addTaxiToList(taxi);

        return taxi;
    }

    public void addTaxiToList(Taxi taxi) {
        taxies.add(taxi);
    }

    public void removeTaxiFromList(Taxi taxi) {
        taxies.remove(taxi);
    }

    private Taxi getTaxiById(int id) {
        return taxies.stream().filter(t -> t.getId() == id).findFirst().get();
    }

    public void passengerEnter(int id) {
        taxies.stream().filter(t -> t.getId() == id).findFirst().get().passengerEnter();
    }

    public void passengerLeave(int id) {
        taxies.stream().filter(t -> t.getId() == id).findFirst().get().passengerLeave();
    }

    public void receivePassengerEnteredTaxi(ReceivedInteraction theInteraction, LogicalTime theTime) throws RTIexception {
        double timeStep = convertTime(theTime);

        int taxiIdInt = EncodingHelpers.decodeInt(theInteraction.getValue(0));
        int passengerIdInt = EncodingHelpers.decodeInt(theInteraction.getValue(1));

        log(federateTime + " receivePassengerEnteredTaxi: (taxiId, passengerId) -> ("+taxiIdInt+", "+passengerIdInt+")");

        sendAskPassengerForDestination(taxiIdInt, passengerIdInt, timeStep + askPassengerForDestinationTime);
    }

    public void receiveDestinationFromPassenger(ReceivedInteraction theInteraction, LogicalTime theTime) throws RTIexception {
        double timeStep = convertTime(theTime);

        int taxiIdInt = EncodingHelpers.decodeInt(theInteraction.getValue(1));
        int startRegionIdInt = EncodingHelpers.decodeInt(theInteraction.getValue(2)); //??????????????????????????????????????????????
        int endRegionIdInt = EncodingHelpers.decodeInt(theInteraction.getValue(0));

        log(federateTime + " receiveDestinationFromPassenger: (taxiId, startRegionIdInt, endRegionIdInt) -> ("+taxiIdInt+", "+startRegionIdInt+", "+endRegionIdInt+")");

        sendCheckDistance(taxiIdInt, startRegionIdInt, endRegionIdInt, timeStep + checkDistanceTime);
    }

    public void receiveDistance(ReceivedInteraction theInteraction, LogicalTime theTime) throws RTIexception {
        double timeStep = convertTime(theTime);

        double distanceDouble = EncodingHelpers.decodeDouble(theInteraction.getValue(0));
        int taxiIdInt = EncodingHelpers.decodeInt(theInteraction.getValue(1));
        int endRegionIdInt = EncodingHelpers.decodeInt(theInteraction.getValue(2));

        log(federateTime + " receiveDistance: (distanceDouble, taxiIdInt, endRegionIdInt) -> ("+distanceDouble+", "+taxiIdInt+", "+endRegionIdInt+")");

        double avgSpeed = getTaxiById(taxiIdInt).getAvgSpeed();
        double rideTime = (distanceDouble/avgSpeed) * 60.0 * 60.0; //km/(km/h) -> h -> s

        sendTaxiStarted(taxiIdInt, endRegionIdInt, rideTime, timeStep + startTime);
    }

    public void receiveTaxiStarted(ReceivedInteraction theInteraction, LogicalTime theTime) throws RTIexception {
        double timeStep = convertTime(theTime);

        int taxiIdInt = EncodingHelpers.decodeInt(theInteraction.getValue(0));
        int endRegionIdInt = EncodingHelpers.decodeInt(theInteraction.getValue(1));
        double rideTimeDouble = EncodingHelpers.decodeDouble(theInteraction.getValue(2));

        log(federateTime + " receiveTaxiStarted: (taxiId, endRegionId, rideTime) -> ("+taxiIdInt+", "+endRegionIdInt+", "+rideTimeDouble+")");

        sendTaxiArrivedToRegion(taxiIdInt, endRegionIdInt, timeStep + rideTimeDouble);
    }

    public void sendCheckDistance(int taxiIdInt, int startRegionIdInt, int endRegionIdInt, double timeStep) throws RTIexception {
        SuppliedParameters parameters =
                RtiFactoryFactory.getRtiFactory().createSuppliedParameters();

        byte[] taxiId = EncodingHelpers.encodeInt(taxiIdInt);
        byte[] startRegionId = EncodingHelpers.encodeInt(startRegionIdInt);
        byte[] endRegionId = EncodingHelpers.encodeInt(endRegionIdInt);

        int taxiIdHandle = rtiamb.getParameterHandle("taxiId", sendCheckDistanceHandle);
        int startRegionIdHandle = rtiamb.getParameterHandle("startRegionId", sendCheckDistanceHandle);
        int endRegionIdHandle = rtiamb.getParameterHandle("endRegionId", sendCheckDistanceHandle);

        parameters.add(taxiIdHandle, taxiId);
        parameters.add(startRegionIdHandle, startRegionId);
        parameters.add(endRegionIdHandle, endRegionId);

        LogicalTime time = convertTime( timeStep );
        log(federateTime + " sendCheckDistance: (taxiId, startRegionId, endRegionId) -> (" +taxiIdInt+", "+ startRegionIdInt + ", "+endRegionIdInt+")");

        rtiamb.sendInteraction( sendCheckDistanceHandle, parameters, "tag".getBytes(), time);
    }

    public void sendTaxiStarted(int taxiIdInt, int endRegionIdInt, double rideTimeDouble, double timeStep) throws RTIexception {
        SuppliedParameters parameters =
                RtiFactoryFactory.getRtiFactory().createSuppliedParameters();

        byte[] taxiId = EncodingHelpers.encodeInt(taxiIdInt);
        byte[] endRegionId = EncodingHelpers.encodeInt(endRegionIdInt);
        byte[] rideTime = EncodingHelpers.encodeDouble(rideTimeDouble);

        int taxiIdHandle = rtiamb.getParameterHandle("taxiId", sendTaxiStartedHandle);
        int endRegionIdHandle = rtiamb.getParameterHandle("endRegionId", sendTaxiStartedHandle);
        int rideTimeHandle = rtiamb.getParameterHandle("rideTime", sendTaxiStartedHandle);

        parameters.add(taxiIdHandle, taxiId);
        parameters.add(endRegionIdHandle, endRegionId);
        parameters.add(rideTimeHandle, rideTime);

        LogicalTime time = convertTime( timeStep );
        log(federateTime + " sendTaxiStarted: (taxiId, endRegionId, rideTime) -> (" +taxiIdInt+", "+endRegionIdInt+", "+rideTimeDouble+")");

        sendTaxiArrivedToRegion(taxiIdInt, endRegionIdInt, timeStep + rideTimeDouble);
        //rtiamb.sendInteraction( sendTaxiStartedHandle, parameters, "tag".getBytes(), time);
    }

    public void sendTaxiArrivedToRegion(int taxiIdInt, int endRegionIdInt, double timeStep) throws RTIexception {
        SuppliedParameters parameters =
                RtiFactoryFactory.getRtiFactory().createSuppliedParameters();

        byte[] taxiId = EncodingHelpers.encodeInt(taxiIdInt);
        byte[] endRegionId = EncodingHelpers.encodeInt(endRegionIdInt);

        int taxiIdHandle = rtiamb.getParameterHandle("taxiId", sendTaxiArrivedToRegionHandle);;
        int endRegionIdHandle = rtiamb.getParameterHandle("endRegionId", sendTaxiArrivedToRegionHandle);

        parameters.add(taxiIdHandle, taxiId);
        parameters.add(endRegionIdHandle, endRegionId);

        LogicalTime time = convertTime( timeStep );
        log(federateTime + " sendTaxiArrivedToRegion: (taxiId, endRegionId) -> (" +taxiIdInt+", "+endRegionIdInt+")");

        rtiamb.sendInteraction( sendTaxiArrivedToRegionHandle, parameters, "tag".getBytes(), time);
    }

    public void sendAskPassengerForDestination(int taxiIdInt, int passengerIdInt, double timeStep) throws RTIexception {
        SuppliedParameters parameters =
                RtiFactoryFactory.getRtiFactory().createSuppliedParameters();

        byte[] taxiId = EncodingHelpers.encodeInt(taxiIdInt);
        byte[] passengerId = EncodingHelpers.encodeInt(passengerIdInt);

        int taxiIdHandle = rtiamb.getParameterHandle("taxiId", sendAskPassengerForDestinationHandle);
        int passengerIdHandle = rtiamb.getParameterHandle("passengerId", sendAskPassengerForDestinationHandle);

        parameters.add(taxiIdHandle, taxiId);
        parameters.add(passengerIdHandle, passengerId);

        LogicalTime time = convertTime( timeStep );
        log(federateTime + " sendAskPassengerForDestination: (taxiId, passengerId) -> (" +taxiIdInt+", "+ passengerIdInt+")");

        rtiamb.sendInteraction( sendAskPassengerForDestinationHandle, parameters, "tag".getBytes(), time);
    }

}
