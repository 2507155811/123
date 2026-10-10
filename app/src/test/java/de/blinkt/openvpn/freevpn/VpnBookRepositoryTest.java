package de.blinkt.openvpn.freevpn;
import org.junit.Test;
import org.json.*;
import java.io.*;
import static org.junit.Assert.*;
public class VpnBookRepositoryTest {
    private JSONObject server(String country,String ip)throws Exception{return new JSONObject().put("hostname","us16.vpnbook.com").put("countryCode",country).put("countryName","Example").put("ipAddress",ip);}
    private String config(){return "client\ndev tun1\nproto tcp\nremote 147.135.15.16 443\nauth-user-pass\nfast-io\n<ca>\nPUBLIC TEST CA\n</ca>\n";}
    @Test public void parsesOfficialFlightDirectoryWithoutEvaluatingScripts()throws Exception {
        String data="23:[\"$\",\"component\",null,{\"servers\":[{\"hostname\":\"us16.vpnbook.com\"}]}]";
        String html="<script>self.__next_f.push("+new JSONArray().put(1).put(data)+")</script>";
        assertEquals(1,VpnBookRepository.servers(html).length());
    }
    @Test public void officialTunDeviceNormalizesAndPreservesLoginRequirement()throws Exception {
        Node node=VpnBookRepository.node(server("US","147.135.15.16"),config());assertEquals("VPNBook",node.provider());assertTrue(node.config.contains("dev tun\n"));assertTrue(node.config.contains("auth-user-pass"));
    }
    @Test(expected=IOException.class) public void domesticProviderServerIsExcluded()throws Exception{VpnBookRepository.node(server("CN","147.135.15.16"),config());}
    @Test(expected=IOException.class) public void privateAddressIsExcluded()throws Exception{VpnBookRepository.node(server("US","192.168.1.1"),config());}
    @Test public void credentialFileAndFastIoArgumentsAreRejected(){assertFalse(NodeParser.safeConfig(config().replace("dev tun1","dev tun").replace("auth-user-pass","auth-user-pass /tmp/secret"),"147.135.15.16"));assertFalse(NodeParser.safeConfig(config().replace("dev tun1","dev tun")+"fast-io /tmp/script\n","147.135.15.16"));}
    @Test(expected=IOException.class) public void malformedPageIsRejected()throws Exception {VpnBookRepository.servers("<script>malicious()</script>");}
}
