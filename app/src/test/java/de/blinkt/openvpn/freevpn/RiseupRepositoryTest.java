package de.blinkt.openvpn.freevpn;
import org.junit.Test;import org.json.*;import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;import static org.junit.Assert.*;
public class RiseupRepositoryTest {
 private String ca()throws Exception{try(InputStream in=getClass().getResourceAsStream("/leap-test-ca.pem")){return new String(in.readAllBytes(),StandardCharsets.US_ASCII);}}
 private JSONObject directory(String country,String ip,String transport,String proto)throws Exception {
  JSONObject caps=new JSONObject().put("protocols",new JSONArray().put(proto)).put("ports",new JSONArray().put(443)).put("transport",new JSONArray().put(transport));
  JSONObject gate=new JSONObject().put("ip_address",ip).put("location","test").put("capabilities",caps);
  return new JSONObject().put("gateways",new JSONArray().put(gate)).put("locations",new JSONObject().put("test",new JSONObject().put("country_code",country).put("name","Unit Test"))).put("openvpn_configuration",new JSONObject().put("cipher","AES-128-CBC").put("auth","SHA512").put("keepalive","10 30"));
 }
 private String credentials()throws Exception{return ca()+"\n-----BEGIN PRIVATE KEY-----\nUNIT TEST PLACEHOLDER ONLY\n-----END PRIVATE KEY-----\n";}
 @Test public void convertsPublicAnonymousTcpGatewayAndLabelsProvider()throws Exception{List<Node> n=RiseupRepository.nodes(directory("US","8.8.8.8","openvpn","tcp"),credentials(),ca());assertEquals(1,n.size());assertEquals("RiseupVPN",n.get(0).provider());assertTrue(NodeParser.safeConfig(n.get(0).config,"8.8.8.8"));assertEquals(443,TcpEndpoint.port(n.get(0).config));assertTrue(n.get(0).config.contains("data-ciphers AES-256-GCM:AES-128-GCM:AES-128-CBC"));assertFalse(n.get(0).config.contains("auth-user-pass"));}
 @Test(expected=IOException.class) public void rejectsDomesticGateway()throws Exception{RiseupRepository.nodes(directory("CN","8.8.8.8","openvpn","tcp"),credentials(),ca());}
 @Test(expected=IOException.class) public void rejectsPrivateGateway()throws Exception{RiseupRepository.nodes(directory("US","10.0.0.1","openvpn","tcp"),credentials(),ca());}
 @Test(expected=IOException.class) public void doesNotInventTcpEndpointForUdp()throws Exception{RiseupRepository.nodes(directory("US","8.8.8.8","openvpn","udp"),credentials(),ca());}
 @Test(expected=IOException.class) public void rejectsUnsupportedTransport()throws Exception{RiseupRepository.nodes(directory("US","8.8.8.8","obfs4","tcp"),credentials(),ca());}
 @Test(expected=IOException.class) public void rejectsDirectiveInjectionFromDirectory()throws Exception{JSONObject d=directory("US","8.8.8.8","openvpn","tcp");d.getJSONObject("openvpn_configuration").put("cipher","AES-128-CBC\nplugin /tmp/x");RiseupRepository.nodes(d,credentials(),ca());}
 @Test(expected=IOException.class) public void requiresAnonymousCertificate()throws Exception{RiseupRepository.nodes(directory("US","8.8.8.8","openvpn","tcp"),"error",ca());}
}
