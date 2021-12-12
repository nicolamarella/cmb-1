package interfaces;

import core.*;

import java.util.Collection;
import java.util.stream.Collectors;


/**
 * A combination of DistanceCapacityInterface and InterferenceLimitedInterface
 * simulating the connection of Access Points and mobile nodes and taking into account
 * the signal noise from other nearby access points and their signal strength.
 */
public class WifiNetworkInterface extends NetworkInterface {

    public static final String TRANSMIT_SPEEDS_S = "transmitSpeeds";
    public static final String ACCESS_POINT_FLAG = "isAccessPoint";
    public static final String TRANSMIT_POWER = "transmitPower";
    public static final String NOISE_MULTIPLIER = "noiseMultiplier";
    public static final String SPEED_MULTIPLIER = "speedMultiplier";

    protected int numberOfTransmissions;
    protected boolean isAccessPoint;
    protected double noiseFactor = 1.0;
    protected int speedMultiplier;
    protected int noiseMultiplier;
    protected final double[] transmitSpeeds;

    private int numberOfConcurrentConnections;

    public WifiNetworkInterface(Settings s) {
        super(s);
        this.numberOfTransmissions = 0;
        this.transmitSpeeds = s.getCsvDoubles(TRANSMIT_SPEEDS_S);
        this.isAccessPoint = s.getBoolean(ACCESS_POINT_FLAG);
        this.noiseMultiplier = s.getInt(NOISE_MULTIPLIER, 1);
        this.speedMultiplier = s.getInt(SPEED_MULTIPLIER, 100);
        this.interfacetype = "WIFI";
    }

    public WifiNetworkInterface(WifiNetworkInterface ni) {
        super(ni);
        this.isAccessPoint = ni.isAccessPoint;
        this.numberOfTransmissions = 0;
        this.noiseMultiplier = ni.noiseMultiplier;
        this.speedMultiplier = ni.speedMultiplier;
        this.transmitSpeeds = ni.transmitSpeeds;
        this.interfacetype = ni.interfacetype;
    }

    public boolean getIsAccessPoint() {
        return isAccessPoint;
    }



    @Override
    public NetworkInterface replicate() {
        return new WifiNetworkInterface(this);
    }

    @Override
    public int getTransmitSpeed(NetworkInterface ni) {
        double distance;
        double fractionIndex;
        double decimal;
        double speed;
        int index;

        /* distance to the other interface */
        distance = ni.getLocation().distance(this.getLocation());

        if (distance >= this.transmitRange) {
            return 0;
        }

        /* interpolate between the two speeds */
        fractionIndex = (distance / this.transmitRange) *
                (this.transmitSpeeds.length - 1);
        index = (int)(fractionIndex);
        decimal = fractionIndex - index;

        speed = this.transmitSpeeds[index] * (1-decimal) +
                this.transmitSpeeds[index + 1] * decimal;

        double transmissionSpeed = ((((double) speed * transmitRange * speedMultiplier) /
                (Math.sqrt((1.0*numberOfConcurrentConnections) *
                    Math.log(1.0*numberOfConcurrentConnections))) / numberOfTransmissions)
                * noiseFactor);

//        System.out.println("***");
//        System.out.println("Distance: " + distance);
//
//        System.out.println("Transmission Speed: " + transmissionSpeed);
//        System.out.println("Distance Speed Factor: " + speed);
//        System.out.println("TransmitRange: " + transmitRange);
//        System.out.println("nrofConcurrentConnections: " + numberOfConcurrentConnections);
//        System.out.println("nrofConcurrentTransmitssions: " + numberOfTransmissions);
//        System.out.println("noiseFactor: " + noiseFactor);
//        System.out.println("***");

        return (int) Math.floor(transmissionSpeed);
    }

    @Override
    public void connect(NetworkInterface anotherInterface) {
        if (isScanning()
                && anotherInterface.getHost().isRadioActive()
                && isWithinRange(anotherInterface)
                && !isConnected(anotherInterface)
                && (this != anotherInterface)) {
            // new contact within range

            Connection con = new VBRConnection(this.host, this,
                    anotherInterface.getHost(), anotherInterface);
            connect(con, anotherInterface);
        }
    }

    private double calculateNoiseFactor(Collection<WifiNetworkInterface> nearby) {

        double noiseFactor = nearby.stream()
                .filter(i -> i.isAccessPoint && i != this)
                .map(i -> (1 / i.getLocation().distance(this.getLocation())) * i.transmitRange * this.noiseMultiplier)
                .reduce(0.0, Double::sum);

        if(Double.isNaN(noiseFactor)) {
            return 0.0;
        }

        return 1 - (Math.min(noiseFactor, 99.0)/100);

    }

    @Override
    public void update() {

        if(optimizer == null) {
            return;
        }

        optimizer.updateLocation(this);
        for(int i=0; i<this.connections.size(); ){
            Connection con = this.connections.get(i);
            NetworkInterface anotherInterface = con.getOtherInterface(this);

            assert con.isUp() : "Connection " + con + " was down!";

            if (!isWithinRange(anotherInterface)) {
                disconnect(con, anotherInterface);
                connections.remove(i);
            } else {
                i++;
            }
        }

        Collection<WifiNetworkInterface> interfaces = optimizer.getNearInterfaces(this).stream()
                .filter(i -> i instanceof WifiNetworkInterface).map(i -> (WifiNetworkInterface) i).collect(Collectors.toList());


        for (WifiNetworkInterface i: interfaces) {
            if(this.isAccessPoint != i.isAccessPoint) {
                connect(i);
            }
        }

        this.numberOfTransmissions = 0;
        this.numberOfConcurrentConnections = 1;
        for (Connection con : this.connections) {
            if (con.getMessage() != null) {
                this.numberOfTransmissions++;
            }
            if (con.getOtherInterface(this).isTransferring()) {
                this.numberOfConcurrentConnections++;
            }
        }

        this.numberOfTransmissions = Math.max(this.numberOfTransmissions, 1);
        this.numberOfConcurrentConnections = Math.max(this.numberOfConcurrentConnections, 2);
        this.noiseFactor = this.calculateNoiseFactor(interfaces);
        // Based on the equation of Gupta and Kumar - and the transmission speed
        // is divided equally to all the ongoing transmissions
        

        for (Connection con : getConnections()) {
            con.update();
        }

    }

    @Override
    public void createConnection(NetworkInterface anotherInterface) {
        if(!(anotherInterface instanceof WifiNetworkInterface)) {
            return;
        }
        WifiNetworkInterface another = ((WifiNetworkInterface) anotherInterface);

        if(!isConnected(another) &&
            (this != another) &&
            this.isAccessPoint || another.isAccessPoint &&
            this.isWithinRange(another))
        {
            Connection con = new VBRConnection(this.host, this,
                    anotherInterface.getHost(), anotherInterface);
            connect(con,anotherInterface);
        }
    }

    /**
     * Returns true if this interface is actually transmitting data
     */
    public boolean isTransferring() {
        return (numberOfTransmissions > 0);
    }

    /**
     * Returns a string representation of the object.
     * @return a string representation of the object.
     */
    public String toString() {
        return "WifiNetworkInterface " + super.toString();
    }
}