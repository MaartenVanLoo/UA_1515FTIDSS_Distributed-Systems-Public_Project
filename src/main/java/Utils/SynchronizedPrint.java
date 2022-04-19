package Utils;

public class SynchronizedPrint {
    public static final Object lock = new Object();
    public static void println(String s) {
        synchronized (lock) {
            System.out.println(s);
        }
    }

    public static void print(String s) {
        synchronized (lock) {
            System.out.print(s);
        }
    }

    public static void clearConsole(){
        try{
            if (System.getProperty("os.name").contains("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            }else{
                Runtime.getRuntime().exec("clear");
            }
            System.out.print("\033[H\033[2J");
            System.out.flush();
        }
        catch(Exception ignored){
            ignored.printStackTrace();
        }
    }
}
