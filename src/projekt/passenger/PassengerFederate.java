package projekt.passenger;

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
import java.util.Random;

public class PassengerFederate {

    public static final String READY_TO_RUN = "ReadyToRun";

    private RTIambassador rtiamb;
    private PassengerAmbassador fedamb;
    private String federateName;
    private final double TIME_STEP = 18.0;
    private final double FINISH_TIME = 86400.0;
    private final double GENERATE_PASSENGER_TIME_MIN = 5.0*60.0;
    private final double GENERATE_PASSENGER_TIME_MAX = 20.0*60.0;
    private double generateNextPassengerTime = 0.0;
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

        fedamb = new PassengerAmbassador(rtiamb);
        federateName = "PassengerFederate";
        rtiamb.joinFederationExecution( federateName, "ProjectFederation", fedamb);
        log( "Joined Federation as PassengerFederate");

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
            tryToGenerateNewPassenger();

            double timeToAdvance = fedamb.federateTime + TIME_STEP;
            advanceTime(timeToAdvance);

            if(fedamb.events.size() > 0) {
                fedamb.events.sort(new Event.EventComparator());
                for(Event event : fedamb.events) {
                    fedamb.federateTime = event.getTime();
                    int interactionClass = event.getInteractionClass();

                    try {
                        if(interactionClass == fedamb.receiveAskPassengerForDestinationHandle) {
                            fedamb.receiveAskPassengerForDestination(event.getInteraction(), convertTime(event.getTime()));
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

        int destToTaxiHandle = rtiamb.getInteractionClassHandle("InteractionRoot.sendDestinationToTaxi");
        rtiamb.publishInteractionClass(destToTaxiHandle);
        fedamb.sendDestinationToTaxiHandle = destToTaxiHandle;

        int passengerJoinQueue = rtiamb.getInteractionClassHandle("InteractionRoot.sendPassengerJoinQueue");
        rtiamb.publishInteractionClass(passengerJoinQueue);
        fedamb.sendPassengerJoinQueueHandle = passengerJoinQueue;

        simulationEndedHandle = rtiamb.getInteractionClassHandle("InteractionRoot.simulationEnded");
        rtiamb.publishInteractionClass(simulationEndedHandle);

        int askPassengerForDestinationHandle = rtiamb.getInteractionClassHandle("InteractionRoot.sendAskPassengerForDestination");
        rtiamb.subscribeInteractionClass(askPassengerForDestinationHandle);
        fedamb.receiveAskPassengerForDestinationHandle = askPassengerForDestinationHandle;
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
        System.out.println( "PassengerFederate   : " + message );
    }

    public static void main(String[] args) {
        try {
            new PassengerFederate().runFederate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void tryToGenerateNewPassenger() throws RTIexception{
        if(fedamb.federateTime >= generateNextPassengerTime) {
            fedamb.generatePassenger();

            generateNextPassengerTime = fedamb.federateTime +
                    new Random().nextDouble()*(GENERATE_PASSENGER_TIME_MAX - GENERATE_PASSENGER_TIME_MIN) + GENERATE_PASSENGER_TIME_MIN;
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
