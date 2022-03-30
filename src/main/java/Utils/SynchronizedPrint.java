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


}
