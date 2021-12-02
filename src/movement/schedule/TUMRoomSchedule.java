package movement.schedule;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

public class TUMRoomSchedule implements Comparable<TUMRoomSchedule> {
    private static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("H:m");
    private String roomRef;
    private String course;
    private LocalTime startTime;
    private LocalTime endTime;
    private int participants;
    private ArrayList<Student> assignedStudents = new ArrayList<Student>();

    public TUMRoomSchedule() {

    }

    /**
     * Parse a csv line to instantiate the schedule class
     * 
     * @param csvLine comma separated line
     */
    public TUMRoomSchedule(String[] values) {
        if (values.length != 11) {
            System.err.println("CSV entries should be exactly 11. Does the line contains commas?");
            System.err.println(values);
            System.exit(-1);
        }
        this.roomRef = values[0];
        this.course = values[6];
        this.startTime = LocalTime.parse(values[4], TUMRoomSchedule.formatter);
        this.endTime = LocalTime.parse(values[5], TUMRoomSchedule.formatter);
        try {
            this.participants = Integer.parseInt(values[7]);
        } catch (Exception e) {
            this.participants = 0;
            System.err.println(
                    "Could not parse participants for course '" + this.course + "'. Given value was " + values[7]);
        }
    }

    /**
     * Whenever we instantiate a new host assigned to this room, we add it here to
     * keep track.
     * Might be useful later
     * 
     * @param student
     */
    public void addStudent(Student student) {
        this.assignedStudents.add(student);
    }

    /**
     * Return true if there is more room for new participants
     * 
     * @return true if there is more room for new participants
     */
    public boolean hasMoreSlots() {
        return this.assignedStudents.size() <= this.participants;
    }

    public int remainingSlots() {
        return this.assignedStudents.size() - this.participants;
    }

    /**
     * given another room schedule, simply returns true if they overlap
     * 
     * @param otherSchedule
     * @return
     */
    public boolean isOverlapping(TUMRoomSchedule otherSchedule) {
        // shame there is no isBeforeOrEquals
        return otherSchedule.startTime.isBefore(this.endTime) && otherSchedule.endTime.isAfter(this.startTime) ||
                otherSchedule.startTime.equals(this.endTime) && otherSchedule.endTime.equals(this.startTime);

    }

    public Student[] getAssigHosts() {
        return (Student[]) this.assignedStudents.toArray();
    }

    public String getRoomRef() {
        return roomRef;
    }

    public void setRoomRef(String roomRef) {
        this.roomRef = roomRef;
    }

    public String getCourse() {
        return course;
    }

    public void setCourse(String course) {
        this.course = course;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public int getParticipants() {
        return participants;
    }

    public void setParticipants(int participants) {
        this.participants = participants;
    }

    @Override
    public String toString() {
        return "Room schedule for room " + this.roomRef + " with " + this.participants + " participants. From "
                + this.startTime + " to " + this.endTime;
    }

    @Override
    public int compareTo(TUMRoomSchedule o) {
        return this.startTime.compareTo(o.getStartTime());
    }

}
