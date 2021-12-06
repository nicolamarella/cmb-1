package movement.schedule;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import core.DTNHost;
import core.SimClock;

/**
 * Act as a proxy/holder for DNTNode
 * Useful to abstract away students while creating schedules
 */
public class Student {
    public static int MAX_CLASSES_PER_STUDENT = 3;
    /** Students might be 5 mins late or early to class */
    public static int CLASS_SECOND_EARLY_LATE_THRESHOLD = 60 * 5;

    private ArrayList<TUMRoomSchedule> schedule = new ArrayList<>();
    private boolean hasOverlappingCourses = false;
    private DTNHost host;
    // used to store the state of a student following a class
    private Random rng = new Random();

    private int firstLectureStartSeconds = Integer.MAX_VALUE;
    private int lastLectureEndSeconds = 0;

    public Student() {
    }

    /**
     * Returns the next class given the time, might return null if student has no
     * schedule
     * 
     * @return
     */
    public TUMRoomSchedule getNextClass() {
        int currentSeconds = SimClock.getIntTime();
        // we might have overlaps, for which we want to pick randomly
        TUMRoomSchedule nextClass = null;
        List<TUMRoomSchedule> upcomingClasses = new ArrayList<TUMRoomSchedule>();
        for (TUMRoomSchedule s : schedule) {
            if (Math.abs(s.getStartTimeSecond() - currentSeconds) < CLASS_SECOND_EARLY_LATE_THRESHOLD) {
                upcomingClasses.add(s);
            }
        }
        if (upcomingClasses.size() > 0) {
            nextClass = upcomingClasses.get(rng.nextInt(upcomingClasses.size()));
        }
        return nextClass;
    }

    public TUMRoomSchedule getUpcomingClass() {
        // TODO: not really efficient, might use a min heap of any ordered stucture
        // here.
        int currTime = SimClock.getIntTime() - 10;
        TUMRoomSchedule upcomingClass = null;
        for (TUMRoomSchedule s : schedule) {
            // find min
            if (s.getStartTimeSecond() >= currTime
                    && (upcomingClass == null || (s.getStartTimeSecond() < upcomingClass.getStartTimeSecond()))) {
                upcomingClass = s;
            }
        }

        return upcomingClass;
    }

    public void setDTNHost(DTNHost host) {
        this.host = host;
    }

    public DTNHost getDTNHost() {
        return this.host;
    }

    public boolean hasOverlappingCourses() {
        return hasOverlappingCourses;
    }

    public ArrayList<TUMRoomSchedule> getSchedule() {
        return schedule;
    }

    /**
     * Students should NOT have a large amount of classes - might happens due to the
     * overlap option
     * 
     * @return
     */
    public boolean isFullyStuffed() {
        return this.schedule.size() >= MAX_CLASSES_PER_STUDENT;
    }

    /**
     * Give a student (the instance itself) if the student has already overlapping
     * classes
     * return true
     * 
     * @param newSchedule
     * @return
     */
    public boolean isOverlappingForStudent(TUMRoomSchedule newSchedule) {
        for (TUMRoomSchedule course : schedule) {
            if (course.isOverlapping(newSchedule)) {
                return true;
            }
        }
        return false;
    }

    public void addCourse(TUMRoomSchedule newSchedule) {
        if (this.isFullyStuffed()) {
            throw new RuntimeException("Students is fully booked");
        }
        this.hasOverlappingCourses = this.isOverlappingForStudent(newSchedule);
        this.schedule.add(newSchedule);
        // keep track of first lecture start and last lecture end
        if (newSchedule.getStartTimeSecond() < firstLectureStartSeconds) {
            firstLectureStartSeconds = newSchedule.getStartTimeSecond();
        }
        if (newSchedule.getEndTimeSecond() > lastLectureEndSeconds) {
            lastLectureEndSeconds = newSchedule.getEndTimeSecond();
        }
    }

    public int getLastLectureEndSeconds() {
        return lastLectureEndSeconds;
    }

    public int getFirstLectureStartSeconds() {
        return firstLectureStartSeconds;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Student: ");
        for (TUMRoomSchedule rs : schedule) {
            sb.append("\n\t" + rs.toString());
        }
        return sb.toString();
    }

}
