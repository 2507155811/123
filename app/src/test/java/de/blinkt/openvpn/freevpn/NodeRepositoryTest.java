package de.blinkt.openvpn.freevpn;
import org.junit.Test;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import static org.junit.Assert.*;
public class NodeRepositoryTest {
    @Test public void explainsConnectionFailureWithoutRawAddresses(){
        assertTrue(NodeRepository.explain(new ConnectException("private address")).contains("切换网络"));
        assertFalse(NodeRepository.explain(new ConnectException("private address")).contains("private address"));
        assertTrue(NodeRepository.explain(new SocketTimeoutException()).contains("超时"));
        assertTrue(NodeRepository.explain(new UnknownHostException()).contains("DNS"));
    }
    @Test public void invalidImportDoesNotOverwriteCache() throws Exception {
        File directory=Files.createTempDirectory("aero-test").toFile();File cache=new File(directory,"vpngate.csv");
        try {
            Files.write(cache.toPath(),"original".getBytes());
            try{new NodeRepository(directory).importList(new ByteArrayInputStream("<html>error</html>".getBytes()));fail("invalid input accepted");}catch(IOException expected){}
            assertEquals("original",new String(Files.readAllBytes(cache.toPath())));
        }finally{cache.delete();directory.delete();}
    }

    private static String list(long at,String ip){
        String config="client\ndev tun\nremote "+ip+" 443\n<ca>\nTEST CA\n</ca>\n";
        return "#AeroFetchedAt="+at+"\n*vpn_servers\n#HostName,IP,Score,Ping,Speed,CountryLong,CountryShort,NumVpnSessions,OpenVPN_ConfigData_Base64\nnode,"+ip+",100,20,10000000,Japan,JP,1,"+java.util.Base64.getEncoder().encodeToString(config.getBytes(java.nio.charset.StandardCharsets.UTF_8))+"\n";
    }
    private static ByteArrayInputStream input(String value){return new ByteArrayInputStream(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    @Test public void bundledListIsAvailableWithoutNetworkAndKeepsCollectionTime()throws Exception {
        File directory=Files.createTempDirectory("aero-seed").toFile();
        try{
            long at=System.currentTimeMillis()-2*3_600_000;
            NodeRepository repository=new NodeRepository(directory);
            NodeRepository.Result seeded=repository.seed(input(list(at,"8.8.8.8")));
            assertEquals(1,seeded.nodes.size());assertEquals(at,seeded.fetchedAt);
            NodeRepository.Result cached=repository.load(false);
            assertTrue(cached.cached);assertEquals(at,cached.fetchedAt);assertTrue(cached.nodes.get(0).matches("日本"));
        }finally{new File(directory,"vpngate.csv").delete();directory.delete();}
    }
    @Test public void olderMirrorCannotReplaceNewerCache()throws Exception {
        File directory=Files.createTempDirectory("aero-mirror").toFile();
        try{
            long at=System.currentTimeMillis();NodeRepository repository=new NodeRepository(directory);
            repository.importList(input(list(at,"8.8.8.8")));
            NodeRepository.Result result=repository.importList(input(list(at-3_600_000,"1.1.1.1")));
            assertEquals("8.8.8.8",result.nodes.get(0).ip);assertEquals(at,result.fetchedAt);
        }finally{new File(directory,"vpngate.csv").delete();directory.delete();}
    }
    @Test public void weekOldSeedIsNotPresentedAsFresh()throws Exception {
        File directory=Files.createTempDirectory("aero-expired").toFile();
        try{
            try{new NodeRepository(directory).seed(input(list(System.currentTimeMillis()-8L*24*3_600_000,"8.8.8.8")));fail("expired seed accepted");}catch(IOException expected){}
            assertFalse(new File(directory,"vpngate.csv").exists());
        }finally{directory.delete();}
    }
    @Test public void futureCollectionDateIsRejected()throws Exception {
        try{NodeRepository.timestamp("#AeroFetchedAt="+(System.currentTimeMillis()+3_600_000)+"\n",0);fail("future timestamp accepted");}catch(IOException expected){}
    }
}
