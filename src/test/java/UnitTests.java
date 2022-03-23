import org.junit.*;
import org.junit.runner.RunWith;
import Client.*;
import NameServer.*;
import Node.*;
import org.mockito.Mockito;
import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.autoconfigure.couchbase.CouchbaseProperties;

import java.io.IOException;
import java.util.Set;
import java.util.TreeMap;

public class UnitTests {

    @BeforeClass
    public static void init(){

    }
    @AfterClass
    public static void clean(){

    }

    @Test
    public void testHash() throws IOException {
        NameServer ns = new NameServer();
        ns.
    }

    @Test
    public void addNodeUnique() throws Exception {
        NameServer test = new NameServer();
        TreeMap<Integer,String> idMap = test.getIdMap();
        test.addNode(1, "192.168.3.55");
        System.out.println("nodes: "+idMap.toString());
        test.addNode(2, "192.168.3.55");
        System.out.println("nodes: "+idMap.toString());
    }


    @Test
    public void addNodeExisting() throws Exception{
        NameServer test2 = new NameServer();
        test2.addNode(2, "192.168.2.2");
        test2.addNode(2, "192.168.2.2");
    }
    @Test
    public void sendFileNameIP() throws Exception{

    }
    @Test
    public void sendFileNameLower() throws Exception{

    }
    @Test
    public void sendFileNameRemove() throws Exception{
        NameServer test5 = new NameServer();
        test5.addNode(5, "192.168.5.5");
        //test5.sendFile();
        test5.removeNode(5, "ip192.168.5.5");

    }
    @Test
    public void askTwoPCs() throws Exception{

    }

}
