package projekt.taxi;

import hla.rti.*;
import hla.rti.jlc.EncodingHelpers;
import hla.rti.jlc.RtiFactoryFactory;
import org.portico.impl.hla13.types.DoubleTime;
import org.portico.impl.hla13.types.DoubleTimeInterval;
import projekt.Event;
import projekt.region.RegionAmbassador;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.Random;

public class TaxiFederate {

    public static final String READY_TO_RUN = "ReadyToRun";

    private RTIambassador rtiamb;
    private TaxiAmbassador fedamb;
    private String federateName;
    private final double TIME_STEP = 18.0;
    private final double FINISH_TIME = 86400.0;
    private final int GENERATE_NUM_OF_TAXIES = 15;
    private boolean generateStartingTaxies = true;
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

        fedamb = new TaxiAmbassador(rtiamb);
        federateName = "TaxiFederate";
        rtiamb.joinFederationExecution( federateName, "ProjectFederation", fedamb );
        log( "Joined Federation as TaxiFederate");

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
            tryToGenerateTaxies(GENERATE_NUM_OF_TAXIES);

            double timeToAdvance = fedamb.federateTime + TIME_STEP;
            advanceTime(timeToAdvance);

            if(fedamb.events.size() > 0) {
                fedamb.events.sort(new Event.EventComparator());
                for(Event event : fedamb.events) {
                    fedamb.federateTime = event.getTime();
                    int interactionClass = event.getInteractionClass();

                    try {
                        if(interactionClass == fedamb.receiveDistanceHandle) {
                            fedamb.receiveDistance(event.getInteraction(), convertTime(event.getTime()));
                        }
                        else if(interactionClass == fedamb.receiveDestinationFromPassengerHandle) {
                            fedamb.receiveDestinationFromPassenger(event.getInteraction(), convertTime(event.getTime()));
                        }
                        else if(interactionClass == fedamb.receivePassengerEnteredTaxiHandle) {
                            fedamb.receivePassengerEnteredTaxi(event.getInteraction(), convertTime(event.getTime()));
                        }
                        else if(interactionClass == fedamb.receiveTaxiStartedHandle) {
                            fedamb.receiveTaxiStarted(event.getInteraction(), convertTime(event.getTime()));
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

        int askPassengerForDestinationHandle = rtiamb.getInteractionClassHandle("InteractionRoot.sendAskPassengerForDestination");
        rtiamb.publishInteractionClass(askPassengerForDestinationHandle);
        fedamb.sendAskPassengerForDestinationHandle = askPassengerForDestinationHandle;

        int checkDistanceHandle = rtiamb.getInteractionClassHandle("InteractionRoot.sendCheckDistance");
        rtiamb.publishInteractionClass(checkDistanceHandle);
        fedamb.sendCheckDistanceHandle = checkDistanceHandle;

        int taxiStartedHandle = rtiamb.getInteractionClassHandle("InteractionRoot.sendTaxiStarted");
        rtiamb.publishInteractionClass(taxiStartedHandle);
        fedamb.sendTaxiStartedHandle = taxiStartedHandle;

        int taxiArrivedToRegionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.sendTaxiArrivedToRegion");
        rtiamb.publishInteractionClass(taxiArrivedToRegionHandle);
        fedamb.sendTaxiArrivedToRegionHandle = taxiArrivedToRegionHandle;

        simulationEndedHandle = rtiamb.getInteractionClassHandle("InteractionRoot.simulationEnded");
        rtiamb.publishInteractionClass(simulationEndedHandle);

        int destinationFromPassengerHandle = rtiamb.getInteractionClassHandle("InteractionRoot.sendDestinationToTaxi");
        rtiamb.subscribeInteractionClass(destinationFromPassengerHandle);
        fedamb.receiveDestinationFromPassengerHandle = destinationFromPassengerHandle;

        int distanceHandle = rtiamb.getInteractionClassHandle("InteractionRoot.sendDistance");
        rtiamb.subscribeInteractionClass(distanceHandle);
        fedamb.receiveDistanceHandle = distanceHandle;

        int passengerEnteredTaxiHandle = rtiamb.getInteractionClassHandle("InteractionRoot.sendPassengerEnteredTaxi");
        rtiamb.subscribeInteractionClass(passengerEnteredTaxiHandle);
        fedamb.receivePassengerEnteredTaxiHandle = passengerEnteredTaxiHandle;

//        int taxiStartHandle = rtiamb.getInteractionClassHandle("InteractionRoot.sendTaxiStarted");
//        rtiamb.subscribeInteractionClass(taxiStartHandle);
//        fedamb.receiveTaxiStartedHandle = taxiStartHandle;
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
        System.out.println( "TaxiFederate  : " + message );
    }

    public static void main(String[] args) {
        try {
            new TaxiFederate().runFederate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void tryToGenerateTaxies(int amount) throws RTIexception {
        if(generateStartingTaxies) {
            for(int i=0;i<amount;i++) {
                Taxi taxi = fedamb.generateTaxi();
                fedamb.addTaxiToList(taxi);

                int randomRegionId = new Random().nextInt(RegionAmbassador.NUM_OF_REGIONS);
                fedamb.sendTaxiArrivedToRegion(taxi.getId(), randomRegionId, fedamb.federateTime + fedamb.arriveToRegionTime);
            }
            generateStartingTaxies = false;
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
