package projekt.region;

import hla.rti.*;
import hla.rti.jlc.EncodingHelpers;
import hla.rti.jlc.RtiFactoryFactory;
import org.portico.impl.hla13.types.DoubleTime;
import org.portico.impl.hla13.types.DoubleTimeInterval;
import projekt.Event;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;

public class RegionFederate {

    public static final String READY_TO_RUN = "ReadyToRun";

    private RTIambassador rtiamb;
    private RegionAmbassador fedamb;
    private String federateName;
    private final double TIME_STEP = 28.0;
    private final double FINISH_TIME = 86400.0;
    private int simulationEndedHandle;
    public void runFederate() throws Exception {

        rtiamb = RtiFactoryFactory.getRtiFactory().createRtiAmbassador();

        try
        {
            File fom = new File( "projekt.fed" );
            rtiamb.createFederationExecution( "ProjectFederation",
                    fom.toURI().toURL() );
            log( "Created Federation" );
        }
        catch( FederationExecutionAlreadyExists exists )
        {
            log( "Didn't create federation, it already existed" );
        }
        catch( MalformedURLException urle )
        {
            log( "Exception processing fom: " + urle.getMessage() );
            urle.printStackTrace();
            return;
        }

        fedamb = new RegionAmbassador(rtiamb);
        federateName = "RegionFederate";
        rtiamb.joinFederationExecution( federateName, "ProjectFederation", fedamb );
        log( "Joined Federation as RegionFederate");

        rtiamb.registerFederationSynchronizationPoint( READY_TO_RUN, null );

        while( fedamb.isAnnounced == false )
        {
            rtiamb.tick();
        }

        waitForUser();

        rtiamb.synchronizationPointAchieved( READY_TO_RUN );
        log( "Achieved sync point: " +READY_TO_RUN+ ", waiting for federation..." );
        while( fedamb.isReadyToRun == false )
        {
            rtiamb.tick();
        }

        enableTimePolicy();

        publishAndSubscribe();

        while (fedamb.running) {
            tryPassengerEnterTaxi();

            double timeToAdvance = fedamb.federateTime + TIME_STEP;
            advanceTime(timeToAdvance);


            if(fedamb.events.size() > 0) {
                fedamb.events.sort(new Event.EventComparator());
                for(Event event : fedamb.events) {
                    fedamb.federateTime = event.getTime();
                    int interactionClass = event.getInteractionClass();

                    try {
                        if(interactionClass == fedamb.receivePassengerJoinQueueHandle) {
                            fedamb.receivePassengerJoinQueue(event.getInteraction(), convertTime(event.getTime()));
                        }
                        else if(interactionClass == fedamb.receiveTaxiArrivedToRegionHandle) {
                            log("receiveTaxiArrivedToRegionHandle Event: " + fedamb.federateTime + ", " + event.getTime());
                            fedamb.receiveTaxiArrivedToRegion(event.getInteraction(), convertTime(event.getTime()));
                        }
                    }
                    catch (RTIexception rtie) {
                        log(rtie.getMessage());
                    }
                }
                fedamb.events.clear();
            }

            if(fedamb.grantedTime == timeToAdvance) {
                timeToAdvance += fedamb.federateLookahead;
                fedamb.federateTime = timeToAdvance;
            }

            tryToEndSimulation();

            rtiamb.tick();
        }

    }

    private void waitForUser()
    {
        log( " >>>>>>>>>> Press Enter to Continue <<<<<<<<<<" );
        BufferedReader reader = new BufferedReader( new InputStreamReader(System.in) );
        try
        {
            reader.readLine();
        }
        catch( Exception e )
        {
            log( "Error while waiting for user input: " + e.getMessage() );
            e.printStackTrace();
        }
    }

    private void advanceTime( double timeToAdvance ) throws RTIexception {
        fedamb.isAdvancing = true;
        LogicalTime newTime = convertTime( timeToAdvance );
        rtiamb.timeAdvanceRequest( newTime );

        while( fedamb.isAdvancing )
        {
            rtiamb.tick();
        }
    }

    private void publishAndSubscribe() throws RTIexception {

        int passengerEnteredTaxiHandle = rtiamb.getInteractionClassHandle("InteractionRoot.sendPassengerEnteredTaxi");
        rtiamb.publishInteractionClass(passengerEnteredTaxiHandle);
        fedamb.sendPassengerEnteredTaxiHandle = passengerEnteredTaxiHandle;

        simulationEndedHandle = rtiamb.getInteractionClassHandle("InteractionRoot.simulationEnded");
        rtiamb.publishInteractionClass(simulationEndedHandle);

        int passengerJoinQueueHandle = rtiamb.getInteractionClassHandle("InteractionRoot.sendPassengerJoinQueue");
        rtiamb.subscribeInteractionClass(passengerJoinQueueHandle);
        fedamb.receivePassengerJoinQueueHandle = passengerJoinQueueHandle;

        int taxiArrivedToRegionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.sendTaxiArrivedToRegion");
        rtiamb.subscribeInteractionClass(taxiArrivedToRegionHandle);
        fedamb.receiveTaxiArrivedToRegionHandle = taxiArrivedToRegionHandle;

    }

    private void enableTimePolicy() throws RTIexception
    {
        LogicalTime currentTime = convertTime( fedamb.federateTime );
        LogicalTimeInterval lookahead = convertInterval( fedamb.federateLookahead );

        this.rtiamb.enableTimeRegulation( currentTime, lookahead );

        while( fedamb.isRegulating == false )
        {
            rtiamb.tick();
        }

        this.rtiamb.enableTimeConstrained();

        while( fedamb.isConstrained == false )
        {
            rtiamb.tick();
        }
    }

    private LogicalTime convertTime( double time )
    {
        // PORTICO SPECIFIC!!
        return new DoubleTime( time );
    }

    /**
     * Same as for {@link #convertTime(double)}
     */
    private LogicalTimeInterval convertInterval( double time )
    {
        // PORTICO SPECIFIC!!
        return new DoubleTimeInterval( time );
    }

    private void log( String message )
    {
        System.out.println( "RegionFederate   : " + message );
    }

    public static void main(String[] args) {
        try {
            new RegionFederate().runFederate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void tryPassengerEnterTaxi() throws RTIexception {
        for(Region region : fedamb.regions) {
            if(region.getPassengerQueueLength() >= 1 && region.getTaxiesQueueLength() >= 1)
            {
                fedamb.sendPassengerEnteredTaxi(region.getId(), fedamb.federateTime + fedamb.passengerEnterTaxiTime);
            }
        }
    }

    private void tryToEndSimulation() throws RTIexception {
        if(fedamb.federateTime >= FINISH_TIME) {
            SuppliedParameters parameters =
                    RtiFactoryFactory.getRtiFactory().createSuppliedParameters();

            byte[] federate = EncodingHelpers.encodeString(federateName);

            int federateHandle = rtiamb.getParameterHandle("federate", simulationEndedHandle);

            parameters.add(federateHandle,federate);
            rtiamb.sendInteraction(simulationEndedHandle, parameters, "tag".getBytes(), convertTime(fedamb.federateTime));

            fedamb.running = false;

            log(fedamb.federateTime + " " + federateName + " Stopped!");
        }
    }
}
