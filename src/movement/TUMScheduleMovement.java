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
import core.Settings;
import core.SettingsError;
import core.SimError;
import input.WKTMapReader;
import movement.map.SimMap;
import movement.map.MapNode;

public class TUMScheduleMovement extends MapBasedMovement {
    /** sim map for the model */
    private SimMap map = null;
    /** node where the last path ended or node next to initial placement */
    /** the indexes of the OK map files or null if all maps are OK */
    private int[] okMapNodeTypes;
    /** how many map files are read */
    private int nrofMapFilesRead = 0;
    /** map cache -- in case last mm read the same map, use it without loading */
    private static SimMap cachedMap = null;
    /** names of the previously cached map's files (for hit comparison) */
    private static List<String> cachedMapFiles = null;

    List<Coord> poiss;

    @Override
    public Path getPath() {
        return super.getPath();
    }

    @Override
    public Coord getInitialLocation() {

        MapNode closes = getClosestMapNode(
                this.poiss.get(rng.nextInt(this.poiss.size())), this.getMap().getNodes());
        this.lastMapNode = closes;
        return closes.getLocation().clone();
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
                nrofMapFilesRead = cachedMapFiles.size();
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

            nrofMapFilesRead = nrofMapFiles;
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
        WKTMapReader pr = new WKTMapReader(true);
        try {
            poiss = pr.readPoints(new File("data/fmi/cleaned/StartingPoints.wkt"));
        } catch (Exception e) {
            throw new SimError(e.toString(), e);
        }
        // map is mirrored, see base class at `mirror` method
        for (Coord n : poiss) {
            n.setLocation(n.getX(), -n.getY());
            n.translate(-offset.getX(), -offset.getY());
        }

        cachedMap = simMap;
        return simMap;
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
    }

    public TUMScheduleMovement(final TUMScheduleMovement mbm) {
        super(mbm);
        this.poiss = new ArrayList(mbm.poiss);
        this.okMapNodeTypes = mbm.okMapNodeTypes;
        this.map = mbm.map;
        this.minPathLength = mbm.minPathLength;
        this.maxPathLength = mbm.maxPathLength;
        this.backAllowed = mbm.backAllowed;

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
