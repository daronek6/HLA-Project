package projekt;

import hla.rti.LogicalTime;
import hla.rti.ReceivedInteraction;

import java.util.Comparator;

public class Event {

    private int interactionClass;
    private ReceivedInteraction interaction;
    private double time;

    public Event(int interactionClass, ReceivedInteraction interaction, double time) {
        this.interactionClass = interactionClass;
        this.interaction = interaction;
        this.time = time;
    }

    public int getInteractionClass() {
        return interactionClass;
    }

    public ReceivedInteraction getInteraction() {
        return interaction;
    }

    public double getTime() {
        return time;
    }

    public static class EventComparator implements Comparator<Event> {

        @Override
        public int compare(Event o1, Event o2) {
            return Double.compare(o1.time, o2.time);
        }
    }
}
