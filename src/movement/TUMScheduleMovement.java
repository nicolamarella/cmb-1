package movement;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import core.Coord;
import core.DTNHostStudent;
import core.Settings;
import core.SettingsError;
import core.SimClock;
import core.SimError;
import input.WKTMapReader;
import movement.map.SimMap;
import movement.schedule.Student;
import movement.schedule.TUMRoomSchedule;
import movement.map.DijkstraPathFinder;
import movement.map.MapNode;

public class TUMScheduleMovement extends MapBasedMovement {
    public static final String TUM_MOVEMENT_NS = "TUMScheduleMovement";
    public static final String STATING_POINTS_FILE = "startingPointsFile";
    public static final String ROOMS_FILE = "roomsFile";
    /** sim map for the model */
    private SimMap map = null;
    private static SimMap cachedMap = null;
    /** names of the previously cached map's files (for hit comparison) */
    private static List<String> cachedMapFiles = null;

    List<Coord> startingPoints;
    List<Coord> roomsPoints;
    private DijkstraPathFinder pathFinder;
    private Coord location;

    @Override
    public Path getPath() {
        /**
         * this is the interesting part:
         * we need to decide wether student will go to next class
         * or student will go to cafeteria
         * or student will go to library
         * or student will go home
         */
        final int curTime = SimClock.getIntTime();
        DTNHostStudent host = (DTNHostStudent) this.getHost();
        Student student = host.getStudent();
        TUMRoomSchedule upcomingClass = student.getNextClass(curTime);
        if (upcomingClass == null) {
            return null;
        }
        Coord targetClass = this.roomsPoints.get(upcomingClass.getPOIIndex());
        MapNode destinationNode = getClosestMapNode(targetClass, this.getMap().getNodes());

        List<MapNode> nodes = pathFinder.getShortestPath(lastMapNode,
                destinationNode);
        Path path = new Path(generateSpeed());
        for (MapNode node : nodes) {
            path.addWaypoint(node.getLocation());
        }
        location = destinationNode.getLocation().clone();
        return path;
    }

    @Override
    public Coord getInitialLocation() {

        MapNode closes = getClosestMapNode(
                this.startingPoints.get(rng.nextInt(this.startingPoints.size())), this.getMap().getNodes());
        lastMapNode = closes;
        location = closes.getLocation().clone();
        return location;
    }

    /**
     * Reads a sim map from location set to the settings, mirrors the map and
     * moves its upper left corner to origo.
     * 
     * @return A new SimMap based on the settings
     */
    private SimMap readMap() {
        /**
         * Group16: again, we must reimplement instead of override because of private
         * visability
         */
        SimMap simMap;
        Settings settings = new Settings(MAP_BASE_MOVEMENT_NS);
        WKTMapReader r = new WKTMapReader(true);

        if (cachedMap == null) {
            cachedMapFiles = new ArrayList<String>(); // no cache present
        } else { // something in cache
                 // check out if previously asked map was asked again
            SimMap cached = checkCache(settings);
            if (cached != null) {
                return cached; // we had right map cached -> return it
            } else { // no hit -> reset cache
                cachedMapFiles = new ArrayList<String>();
                cachedMap = null;
            }
        }

        try {
            int nrofMapFiles = settings.getInt(NROF_FILES_S);

            for (int i = 1; i <= nrofMapFiles; i++) {
                System.out.println("Reading map number " + i);
                String pathFile = settings.getSetting(FILE_S + i);
                cachedMapFiles.add(pathFile);
                r.addPaths(new File(pathFile), i);
            }
        } catch (IOException e) {
            throw new SimError(e.toString(), e);
        }

        simMap = r.getMap();
        checkMapConnectedness(simMap.getNodes());
        // mirrors the map (y' = -y) and moves its upper left corner to origo
        simMap.mirror();
        Coord offset = simMap.getMinBound().clone();
        simMap.translate(-offset.getX(), -offset.getY());
        checkCoordValidity(simMap.getNodes());
        readStartingPoints(offset);
        readRoomsPoints(offset);
        cachedMap = simMap;
        return simMap;
    }

    protected void readStartingPoints(Coord offset) {
        Settings settings = new Settings(TUM_MOVEMENT_NS);
        WKTMapReader pr = new WKTMapReader(true);
        try {
            startingPoints = pr.readPoints(new File(settings.getSetting(STATING_POINTS_FILE)));
        } catch (Exception e) {
            throw new SimError(e.toString(), e);
        }
        for (Coord n : startingPoints) {
            n.setLocation(n.getX(), -n.getY());
            n.translate(-offset.getX(), -offset.getY());
        }
    }

    protected void readRoomsPoints(Coord offset) {
        Settings settings = new Settings(TUM_MOVEMENT_NS);
        WKTMapReader rr = new WKTMapReader(true);
        try {
            roomsPoints = rr.readPoints(new File(settings.getSetting(ROOMS_FILE)));
        } catch (Exception e) {
            throw new SimError(e.toString(), e);
        }
        for (Coord n : roomsPoints) {
            n.setLocation(n.getX(), -n.getY());
            n.translate(-offset.getX(), -offset.getY());
        }

    }

    /**
     * Checks that all coordinates of map nodes are within the min&max limits
     * of the movement model
     * 
     * @param nodes The list of nodes to check
     * @throws SettingsError if some map node is out of bounds
     */
    private void checkCoordValidity(List<MapNode> nodes) {
        // Check that all map nodes are within world limits
        for (MapNode n : nodes) {
            double x = n.getLocation().getX();
            double y = n.getLocation().getY();
            if (x < 0 || x > getMaxX() || y < 0 || y > getMaxY()) {
                throw new SettingsError("Map node " + n.getLocation() +
                        " is out of world  bounds " +
                        "(x: 0..." + getMaxX() + " y: 0..." + getMaxY() + ")");
            }
        }
    }

    /**
     * Checks that all map nodes can be reached from all other map nodes
     * 
     * @param nodes The list of nodes to check
     * @throws SettingsError if all map nodes are not connected
     */
    private void checkMapConnectedness(List<MapNode> nodes) {
        Set<MapNode> visited = new HashSet<MapNode>();
        Queue<MapNode> unvisited = new LinkedList<MapNode>();
        MapNode firstNode;
        MapNode next = null;

        if (nodes.size() == 0) {
            throw new SimError("No map nodes in the given map");
        }

        firstNode = nodes.get(0);

        visited.add(firstNode);
        unvisited.addAll(firstNode.getNeighbors());

        while ((next = unvisited.poll()) != null) {
            visited.add(next);
            for (MapNode n : next.getNeighbors()) {
                if (!visited.contains(n) && !unvisited.contains(n)) {
                    unvisited.add(n);
                }
            }
        }

        if (visited.size() != nodes.size()) { // some node couldn't be reached
            MapNode disconnected = null;
            for (MapNode n : nodes) { // find an example node
                if (!visited.contains(n)) {
                    disconnected = n;
                    break;
                }
            }
            throw new SettingsError("SimMap is not fully connected. Only " +
                    visited.size() + " out of " + nodes.size() + " map nodes " +
                    "can be reached from " + firstNode + ". E.g. " +
                    disconnected + " can't be reached");
        }
    }

    /**
     * Checks map cache if the requested map file(s) match to the cached
     * sim map
     * 
     * @param settings The Settings where map file names are found
     * @return A cached map or null if the cached map didn't match
     */
    private SimMap checkCache(Settings settings) {
        int nrofMapFiles = settings.getInt(NROF_FILES_S);

        if (nrofMapFiles != cachedMapFiles.size() || cachedMap == null) {
            return null; // wrong number of files
        }

        for (int i = 1; i <= nrofMapFiles; i++) {
            String pathFile = settings.getSetting(FILE_S + i);
            if (!pathFile.equals(cachedMapFiles.get(i - 1))) {
                return null; // found wrong file name
            }
        }

        // all files matched -> return cached map
        return cachedMap;
    }

    /**
     * Help method to find the closest coordinate from a list of coordinates,
     * to a specific location
     * 
     * @param allCoords list of coordinates to compare
     * @param coord     destination node
     * @return closest to the destination
     */
    private static MapNode getClosestMapNode(Coord point, List<MapNode> nodes) {
        MapNode closestCoord = null;
        double minDistance = Double.POSITIVE_INFINITY;
        for (MapNode temp : nodes) {
            double distance = temp.getLocation().distance(point);
            if (distance < minDistance) {
                minDistance = distance;
                closestCoord = temp;
            }
        }
        return closestCoord;
    }

    // Constructors and replicate
    public TUMScheduleMovement(final Settings settings) {
        super(settings);
        map = readMap();
        maxPathLength = 100;
        minPathLength = 10;
        backAllowed = false;
        pathFinder = new DijkstraPathFinder(null);
    }

    public TUMScheduleMovement(final TUMScheduleMovement mbm) {
        super(mbm);
        this.startingPoints = mbm.startingPoints;
        this.roomsPoints = mbm.roomsPoints;
        this.map = mbm.map;
        this.minPathLength = mbm.minPathLength;
        this.maxPathLength = mbm.maxPathLength;
        this.backAllowed = mbm.backAllowed;
        this.pathFinder = mbm.pathFinder;

    }

    @Override
    public MapBasedMovement replicate() {
        return new TUMScheduleMovement(this);
    }

    public static void main(String[] args) {
        WKTMapReader r = new WKTMapReader(true);
        List<Coord> pois;
        try {
            pois = r.readPoints(new File("data/fmi/cleaned/StartingPoints.wkt"));
        } catch (Exception e) {
            throw new SimError(e.toString(), e);
        }
        System.out.println(pois);
    }
}
