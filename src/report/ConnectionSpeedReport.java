package report;

import core.DTNHost;
import core.UpdateListener;
import interfaces.WifiNetworkInterface;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class ConnectionSpeedReport extends SamplingReport implements UpdateListener {

    private boolean setupDone = false;
    private List<WifiNetworkInterface> APInterfaces;
    private List<WifiNetworkInterface> clientInterfaces;

    //averages as well as connection ratios calculated over all connected client nodes per time unit
    private final HashMap<WifiNetworkInterface,List<Double>> averageAPSpeeds = new HashMap<>();
    private final List<Double> averageConnectionSpeeds = new LinkedList<>();
    private final List<Double> connectionRatios = new LinkedList<>();

    //connection speeds of all connected client nodes over the full simulation time
    private final HashMap<WifiNetworkInterface,List<Integer>> APSpeeds = new HashMap<>();
    private final List<Integer> connectionSpeeds = new LinkedList<>();

    @Override
    public void sample(List<DTNHost> hosts) {
        if (!setupDone) setup(hosts);

        HashMap<WifiNetworkInterface,List<Integer>> currentAPSpeeds = new HashMap<>();
        List<Integer> currentConnectionSpeeds = new LinkedList<>();
        AtomicInteger connectedCounter = new AtomicInteger();
        AtomicInteger unconnectedCounter = new AtomicInteger();

        APInterfaces.forEach(APInterface -> {
            List<Integer> emptyList = new LinkedList<>();
            currentAPSpeeds.put(APInterface, emptyList);
        });

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

        averageConnectionSpeeds.add(calculateIntAverage(currentConnectionSpeeds));
        connectionRatios.add((double) connectedCounter.get() / (connectedCounter.get() + unconnectedCounter.get()));
        for (WifiNetworkInterface in: APInterfaces) {
            averageAPSpeeds.get(in).add(calculateIntAverage(currentAPSpeeds.get(in)));
        }
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
                    List<Integer> emptyIntList = new LinkedList<>();
                    averageAPSpeeds.put(APInterface, emptyDoubleList);
                    APSpeeds.put(APInterface, emptyIntList);
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