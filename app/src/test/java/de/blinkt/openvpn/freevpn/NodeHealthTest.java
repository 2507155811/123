package de.blinkt.openvpn.freevpn;
import org.junit.Test;
import static org.junit.Assert.*;
public class NodeHealthTest {
    private NodeHealth h(long at,long latency,long speed){return new NodeHealth(null,at,latency,speed);}
    @Test public void certificationExpiresAfterTenMinutes(){assertTrue(h(1000,10,100).fresh(600999));assertFalse(h(1000,10,100).fresh(601000));}
    @Test public void futureCertificationIsNotAccepted(){assertFalse(h(1000,10,100).fresh(999));}
    @Test public void avoidsSwitchingForTinySpeedDifferences(){NodeHealth current=h(0,100,1000);assertFalse(h(0,100,1100).substantiallyBetterThan(current));assertTrue(h(0,100,1300).substantiallyBetterThan(current));}
    @Test public void highLatencyPenalizesDownloadRate(){assertTrue(h(0,10,1000).quality()>h(0,1000,1100).quality());}
    @Test public void unknownSpeedUsesMeasuredLatency(){assertTrue(h(0,50,0).quality()>h(0,500,0).quality());assertTrue(h(0,50,0).substantiallyBetterThan(null));}
}
