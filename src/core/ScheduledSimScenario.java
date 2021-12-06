package core;

import input.EventQueue;
import input.EventQueueHandler;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import movement.MapBasedMovement;
import movement.MovementModel;
import movement.map.SimMap;
import movement.schedule.Student;
import movement.schedule.TUMRoomSchedule;
import movement.schedule.TUMScheduler;
import routing.MessageRouter;

public class ScheduledSimScenario extends SimScenario {
    /** a way to get a hold of this... */
    private static ScheduledSimScenario myinstance = null;

    /** namespace of scenario settings ({@value}) */
    public static final String SCENARIO_NS = "Scenario";
    /** number of host groups -setting id ({@value}) */
    public static final String NROF_GROUPS_S = "nrofHostGroups";
    /** number of interface types -setting id ({@value}) */
    public static final String NROF_INTTYPES_S = "nrofInterfaceTypes";
    /** scenario name -setting id ({@value}) */
    public static final String NAME_S = "name";
    /** end time -setting id ({@value}) */
    public static final String END_TIME_S = "endTime";
    /** update interval -setting id ({@value}) */
    public static final String UP_INT_S = "updateInterval";
    /** simulate connections -setting id ({@value}) */
    public static final String SIM_CON_S = "simulateConnections";

    /** namespace for interface type settings ({@value}) */
    public static final String INTTYPE_NS = "Interface";
    /** interface type -setting id ({@value}) */
    public static final String INTTYPE_S = "type";
    /** interface name -setting id ({@value}) */
    public static final String INTNAME_S = "name";

    /** namespace for application type settings ({@value}) */
    public static final String APPTYPE_NS = "Application";
    /** application type -setting id ({@value}) */
    public static final String APPTYPE_S = "type";
    /** setting name for the number of applications */
    public static final String APPCOUNT_S = "nrofApplications";

    /** namespace for host group settings ({@value}) */
    public static final String GROUP_NS = "Group";
    /** group id -setting id ({@value}) */
    public static final String GROUP_ID_S = "groupID";
    /** number of hosts in the group -setting id ({@value}) */
    public static final String NROF_HOSTS_S = "nrofHosts";
    /** movement model class -setting id ({@value}) */
    public static final String MOVEMENT_MODEL_S = "movementModel";
    /** router class -setting id ({@value}) */
    public static final String ROUTER_S = "router";
    /** number of interfaces in the group -setting id ({@value}) */
    public static final String NROF_INTERF_S = "nrofInterfaces";
    /** interface name in the group -setting id ({@value}) */
    public static final String INTERFACENAME_S = "interface";
    /** application name in the group -setting id ({@value}) */
    public static final String GAPPNAME_S = "application";

    /** package where to look for movement models */
    private static final String MM_PACKAGE = "movement.";
    /** package where to look for router classes */
    private static final String ROUTING_PACKAGE = "routing.";

    /** package where to look for interface classes */
    private static final String INTTYPE_PACKAGE = "interfaces.";

    /** package where to look for application classes */
    private static final String APP_PACKAGE = "applications.";
    private SimMap simMap;

    /** number of host groups */
    int nrofGroups;
    /** Width of the world */
    /** Global connection event listeners */
    private List<ConnectionListener> connectionListeners;
    /** Global message event listeners */
    private List<MessageListener> messageListeners;
    /** Global movement event listeners */
    private List<MovementListener> movementListeners;
    /** Global application event listeners */
    private List<ApplicationListener> appListeners;

    private TUMScheduler scheduler;

    protected ScheduledSimScenario() {
        super();
        this.scheduler = TUMScheduler.getInstance();

    }

    static {
        DTNSim.registerForReset(ScheduledSimScenario.class.getCanonicalName());
        reset();
    }

    public static void reset() {
        myinstance = null;
    }

    public static ScheduledSimScenario getInstance() {
        if (myinstance == null) {
            myinstance = new ScheduledSimScenario();
        }
        return myinstance;
    }

    /**
     * Creates hosts for the scenario
     */
    protected void createHosts() {
        /**
         * TODO: this class is supposed to replace SimScenario
         * due to it's implementation limitations (mostly becase a lot of methods and
         * properties are marked as private), though, we cannot
         * simply override some methods. Hence it was easier to tweak the original class
         */
        System.err.println(
                "Creating hosts");
        this.hosts = new ArrayList<DTNHost>();

        for (int i = 1; i <= nrofGroups; i++) {
            List<NetworkInterface> interfaces = new ArrayList<NetworkInterface>();
            Settings s = new Settings(GROUP_NS + i);
            s.setSecondaryNamespace(GROUP_NS);
            String gid = s.getSetting(GROUP_ID_S);
            int nrofHosts = s.getInt(NROF_HOSTS_S);
            int nrofInterfaces = s.getInt(NROF_INTERF_S);
            int appCount;

            // creates prototypes of MessageRouter and MovementModel
            MovementModel mmProto = (MovementModel) s.createIntializedObject(MM_PACKAGE +
                    s.getSetting(MOVEMENT_MODEL_S));
            MessageRouter mRouterProto = (MessageRouter) s.createIntializedObject(ROUTING_PACKAGE +
                    s.getSetting(ROUTER_S));

            /* checks that these values are positive (throws Error if not) */
            s.ensurePositiveValue(nrofHosts, NROF_HOSTS_S);
            s.ensurePositiveValue(nrofInterfaces, NROF_INTERF_S);

            // setup interfaces
            for (int j = 1; j <= nrofInterfaces; j++) {
                String intName = s.getSetting(INTERFACENAME_S + j);
                Settings intSettings = new Settings(intName);
                NetworkInterface iface = (NetworkInterface) intSettings.createIntializedObject(
                        INTTYPE_PACKAGE + intSettings.getSetting(INTTYPE_S));
                iface.setClisteners(connectionListeners);
                iface.setGroupSettings(s);
                interfaces.add(iface);
            }

            // setup applications
            if (s.contains(APPCOUNT_S)) {
                appCount = s.getInt(APPCOUNT_S);
            } else {
                appCount = 0;
            }
            for (int j = 1; j <= appCount; j++) {
                String appname = null;
                Application protoApp = null;
                try {
                    // Get name of the application for this group
                    appname = s.getSetting(GAPPNAME_S + j);
                    // Get settings for the given application
                    Settings t = new Settings(appname);
                    // Load an instance of the application
                    protoApp = (Application) t.createIntializedObject(
                            APP_PACKAGE + t.getSetting(APPTYPE_S));
                    // Set application listeners
                    protoApp.setAppListeners(this.appListeners);
                    // Set the proto application in proto router
                    // mRouterProto.setApplication(protoApp);
                    mRouterProto.addApplication(protoApp);
                } catch (SettingsError se) {
                    // Failed to create an application for this group
                    System.err.println("Failed to setup an application: " + se);
                    System.err.println("Caught at " + se.getStackTrace()[0]);
                    System.exit(-1);
                }
            }

            if (mmProto instanceof MapBasedMovement) {
                this.simMap = ((MapBasedMovement) mmProto).getMap();
            }
            /*
             * // creates hosts of ith group
             * /*
             * for (int j=0; j<nrofHosts; j++) {
             * 
             * // prototypes are given to new DTNHost which replicates
             * // new instances of movement model and message router
             * DTNHost host = new DTNHost(this.messageListeners,
             * this.movementListeners, gid, interfaces, comBus,
             * mmProto, mRouterProto);
             * hosts.add(host);
             * }
             */
            /**
             * Unlike the original implementation, the host instantiation
             * is dictated by the scheduler class
             */
            for (Student student : scheduler.getStudents()) {
                ModuleCommunicationBus comBus = new ModuleCommunicationBus();
                // TODO: extend class DTNHost with ref to student
                // such that movement model can access student schedule
                DTNHostStudent host = new DTNHostStudent(this.messageListeners,
                        this.movementListeners, gid, interfaces, comBus,
                        mmProto, mRouterProto);
                hosts.add(host);
                student.setDTNHost(host);
                host.setStudent(student);
            }
        }
    }
}
