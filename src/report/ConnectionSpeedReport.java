package report;

import core.*;
import interfaces.WifiNetworkInterface;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/** Reports average connection speeds and connection ratios of nodes connected by {@link WifiNetworkInterface}.
 * Splits up by specified time intervals and access points.**/
public class ConnectionSpeedReport extends SamplingReport implements UpdateListener {

    /** Interval in seconds between two report numbers ({@value}).
     * Has to be equal to or higher than {@link #SAMPLE_INTERVAL_SETTING}.
     * Ideally is a multiple of it. **/
    public static final String REPORT_INTERVAL_SETTING = "reportInterval";
    /** Default value for {@link #REPORT_INTERVAL_SETTING} ({@value} seconds). **/
    public static final double DEFAULT_REPORT_INTERVAL = 1000;

    private final double reportInterval;
    private boolean setupDone = false;
    private double lastReport = 0;

    //lists of access point and client node interfaces
    private List<WifiNetworkInterface> APInterfaces;
    private List<WifiNetworkInterface> clientInterfaces;

    //connection speed averages calculated over all connected client nodes per report interval
    private final HashMap<WifiNetworkInterface,List<Double>> averageAPSpeeds = new HashMap<>();
    private final List<Double> averageConnectionSpeeds = new LinkedList<>();

    //connection ratios calculated over all client nodes per report interval
    private final List<Double> connectionRatios = new LinkedList<>();

    //connection speeds of all connected client nodes over the full simulation time
    private final HashMap<WifiNetworkInterface,List<Integer>> APSpeeds = new HashMap<>();
    private final List<Integer> connectionSpeeds = new LinkedList<>();

    //values measured in the current report interval
    private final HashMap<WifiNetworkInterface,List<Integer>> currentAPSpeeds = new HashMap<>();
    private final List<Integer> currentConnectionSpeeds = new LinkedList<>();
    private final AtomicInteger connectedCounter = new AtomicInteger();
    private final AtomicInteger unconnectedCounter = new AtomicInteger();

    public ConnectionSpeedReport() {
        super();
        reportInterval = getSettings().getDouble(REPORT_INTERVAL_SETTING, DEFAULT_REPORT_INTERVAL);
        if (reportInterval < super.interval) {
            throw new SettingsError("Setting '" + REPORT_INTERVAL_SETTING + "' must be higher than setting '"
                    + SAMPLE_INTERVAL_SETTING + "'. Found " + this.reportInterval + ".");
        }
    }

    @Override
    public void sample(List<DTNHost> hosts) {
        if (!setupDone) setup(hosts);
        clientInterfaces.forEach(clientInterface -> {
                    if (clientInterface.getConnections().size() == 0) {
                        unconnectedCounter.getAndIncrement();
                        return;
                    }
                    WifiNetworkInterface APInterface = (WifiNetworkInterface) clientInterface.getConnections().get(0)
                            .getOtherInterface(clientInterface);
                    int connectionSpeed = clientInterface.getTransmitSpeed(APInterface);
                    currentAPSpeeds.get(APInterface).add(connectionSpeed);
                    APSpeeds.get(APInterface).add(connectionSpeed);
                    currentConnectionSpeeds.add(connectionSpeed);
                    connectionSpeeds.add(connectionSpeed);
                    connectedCounter.getAndIncrement();
        });

        //if the current report interval is over, averages are calculated for it and a new interval is started
        if (SimClock.getTime() - lastReport <= reportInterval) return;
        lastReport = SimClock.getTime();
        averageConnectionSpeeds.add(calculateIntAverage(currentConnectionSpeeds));
        connectionRatios.add((double) connectedCounter.get() / (connectedCounter.get() + unconnectedCounter.get()));
        for (WifiNetworkInterface in: APInterfaces) {
            averageAPSpeeds.get(in).add(calculateIntAverage(currentAPSpeeds.get(in)));
        }
        APInterfaces.forEach(APInterface -> {
            List<Integer> emptyList = new LinkedList<>();
            currentAPSpeeds.put(APInterface, emptyList);
        });
    }

    @Override
    public void done() {
        init();
        out.println("TOTAL AVERAGES:");
        out.println("---------------");
        out.println("Connection Ratio: " + format(calculateDoubleAverage(connectionRatios)));
        out.println("Overall Average Connection Speed: " + format(calculateIntAverage(connectionSpeeds)));
        for (WifiNetworkInterface in: APInterfaces) {
            out.print(in.getHost().toString() + " Average Connection Speed: "
                    + format(calculateIntAverage(APSpeeds.get(in))));
            out.print("\n");
        }
        out.print("\n");

        out.println("AVERAGES OVER TIME:");
        out.println("-------------------");
        out.print("Connection Ratios:                ");
        connectionRatios.forEach(ratio -> out.print("\t" + format(ratio)));
        out.print("\n");
        out.print("Overall Average Connection Speeds:");
        averageConnectionSpeeds.forEach(speed -> out.print("\t" + format(speed) + " "));
        out.print("\n");
        for (WifiNetworkInterface in: APInterfaces) {
            out.print(in.getHost().toString() + " Average Connection Speeds:");
            averageAPSpeeds.get(in).forEach(speed -> out.print("\t" + format(speed) + " "));
            out.print("\n");
        }
        super.done();
    }

    private void setup(List<DTNHost> hosts) {
        APInterfaces = hosts.stream()
                .map(host -> host.getInterface(1))
                .filter(WifiNetworkInterface.class::isInstance)
                .map(WifiNetworkInterface.class::cast)
                .filter(WifiNetworkInterface::getIsAccessPoint)
                .collect(Collectors.toList());
        APInterfaces.forEach(APInterface -> {
                    List<Double> emptyDoubleList = new LinkedList<>();
                    List<Integer> emptyIntList0 = new LinkedList<>();
                    List<Integer> emptyIntList1 = new LinkedList<>();
                    averageAPSpeeds.put(APInterface, emptyDoubleList);
                    APSpeeds.put(APInterface, emptyIntList0);
                    currentAPSpeeds.put(APInterface, emptyIntList1);
        });
        clientInterfaces = hosts.stream()
                .map(i -> i.getInterface(1))
                .filter(WifiNetworkInterface.class::isInstance)
                .map(WifiNetworkInterface.class::cast)
                .filter(i -> !i.getIsAccessPoint())
                .collect(Collectors.toList());
        setupDone = true;
    }

    private double calculateIntAverage(List<Integer> values) {
        int sum = 0;
        for (int v: values) sum += v;
        return (double) sum / values.size();
    }

    private double calculateDoubleAverage(List<Double> values) {
        double sum = 0;
        for (double v: values) sum += v;
        return sum / values.size();
    }
}