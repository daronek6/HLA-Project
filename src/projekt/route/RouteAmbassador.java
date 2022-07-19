package projekt.route;

import hla.rti.*;
import hla.rti.jlc.EncodingHelpers;
import hla.rti.jlc.NullFederateAmbassador;
import hla.rti.jlc.RtiFactoryFactory;
import org.portico.impl.hla13.types.DoubleTime;
import projekt.Event;
import projekt.region.RegionAmbassador;

import java.util.ArrayList;

public class RouteAmbassador extends NullFederateAmbassador {

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

    protected int receiveCheckDistanceHandle;
    protected int sendDistanceHandle;
    protected double checkDistanceTime = 25.0;

    protected ArrayList<Route> routes = new ArrayList<>();
    protected ArrayList<Event> events = new ArrayList<>();

    public RouteAmbassador(RTIambassador rtiamb) {
        this.rtiamb = rtiamb;
        generateRoutes();
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
        System.out.println( "RouteAmbassador: " + message );
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
        if( label.equals(RouteFederate.READY_TO_RUN) )
            this.isAnnounced = true;
    }

    public void federationSynchronized( String label )
    {
        log( "Federation Synchronized: " + label );
        if( label.equals(RouteFederate.READY_TO_RUN) )
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

    private void generateRoutes() {
        for(int i = 0; i< RegionAmbassador.NUM_OF_REGIONS; i++) {
            for(int j = i+1; j<RegionAmbassador.NUM_OF_REGIONS; j++) {
                routes.add(new Route(i, j));
            }
        }
    }

    public double getRouteDistance(int startRegionId, int endRegionId) {
        double distance = 0;
        distance = routes.stream().filter(r -> r.sameRoute(startRegionId, endRegionId)).findFirst().get().getDistance();
        return distance;
    }

    public void receiveCheckDistance(ReceivedInteraction theInteraction, LogicalTime theTime) throws RTIexception {
        double timeStep = convertTime(theTime);

        int taxiIdInt = EncodingHelpers.decodeInt(theInteraction.getValue(0));
        int startRegionIdInt = EncodingHelpers.decodeInt(theInteraction.getValue(1));
        int endRegionIdInt = EncodingHelpers.decodeInt(theInteraction.getValue(2));

        log(federateTime + " receiveCheckDistance: (taxiId, startRegionId, endRegionId) -> ("+taxiIdInt+", "+startRegionIdInt+ ", "+endRegionIdInt+")");

        double distanceDouble = getRouteDistance(startRegionIdInt, endRegionIdInt);

        sendDistance(taxiIdInt, distanceDouble, endRegionIdInt, timeStep + checkDistanceTime);
    }

    public void sendDistance(int taxiIdInt, double distanceDouble, int endRegionIdInt, double timeStep) throws RTIexception {
        SuppliedParameters parameters =
                RtiFactoryFactory.getRtiFactory().createSuppliedParameters();

        byte[] taxiId = EncodingHelpers.encodeInt(taxiIdInt);
        byte[] distance = EncodingHelpers.encodeDouble(distanceDouble);
        byte[] endRegionId = EncodingHelpers.encodeInt(endRegionIdInt);

        int taxiIdHandle = rtiamb.getParameterHandle("taxiId", sendDistanceHandle);
        int distanceHandle = rtiamb.getParameterHandle("distance", sendDistanceHandle);
        int endRegionIdHandle = rtiamb.getParameterHandle("endRegionId", sendDistanceHandle);

        parameters.add(taxiIdHandle, taxiId);
        parameters.add(distanceHandle, distance);
        parameters.add(endRegionIdHandle, endRegionId);

        LogicalTime time = convertTime( timeStep );
        log(federateTime + " sendDistance: (distance, taxiId, endRegionId) -> (" +distanceDouble+", "+taxiIdInt+","+endRegionIdInt+")");

        rtiamb.sendInteraction( sendDistanceHandle, parameters, "tag".getBytes(), time);
    }
}
