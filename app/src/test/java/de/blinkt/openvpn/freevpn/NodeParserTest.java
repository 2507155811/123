package de.blinkt.openvpn.freevpn;

import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class NodeParserTest {
    private static final String HEADER="*vpn_servers\n#HostName,IP,Score,Ping,Speed,CountryLong,CountryShort,NumVpnSessions,OpenVPN_ConfigData_Base64\n";
    private String config(String ip){return "client\ndev tun\nproto tcp\nremote "+ip+" 443\n<ca>\nTEST CA\n</ca>\n";}
    private String line(String country,String ip,String config){return "node,"+ip+",100,20,50000000,\"Example, Region\","+country+",4,"+Base64.getEncoder().encodeToString(config.getBytes(StandardCharsets.UTF_8))+"\n";}
    @Test public void acceptsValidRowsAndQuotedCountry(){List<Node> n=NodeParser.parse(HEADER+line("JP","8.8.8.8",config("8.8.8.8")));assertEquals(1,n.size());assertEquals("Example, Region",n.get(0).country);assertEquals("50.0 Mbps",n.get(0).speedLabel());}
    @Test public void excludesChinaHongKongAndMacao(){String s=HEADER;for(String c:Arrays.asList("CN","HK","MO"))s+=line(c,"8.8.8.8",config("8.8.8.8"));assertTrue(NodeParser.parse(s).isEmpty());}
    @Test public void acceptsLowerCaseCodeAndSearchInChinese(){Node n=NodeParser.parse(HEADER+line("jp","8.8.8.8",config("8.8.8.8"))).get(0);assertTrue(n.matches("日本"));assertTrue(n.matches("JP"));assertFalse(n.matches("美国"));}
    @Test public void deduplicatesButKeepsValidRowAfterMalformedOne(){String row=line("US","8.8.8.8",config("8.8.8.8"));assertEquals(1,NodeParser.parse(HEADER+"bad,row\n"+row+row).size());}
    @Test public void blocksPrivateAndMalformedAddresses(){for(String ip:Arrays.asList("127.0.0.1","10.1.1.1","172.16.0.1","192.168.1.1","169.254.2.1","100.64.1.1","224.1.1.1","999.1.1.1","localhost"))assertFalse(ip,NodeParser.publicIpv4(ip));}
    @Test public void rejectsProfileWithoutEmbeddedCa(){assertFalse(NodeParser.safeConfig("client\nremote 8.8.8.8 443\n","8.8.8.8"));}
    @Test public void blocksScriptsPluginsAndExternalFiles(){for(String option:Arrays.asList("up /tmp/script","plugin /tmp/plugin","ca /etc/cert","script-security 2","management 127.0.0.1 9999","setenv LD_PRELOAD /tmp/lib","tls-cert-profile insecure"))assertFalse(option,NodeParser.safeConfig(config("8.8.8.8")+option+"\n","8.8.8.8"));}
    @Test public void rejectsRemoteMismatchAndUnclosedInlineBlock(){assertFalse(NodeParser.safeConfig(config("1.1.1.1"),"8.8.8.8"));assertFalse(NodeParser.safeConfig(config("8.8.8.8")+"<key>\nabc","8.8.8.8"));}
    @Test(expected=IllegalArgumentException.class) public void rejectsHtmlInsteadOfCsv(){NodeParser.parse("<html>Bad gateway</html>");}
    @Test public void blankLatencySortsLast(){String row=line("JP","8.8.8.8",config("8.8.8.8")).replace(",100,20,",",100,,");assertEquals(Integer.MAX_VALUE,NodeParser.parse(HEADER+row).get(0).ping);}
    @Test public void preservesTrailingAndEscapedCsvFields(){assertEquals(Arrays.asList("a","quoted \"value\"", ""),NodeParser.columns("a,\"quoted \"\"value\"\"\","));}
}
