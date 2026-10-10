package de.blinkt.openvpn.freevpn;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.IOException;
public class TunnelStopperTest {
    @Test public void retriesUntilDelayedManagementInterfaceStartsAndExits()throws Exception {
        class Delayed implements TunnelStopper.Transport {
            long now;int stops;boolean stopped;
            public long elapsed(){return now;}
            public void requestStop(){stops++;if(now>=1000)stopped=true;}
            public boolean inactive(){return stopped;}
            public void pause(){now+=200;}
        }
        Delayed delayed=new Delayed();TunnelStopper.await(delayed);
        assertTrue(delayed.stops>5);assertTrue(delayed.now>=2500);assertTrue(delayed.stopped);
    }
    @Test public void transientDisconnectedStatusCannotFinishCancellation()throws Exception {
        class Transient implements TunnelStopper.Transport {
            long now;
            public long elapsed(){return now;}
            public void requestStop(){}
            public boolean inactive(){return now<400||now>=2000;}
            public void pause(){now+=200;}
        }
        Transient connection=new Transient();TunnelStopper.await(connection);assertTrue(connection.now>=3500);
    }
    @Test public void unconfirmedStopTimesOutRatherThanClaimsSuccess()throws Exception {
        TunnelStopper.Transport stuck=new TunnelStopper.Transport(){long now;public long elapsed(){return now;}public void requestStop(){}public boolean inactive(){return false;}public void pause(){now+=200;}};
        try{TunnelStopper.await(stuck);fail("unconfirmed disconnect accepted");}catch(IOException expected){assertTrue(expected.getMessage().contains("确认"));}
    }
}
