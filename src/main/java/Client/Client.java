package Client;
import Utils.Hashing;

public class Client {
    public static void main(String[] args) {
         System.out.println("Client Started");
         System.out.println(Hashing.hash("Node0"));
         System.out.println(Hashing.hash("Node1"));
         System.out.println(Hashing.hash("Node2"));
    }

}
