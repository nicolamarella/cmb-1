package report;

import core.DTNHost;
import core.UpdateListener;
import interfaces.WifiNetworkInterface;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ConnectionSpeedReport extends SamplingReport implements UpdateListener {

    private boolean setupDone = false;
    private List<WifiNetworkInterface> APInterfaces;

    //averages as well as connection ratios are calculated over all connected client nodes per time unit
    private final HashMap<WifiNetworkInterface,List<Double>> averageAPSpeeds = new HashMap<>();
    private final List<Double> averageConnectionSpeeds = new LinkedList<>();
    private final List<Double> connectionRatios = new LinkedList<>();

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

        hosts.stream()
                .map(i -> i.getInterface(1))
                .filter(WifiNetworkInterface.class::isInstance)
                .map(WifiNetworkInterface.class::cast)
                .filter(i -> !i.getIsAccessPoint())
                .forEach(clientInterface -> {
                    if (clientInterface.getConnections().size() == 0) {
                        unconnectedCounter.getAndIncrement();
                        return;
                    }
                    WifiNetworkInterface APInterface = (WifiNetworkInterface) clientInterface.getConnections().get(0).getOtherInterface(clientInterface);
                    int connectionSpeed = clientInterface.getTransmitSpeed(APInterface);
                    currentAPSpeeds.get(APInterface).add(connectionSpeed);
                    currentConnectionSpeeds.add(connectionSpeed);
                    connectedCounter.getAndIncrement();
                });

        averageConnectionSpeeds.add(calculateIntAverage(currentConnectionSpeeds));
        connectionRatios.add((double) connectedCounter.get() / (connectedCounter.get() + unconnectedCounter.get()));
        for (WifiNetworkInterface APInterface: APInterfaces) {
            averageAPSpeeds.get(APInterface).add(calculateIntAverage(currentAPSpeeds.get(APInterface)));
        }
    }

    @Override
    public void done() {
        //TODO: Print out summary
        init();
        out.print("Connection Ratios:");
        connectionRatios.forEach(ratio -> out.print("\t" + ratio));
        out.print("\n");
        out.print("Overall average Connection Speeds:");
        averageConnectionSpeeds.forEach(speed -> out.print("\t" + speed));
        out.print("\n");
        for (WifiNetworkInterface in: APInterfaces) {
            out.print(in.toString() + " average connection speeds");
            averageAPSpeeds.get(in).forEach(speed -> out.print("\t" + speed));
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
                .toList();
        APInterfaces.forEach(APInterface -> {
                    List<Double> emptyList = new LinkedList<>();
                    averageAPSpeeds.put(APInterface, emptyList);
                });
        setupDone = true;
    }

    private double calculateIntAverage(List<Integer> values) {
        int sum = 0;
        for (int v: values) sum += v;
        return (double) sum / values.size();
    }
}
