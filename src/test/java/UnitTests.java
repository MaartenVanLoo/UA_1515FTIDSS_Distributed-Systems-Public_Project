import NameServer.NameServerController;
import Node.FileManager;
import Node.Node;
import Utils.Hashing;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.TreeMap;



public class UnitTests {

    protected static NameServerController ns;

    @BeforeAll
    static void beforeAll() {
        System.out.println("Before All");
        ns =  new NameServerController();
    }
    @BeforeEach
    void setUp() {
        System.out.println("before Each");
        ns.getNameServer().getIpMapping().clear();
    }
    @AfterEach
    void tearDown() {
        System.out.println("teardown");
        ns.getNameServer().getIpMapping().clear();
    }

    @Test
    public void testHash() throws IOException {
        String n0 = "Node0";
        String n1 = "Node1";
        String n2 = "Node2";
        String n3 = "Node3";
        String n4 = "Node4";
        String n5 = "Node5";

        System.out.println(n0 + "=>" + n0.hashCode() +"->" + Hashing.hash(n0));
        System.out.println(n1 + "=>" + n1.hashCode() +"->" + Hashing.hash(n1));
        System.out.println(n2 + "=>" + n2.hashCode() +"->" + Hashing.hash(n2));
        System.out.println(n3 + "=>" + n3.hashCode() +"->" + Hashing.hash(n3));
        System.out.println(n4 + "=>" + n4.hashCode() +"->" + Hashing.hash(n4));
        System.out.println(n5 + "=>" + n5.hashCode() +"->" + Hashing.hash(n5));

    }

    @Test
    public void addNodeUnique() throws Exception {
        TreeMap<Integer,String> idMap = ns.getNameServer().getIpMapping();
        ns.getNameServer().addNode(1, "192.168.3.55");
        System.out.println("nodes: "+idMap.toString());
        ns.getNameServer().addNode(2, "192.168.3.55");
        System.out.println("nodes: "+idMap.toString());
    }


    @Test
    public void addNodeExisting() throws Exception{
        Assumptions.assumeTrue(ns.getNameServer().addNode(2, "192.168.2.2"));
        Assumptions.assumeFalse(ns.getNameServer().addNode(2, "192.168.2.2"));
        Assertions.assertEquals(1, ns.getNameServer().getIpMapping().size());
    }
    @Test
    public void sendFileNameIP() throws Exception{
        ns.getNameServer().addNode(1000, "192.168.2.1");
        ns.getNameServer().addNode(2000, "192.168.2.2");
        ns.getNameServer().addNode(3000, "192.168.2.3");
        ns.getNameServer().addNode(4000, "192.168.2.4");
        ns.getNameServer().addNode(5000, "192.168.2.5");

        int hash = -1;
        String generatedString = "";
        while (hash<= 2000 || hash >=3000){
            byte[] array = new byte[7];
            new Random().nextBytes(array);
            generatedString = new String(array, StandardCharsets.UTF_8);
            hash = Hashing.hash(generatedString);
            //System.out.println(hash + "\t=>\t" + generatedString);
        }
        Assertions.assertEquals("192.168.2.2", ns.getFile(generatedString));
        Assertions.assertEquals(ns.getNameServer().getIpMapping().get(2000), ns.getFile(generatedString));

    }
    @Test
    public void sendFileNameLower() throws Exception{
        ns.getNameServer().addNode(1000, "192.168.2.1");
        ns.getNameServer().addNode(2000, "192.168.2.2");
        ns.getNameServer().addNode(3000, "192.168.2.3");
        ns.getNameServer().addNode(4000, "192.168.2.4");
        ns.getNameServer().addNode(5000, "192.168.2.5");

        int hash = 65536;
        String generatedString = "";
        while (hash >= 1000){
            byte[] array = new byte[7];
            new Random().nextBytes(array);
            generatedString = new String(array, StandardCharsets.UTF_8);
            hash = Hashing.hash(generatedString);
            //System.out.println(hash + "\t=>\t" + generatedString);
        }
        Assertions.assertEquals("192.168.2.5", ns.getFile(generatedString));
        Assertions.assertEquals(ns.getNameServer().getIpMapping().get(5000), ns.getFile(generatedString));
    }
    @Test
    public void sendFileNameRemove() throws Exception{
        ns.getNameServer().addNode(5, "192.168.5.5");
        //test5.sendFile();
        ns.getNameServer().deleteNode(5);

    }

    @Test
    public void status() throws IOException {
        Node n = new Node("def");
        n.printStatus();
    }


    @Test
    public void getAllNodes() {
        ns.getNameServer().addNode(1000, "192.168.2.1");
        ns.getNameServer().addNode(2000, "192.168.2.2");
        ns.getNameServer().addNode(3000, "192.168.2.3");
        ns.getNameServer().addNode(4000, "192.168.2.4");
        ns.getNameServer().addNode(5000, "192.168.2.5");
        System.out.println(ns.getAllNodes());
    }

}
