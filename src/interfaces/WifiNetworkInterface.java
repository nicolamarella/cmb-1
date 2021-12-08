package interfaces;

import core.Connection;
import core.NetworkInterface;
import core.Settings;
import core.VBRConnection;

import java.util.Collection;


/**
 * A combination of DistanceCapacityInterface and InterferenceLimitedInterface
 * simulating the connection of Access Points and mobile nodes and taking into account
 * the signal noise from other nearby access points and their signal strength.
 */
public class WifiNetworkInterface extends NetworkInterface {

    public static final String TRANSMIT_SPEEDS_S = "transmitSpeeds";
    public static final String ACCESS_POINT_FLAG = "isAccessPoint";
    public static final String TRANSMIT_POWER = "transmitPower";

    protected int currentTransmitSpeed;
    protected int numberOfTransmissions;
    protected boolean isAccessPoint;
    protected double noiseFactor;
    protected double transmitPower;
    protected final double[] transmitSpeeds;

    public WifiNetworkInterface(Settings s) {
        super(s);
        this.currentTransmitSpeed = 0;
        this.numberOfTransmissions = 0;
        this.noiseFactor = 0;
        this.transmitSpeeds = s.getCsvDoubles(TRANSMIT_SPEEDS_S);
        this.isAccessPoint = s.getBoolean(ACCESS_POINT_FLAG, false);
        this.transmitPower = s.getDouble(TRANSMIT_POWER, 1);
        this.transmitRange *= this.transmitPower;


    }

    public WifiNetworkInterface(WifiNetworkInterface ni) {
        super(ni);
        this.transmitRange = ni.transmitRange;
        this.transmitSpeed = ni.transmitSpeed;
        this.isAccessPoint = ni.isAccessPoint;
        this.currentTransmitSpeed = 0;
        this.numberOfTransmissions = 0;
        this.transmitPower = 0;
        this.transmitSpeeds = ni.transmitSpeeds;
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

        return (int)(speed * this.currentTransmitSpeed);
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
                .filter(i -> i instanceof WifiNetworkInterface).map(i -> (WifiNetworkInterface) i).toList();

        this.noiseFactor = interfaces.stream()
                .filter(i -> i.isAccessPoint)
                .map(i -> (1 / i.getLocation().distance(this.getLocation())) * i.transmitPower)
                .reduce(0.0, Double::sum);

        if(this.noiseFactor > 99.0) {
            this.noiseFactor = 99.0;
        }


        for (WifiNetworkInterface i: interfaces) {
            if(this.isAccessPoint != i.isAccessPoint) {
                connect(i);
            }
        }

        numberOfTransmissions = 0;
        int numberOfActive = 1;
        for (Connection con : this.connections) {
            if (con.getMessage() != null) {
                numberOfTransmissions++;
            }
            if (con.getOtherInterface(this).isTransferring()) {
                numberOfActive++;
            }
        }

        int ntrans = numberOfTransmissions;
        if ( numberOfTransmissions < 1) ntrans = 1;
        if ( numberOfActive <2 ) numberOfActive = 2;

        // Based on the equation of Gupta and Kumar - and the transmission speed
        // is divided equally to all the ongoing transmissions
        currentTransmitSpeed = (int)Math.floor(((double)transmitSpeed /
                (Math.sqrt((1.0*numberOfActive) *
                        Math.log(1.0*numberOfActive))) /
                ntrans ) *  (1 - (this.noiseFactor / 100)));

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