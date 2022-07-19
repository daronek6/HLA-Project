package projekt.statistics;

import hla.rti.*;
import hla.rti.jlc.RtiFactoryFactory;
import org.portico.impl.hla13.types.DoubleTime;
import org.portico.impl.hla13.types.DoubleTimeInterval;
import projekt.Event;


import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;

public class StatisticsFederate {

    public static final String READY_TO_RUN = "ReadyToRun";

    private RTIambassador rtiamb;
    private StatisticsAmbassador fedamb;
    private String federateName;
    private final double TIME_STEP = 60.0;

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

        fedamb = new StatisticsAmbassador(rtiamb);
        federateName = "StatisticsFederate";
        rtiamb.joinFederationExecution( federateName, "ProjectFederation", fedamb );
        log( "Joined Federation as RouteFederate");

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
            double timeToAdvance = fedamb.federateTime + TIME_STEP;
            advanceTime(timeToAdvance);

            if(fedamb.events.size() > 0) {
                fedamb.events.sort(new Event.EventComparator());
                for(Event event : fedamb.events) {
                    fedamb.federateTime = event.getTime();
                    int interactionClass = event.getInteractionClass();

                    try {
                        if(interactionClass == fedamb.receivePassengerJoinedQueueHandle) {
                            fedamb.receivePassengerJoinQueue(event.getInteraction(), convertTime(event.getTime()));
                        }
                        else if(interactionClass == fedamb.receiveTaxiArrivedHandle) {
                            fedamb.receiveTaxiArrivedToRegion(event.getInteraction(), convertTime(event.getTime()));
                        }
                        else if(interactionClass == fedamb.receivePassengerEnteredTaxiHandle) {
                            fedamb.receivePassengerEnteredTaxi(event.getInteraction(), convertTime(event.getTime()));
                        }
                        else if(interactionClass == fedamb.receiveSimulationEndedHandle) {
                            fedamb.receiveSimulationEnded(event.getInteraction(), convertTime(event.getTime()));
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

        int taxiArrivedHandle = rtiamb.getInteractionClassHandle("InteractionRoot.sendTaxiArrivedToRegion");
        rtiamb.subscribeInteractionClass(taxiArrivedHandle);
        fedamb.receiveTaxiArrivedHandle = taxiArrivedHandle;

        int passengerJoinedQueueHandle = rtiamb.getInteractionClassHandle("InteractionRoot.sendPassengerJoinQueue");
        rtiamb.subscribeInteractionClass(passengerJoinedQueueHandle);
        fedamb.receivePassengerJoinedQueueHandle = passengerJoinedQueueHandle;

        int passengerEnteredTaxiHandle = rtiamb.getInteractionClassHandle("InteractionRoot.sendPassengerEnteredTaxi");
        rtiamb.subscribeInteractionClass(passengerEnteredTaxiHandle);
        fedamb.receivePassengerEnteredTaxiHandle = passengerEnteredTaxiHandle;

        int simulationEndedHandle = rtiamb.getInteractionClassHandle("InteractionRoot.simulationEnded");
        rtiamb.subscribeInteractionClass(simulationEndedHandle);
        fedamb.receiveSimulationEndedHandle = simulationEndedHandle;
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
        System.out.println( "StatisticsFederate   : " + message );
    }

    public static void main(String[] args) {
        try {
            new StatisticsFederate().runFederate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void tryToEndSimulation() throws RTIexception {
        if(fedamb.simulationEnded) {
            fedamb.running = false;

            fedamb.summary(fedamb.federateTime);
        }
    }
}
