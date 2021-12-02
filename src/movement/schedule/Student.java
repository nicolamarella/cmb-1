package movement.schedule;

import java.util.ArrayList;

/**
 * Act as a proxy/holder for DNTNode
 * Useful to abstract away students while creating schedules
 */
public class Student {
    public static int MAX_CLASSES_PER_STUDENT = 3;

    private ArrayList<TUMRoomSchedule> schedule = new ArrayList<>();
    private boolean hasOverlappingCourses = false;

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

    public Student() {
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
