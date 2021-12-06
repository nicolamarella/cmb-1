package movement.schedule;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import core.DTNHost;

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
    private TUMRoomSchedule currentClass;
    private Random rng = new Random();

    public Student() {
    }

    public boolean isHavingClass() {
        return currentClass != null;
    }

    private void checkCurrentClassOver(int currentSeconds) {
        if (isHavingClass()
                && Math.abs(currentClass.getEndTimeSecond() - currentSeconds) < CLASS_SECOND_EARLY_LATE_THRESHOLD) {
            // class is over, set it to null
            System.out.println("Class is over!" + currentClass);
            currentClass = null;
        }
    }

    /**
     * Returns the next class given the time, might return null if student has no
     * schedule
     * 
     * @return
     */
    public TUMRoomSchedule getNextClass(int currentSeconds) {
        checkCurrentClassOver(currentSeconds);
        if (isHavingClass()) {
            // staying in the same class, no change
            return null;
        }
        // we might have overlaps, for which we want to pick randomly
        List<TUMRoomSchedule> upcomingClasses = new ArrayList<TUMRoomSchedule>();
        for (TUMRoomSchedule s : schedule) {
            if (Math.abs(s.getStartTimeSecond() - currentSeconds) < CLASS_SECOND_EARLY_LATE_THRESHOLD) {
                upcomingClasses.add(s);
            }
        }
        if (upcomingClasses.size() > 0) {
            currentClass = upcomingClasses.get(rng.nextInt(upcomingClasses.size()));
            // set new class
            System.out.println("New class set !" + currentClass);
        }
        return currentClass;
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
