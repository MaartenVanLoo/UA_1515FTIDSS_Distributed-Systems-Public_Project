package Client;
import Utils.Hashing;
import Utils.MessageType;

public class Client {
    public static void main(String[] args) {
         System.out.println("Client Started");
         System.out.println(Hashing.hash("Node0"));
         System.out.println(Hashing.hash("Node1"));
         System.out.println(Hashing.hash("Node2"));
         System.out.println(MessageType.Discovery.name());
         System.out.println(MessageType.valueOf("Discovery").name());
         System.out.println(MessageType.Discovery.toString());
    }

}
