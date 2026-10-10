package de.blinkt.openvpn.freevpn;
import org.junit.Test;import static org.junit.Assert.*;
public class TcpEndpointTest {
 @Test public void readsTcpPortEvenWhenProtoFollowsRemote(){assertEquals(443,TcpEndpoint.port("remote 8.8.8.8 443\nproto tcp-client\n"));}
 @Test public void udpIsNotCertifiedByTcpChecks(){assertEquals(-1,TcpEndpoint.port("proto udp\nremote 8.8.8.8 1194\n"));assertEquals(-1,TcpEndpoint.port("remote 8.8.8.8 1194\n"));}
 @Test public void remoteProtocolOverridesGlobalProtocol(){assertEquals(80,TcpEndpoint.port("proto udp\nremote 8.8.8.8 80 tcp\n"));assertEquals(-1,TcpEndpoint.port("proto tcp\nremote 8.8.8.8 80 udp\n"));}
 @Test public void invalidPortsAreRejected(){for(String port:new String[]{"0","65536","bad"})assertEquals(-1,TcpEndpoint.port("proto tcp\nremote 8.8.8.8 "+port));}
 @Test public void tcpEvidenceNeverClaimsTunnelVerificationOrSpeed(){NodeHealth h=NodeHealth.reachable(null,1000,23);assertFalse(h.tunnelVerified);assertEquals(0,h.bytesPerSecond);assertEquals(23,h.latencyMs);assertTrue(h.fresh(1001));assertTrue(new NodeHealth(null,1000,23,50).tunnelVerified);}
}
