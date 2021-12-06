package movement.schedule;

import java.io.Serializable;
import java.net.SecureCacheResponse;
import java.util.ArrayList;
import java.util.Stack;
import java.io.FileNotFoundException; // Import this class to handle errors
import java.io.FileReader;
import java.io.IOException;

import com.opencsv.CSVReader;

/**
 * This class is in charge of parsing an input file with rooms/lectures
 * schedules
 * and provide, for each node, a randomic but plausible schedule.
 * That is:
 * - Reasonable amount of classes
 * - No overlapping classes (OR can there be overlapping classes for some
 * students?)
 * - Ensure that for each class defined in the input file, we don't assign more
 * than the limit participants
 */
public class TUMScheduler implements Serializable {

	/**
	 * normally students tries not to have overlapping classes
	 * But something this is not possible. Hence, we adding classes to schedule,
	 * we give a certaing randomness for this to happen
	 */
	public static float OVERLAP_CHANGE = 0.1f;

	private static TUMScheduler singleton_instance = null;

	public static TUMScheduler getInstance(String scheduleCSVFile) {
		if (singleton_instance == null) {
			singleton_instance = new TUMScheduler(scheduleCSVFile);
		}
		return singleton_instance;
	}

	private String scheduleCSVFile = "";
	private ArrayList<TUMRoomSchedule> roomSchedules;
	private ArrayList<Student> students = new ArrayList<>();

	/**
	 * This is private, to ensure singleton constructor
	 */
	private TUMScheduler(String scheduleCSVFile) {
		this.scheduleCSVFile = scheduleCSVFile;
		this.loadCSV();
		this.initSchedule();
	}

	/**
	 * Load Schedule csv file, see example file data/fmi/fmi_schedule_tuesday.csv
	 * 
	 * @param path relative path to input schedule csv file, for instance inside
	 *             data directory
	 */
	private void loadCSV() {
		try (CSVReader csvReader = new CSVReader(new FileReader(this.scheduleCSVFile));) {
			String[] values = null;
			csvReader.readNext(); // skip header
			this.roomSchedules = new ArrayList<>();
			while ((values = csvReader.readNext()) != null) {
				TUMRoomSchedule schedule = new TUMRoomSchedule(values);
				this.roomSchedules.add(schedule);
			}

		} catch (FileNotFoundException fnfe) {
			System.err.println("Could not find TUM schedule csv input");
			System.exit(-1);
		} catch (IOException ioe) {
			System.err.println("Could not find TUM schedule csv input");
			System.exit(-1);
		}
	}

	/**
	 * We need to do some checks before hand, i.e total number of participants
	 * spread across rooms
	 * init some variables and so on. This happens here
	 */
	private void initSchedule() {
		Stack<TUMRoomSchedule> schedulesToAssign = new Stack<TUMRoomSchedule>();
		schedulesToAssign.addAll(this.roomSchedules);
		/**
		 * Logic:
		 * - start from the class with highest number of students
		 * - assign a node until class participants is filled
		 * - move to the next class:
		 * - if overlapping: create a new student node and assign
		 * - else assign that node
		 * - rinse and repeat until all classes have all participants
		 */
		TUMRoomSchedule current;
		while (!schedulesToAssign.isEmpty()) {
			current = schedulesToAssign.pop();
			for (int i = 0; i < students.size() && current.hasMoreSlots(); i++) {
				Student s = students.get(i);
				if ((!s.isOverlappingForStudent(current) || Math.random() < OVERLAP_CHANGE) && !s.isFullyStuffed()) {
					s.addCourse(current);
					current.addStudent(s);
				}
			}
			while (current.hasMoreSlots()) {
				Student s = new Student();
				s.addCourse(current);
				current.addStudent(s);
				students.add(s);
			}
		}
		int maxSchedules = 0;
		int minSchedules = 1000;
		for (Student s : students) {
			if (s.getSchedule().size() > maxSchedules) {
				maxSchedules = s.getSchedule().size();
			}
			if (s.getSchedule().size() < minSchedules) {
				minSchedules = s.getSchedule().size();
			}
		}
		System.out.println("Printint all students schedule");
		System.out.println("Total number of students: " + students.size());
		System.out.println("Min number of schedule is " + minSchedules);
		System.out.println("Max number of schedule is " + maxSchedules);
	}

	public ArrayList<Student> getStudents() {
		return students;
	}

	/**
	 * Returns a String representation of the TUM schedule
	 * 
	 * @return a String representation of the TUM map
	 */
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (Student s : getStudents()) {
			sb.append(s.toString());
		}
		return sb.toString();
	}

	public static void main(String[] args) {
		/**
		 * Used only for debug purposes
		 */
		System.out.println("Testing TUMScheduler");
		TUMScheduler scheduler = TUMScheduler.getInstance("data/fmi/fmi_schedule_tuesday.csv");
		System.out.println(scheduler);

	}
}
