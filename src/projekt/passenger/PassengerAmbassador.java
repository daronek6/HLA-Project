package projekt.passenger;

import hla.rti.*;
import hla.rti.jlc.EncodingHelpers;
import hla.rti.jlc.NullFederateAmbassador;
import hla.rti.jlc.RtiFactoryFactory;
import org.portico.impl.hla13.types.DoubleTime;
import projekt.Event;

import java.util.ArrayList;

public class PassengerAmbassador extends NullFederateAmbassador {

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

    protected int receiveAskPassengerForDestinationHandle;
    protected int sendDestinationToTaxiHandle;
    protected int sendPassengerJoinQueueHandle;
    protected double passengerJoinQueueTime = 40.0;
    protected double askPassengerForDestinationTime = 20.0;

    protected ArrayList<Passenger> passengers = new ArrayList<>();
    protected ArrayList<Event> events = new ArrayList<>();
    public PassengerAmbassador(RTIambassador rtiamb) {
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
        System.out.println( "PassengerAmbassador: " + message );
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
        if( label.equals(PassengerFederate.READY_TO_RUN) )
            this.isAnnounced = true;
    }

    public void federationSynchronized( String label )
    {
        log( "Federation Synchronized: " + label );
        if( label.equals(PassengerFederate.READY_TO_RUN) )
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

    public void generatePassenger() throws RTIexception{
        Passenger passenger = new Passenger();
        passengers.add(passenger);

        sendPassengerJoinQueue(passenger, federateTime + passengerJoinQueueTime);
    }

    public Passenger getPassengerById(int id) {
        Passenger passenger = passengers.stream().filter(p -> p.getId() == id).findFirst().get();

        return passenger;
    }

    public void receiveAskPassengerForDestination(ReceivedInteraction theInteraction, LogicalTime theTime) throws RTIexception {
        double timeStep = convertTime(theTime);
        timeStep += askPassengerForDestinationTime;

        int taxiIdInt = EncodingHelpers.decodeInt(theInteraction.getValue(0));
        int passengerIdInt = EncodingHelpers.decodeInt(theInteraction.getValue(1));

        log(federateTime + " receiveAskPassengerForDestination: (taxiId, passengerId) -> ("+taxiIdInt+", "+passengerIdInt+")");

        sendDestinationToTaxi(passengerIdInt, taxiIdInt, timeStep);
    }

    public void sendDestinationToTaxi(int passengerIdInt, int taxiIdInt, double timeStep) throws RTIexception {
        Passenger passenger = getPassengerById(passengerIdInt);

        SuppliedParameters parameters =
                RtiFactoryFactory.getRtiFactory().createSuppliedParameters();

        int startRegionIdInt = passenger.getStartRegionId();
        int endRegionIdInt = passenger.getEndRegionId();

        byte[] taxiId = EncodingHelpers.encodeInt(taxiIdInt);
        byte[] startRegionId = EncodingHelpers.encodeInt(startRegionIdInt);
        byte[] endRegionId = EncodingHelpers.encodeInt(endRegionIdInt);

        int taxiIdHandle = rtiamb.getParameterHandle("taxiId", sendDestinationToTaxiHandle);
        int startRegionIdHandle = rtiamb.getParameterHandle("startRegionId", sendDestinationToTaxiHandle);
        int endRegionIdHandle = rtiamb.getParameterHandle("endRegionId", sendDestinationToTaxiHandle);

        parameters.add(taxiIdHandle, taxiId);
        parameters.add(startRegionIdHandle, startRegionId);
        parameters.add(endRegionIdHandle, endRegionId);

        LogicalTime time = convertTime( timeStep );
        log(federateTime + " sendDestinationToTaxi: (taxiId, startRegionId, endRegionId) -> ("+taxiIdInt +", "+startRegionIdInt+", "+endRegionIdInt+")");

        rtiamb.sendInteraction( sendDestinationToTaxiHandle, parameters, "tag".getBytes(), time);

    }

    public void sendPassengerJoinQueue(Passenger passenger, double timeStep) throws RTIexception{
        SuppliedParameters parameters =
                RtiFactoryFactory.getRtiFactory().createSuppliedParameters();

        int startRegionIdInt = passenger.getStartRegionId();
        int passengerIdInt = passenger.getId();

        byte[] startRegionId = EncodingHelpers.encodeInt(startRegionIdInt);
        byte[] passengerId = EncodingHelpers.encodeInt(passengerIdInt);

        int startRegionIdHandle = rtiamb.getParameterHandle("startRegionId", sendPassengerJoinQueueHandle);
        int passengerIdHandle = rtiamb.getParameterHandle("passengerId", sendPassengerJoinQueueHandle);

        parameters.add(startRegionIdHandle, startRegionId);
        parameters.add(passengerIdHandle, passengerId);

        LogicalTime time = convertTime( timeStep );
        log(federateTime + " sendPassengerJoinQueue: (passengerId, startRegionId) -> (" +passengerIdInt+", "+startRegionIdInt+")");

        rtiamb.sendInteraction( sendPassengerJoinQueueHandle, parameters, "tag".getBytes(), time);
    }
}
