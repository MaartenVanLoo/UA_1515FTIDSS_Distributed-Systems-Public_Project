import NameServer.NameServer;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.TreeMap;



public class UnitTests {
    protected static NameServer ns;

    @BeforeAll
    static void beforeAll() {
        System.out.println("Before All");
        ns =  new NameServer();
    }
    @BeforeEach
    void setUp() {
        System.out.println("before Each");
        ns.getIdMap().clear();
    }
    @AfterEach
    void tearDown() {
        System.out.println("teardown");
        ns.getIdMap().clear();
    }



    @Test
    public void testHash() throws IOException {
        String n0 = "Node0";
        String n1 = "Node1";
        String n2 = "Node2";
        String n3 = "Node3";
        String n4 = "Node4";
        String n5 = "Node5";

        System.out.println(n0 + "=>" + n0.hashCode() +"->" + this.ns.hash(n0));
        System.out.println(n1 + "=>" + n1.hashCode() +"->" + ns.hash(n1));
        System.out.println(n2 + "=>" + n2.hashCode() +"->" + ns.hash(n2));
        System.out.println(n3 + "=>" + n3.hashCode() +"->" + ns.hash(n3));
        System.out.println(n4 + "=>" + n4.hashCode() +"->" + ns.hash(n4));
        System.out.println(n5 + "=>" + n5.hashCode() +"->" + ns.hash(n5));

    }

    @Test
    public void addNodeUnique() throws Exception {
        TreeMap<Integer,String> idMap = ns.getIdMap();
        ns.addNode(1, "192.168.3.55");
        System.out.println("nodes: "+idMap.toString());
        ns.addNode(2, "192.168.3.55");
        System.out.println("nodes: "+idMap.toString());
    }


    @Test
    public void addNodeExisting() throws Exception{
        Assumptions.assumeTrue(ns.addNode(2, "192.168.2.2"));
        Assumptions.assumeFalse(ns.addNode(2, "192.168.2.2"));
        Assertions.assertEquals(1, ns.getIdMap().size());
    }
    @Test
    public void sendFileNameIP() throws Exception{
        ns.addNode(1000, "192.168.2.1");
        ns.addNode(2000, "192.168.2.2");
        ns.addNode(3000, "192.168.2.3");
        ns.addNode(4000, "192.168.2.4");
        ns.addNode(5000, "192.168.2.5");

        int hash = -1;
        String generatedString = "";
        while (hash<= 2000 || hash >=3000){
            byte[] array = new byte[7];
            new Random().nextBytes(array);
            generatedString = new String(array, StandardCharsets.UTF_8);
            hash = ns.hash(generatedString);
            //System.out.println(hash + "\t=>\t" + generatedString);
        }
        Assertions.assertEquals("192.168.2.2", ns.getLocation(generatedString));
        Assertions.assertEquals(ns.getIdMap().get(2000), ns.getLocation(generatedString));

    }
    @Test
    public void sendFileNameLower() throws Exception{
        ns.addNode(1000, "192.168.2.1");
        ns.addNode(2000, "192.168.2.2");
        ns.addNode(3000, "192.168.2.3");
        ns.addNode(4000, "192.168.2.4");
        ns.addNode(5000, "192.168.2.5");

        int hash = 65536;
        String generatedString = "";
        while (hash >= 1000){
            byte[] array = new byte[7];
            new Random().nextBytes(array);
            generatedString = new String(array, StandardCharsets.UTF_8);
            hash = ns.hash(generatedString);
            //System.out.println(hash + "\t=>\t" + generatedString);
        }
        Assertions.assertEquals("192.168.2.5", ns.getLocation(generatedString));
        Assertions.assertEquals(ns.getIdMap().get(5000), ns.getLocation(generatedString));
    }
    @Test
    public void sendFileNameRemove() throws Exception{
        ns.addNode(5, "192.168.5.5");
        //test5.sendFile();
        ns.removeNode(5);

    }
    @Test
    public void askTwoPCs() throws Exception{

    }

}
