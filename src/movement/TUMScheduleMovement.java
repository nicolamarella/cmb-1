package movement;

import core.Coord;
import core.Settings;

public class TUMScheduleMovement extends MovementModel {
    // ==========================================================================//
    // Settings
    // ==========================================================================//
    /** {@code true} to confine nodes inside the polygon */
    public static final String SCHEDULE_FILE = "scheduleFile";
    // ==========================================================================//
    private String fileSchedulePath;

    public TUMScheduleMovement(final Settings settings) {
        super(settings);
        this.fileSchedulePath = settings.getSetting(SCHEDULE_FILE);
    }

    public TUMScheduleMovement(final TUMScheduleMovement other) {
        super(other);
        this.fileSchedulePath = other.fileSchedulePath;
    }

    @Override
    public MovementModel replicate() {
        return new TUMScheduleMovement(this);
    }

    @Override
    public Path getPath() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Coord getInitialLocation() {
        // TODO Auto-generated method stub
        return null;
    }
}
