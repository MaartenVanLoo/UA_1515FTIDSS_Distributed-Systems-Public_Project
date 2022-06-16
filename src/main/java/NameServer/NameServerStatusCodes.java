package NameServer;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.concurrent.locks.Lock;

/**
 * Class with a collection of exception classes that can be thrown by the NameServerController.<br>
 * <br>
 * note: the following also works!<br>
 * throw new ResponseStatusException(HttpStatus.CONFLICT, "Node with id " + id + " already exists on the network at " + ip);
 */
public class NameServerStatusCodes {

    //<editor-fold desc="Node errors">

    /**
     * Exception thrown when the node that has to be modified or deleted doesn't exist.
     */
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class NodeNotFoundException extends RuntimeException{
        /**
         * Throws the appropriate HttpsStatus status codes. If a lock is specified,
         * the lock will be released.
         * This error sends a HttpsStatus.NOT_FOUND 404.
         */
        public NodeNotFoundException() {
        }

        /**
         * Throws the appropriate HttpsStatus status codes. If a lock is specified,
         * the lock will be released.
         * This error sends a HttpsStatus.NOT_FOUND 404.
         * @param message Message to be displayed
         */
        public NodeNotFoundException(String message) {
            super(message);
        }
        /**
         * Throws the appropriate HttpsStatus status codes. If a lock is specified,
         * the lock will be released.
         * This error sends a HttpsStatus.NOT_FOUND 404.
         * @param message Message to be displayed
         */
        public NodeNotFoundException(String message, Lock lock) {
            super(message);
            if (lock != null) {
                lock.unlock();
            }
        }

        /**
         * Throws the appropriate HttpsStatus status codes. If a lock is specified,
         * the lock will be released.
         * This error sends a HttpsStatus.NOT_FOUND 404.
         * @param nodeId NodeId of the node that cannot be found
         */
        public NodeNotFoundException(int nodeId) {
            super("Node with id " + nodeId + " can not be found on the network");
        }


    }

    /**
     * Exception thrown when the node that has to be created, already exists.
     */
    @ResponseStatus(HttpStatus.CONFLICT)
    public static class NodeAlreadyExistsException extends RuntimeException{
        /**
         * Throws the appropriate HttpsStatus status codes. If a lock is specified,
         * the lock will be released.
         * This error sends a HttpsStatus.CONFLICT 409.
         */
        public NodeAlreadyExistsException() {
        }

        /**
         * Throws the appropriate HttpsStatus status codes. If a lock is specified,
         * the lock will be released.
         * This error sends a HttpsStatus.CONFLICT 409.
         * @param message Message to be displayed
         */
        public NodeAlreadyExistsException(String message) {
            super(message);
        }

        /**
         * Throws the appropriate HttpsStatus status codes. If a lock is specified,
         * the lock will be released.
         * This error sends a HttpsStatus.CONFLICT 409.
         * @param nodeId NodeId of the node that already exists
         */
        public NodeAlreadyExistsException(int nodeId) {
            super("Node with id " + nodeId + " already exists on the network");
        }

        /**
         * Throws the appropriate HttpsStatus status codes. If a lock is specified,
         * the lock will be released.
         * This error sends a HttpsStatus.CONFLICT 409.
         * @param nodeId NodeId of the node that already exists
         * @param ip IP of the node that already exists
         */
        public NodeAlreadyExistsException(int nodeId, String ip) {
            super("Node with id " + nodeId + " and ip " + ip + " already exists on the network");
        }

        /**
         * Throws the appropriate HttpsStatus status codes. If a lock is specified,
         * the lock will be released.
         * This error sends a HttpsStatus.CONFLICT 409.
         * @param nodeId NodeId of the node that already exists
         * @param ip IP of the node that already exists
         * @param lock Lock to be released
         */
        public NodeAlreadyExistsException(int nodeId, String ip, Lock lock) {
            super("Node with id " + nodeId + " and ip " + ip + " already exists on the network");
            if (lock != null) {
                lock.unlock();
            }
        }
    }
    //</editor-fold>

    //<editor-fold desc="json errors">

    /**
     * Exception thrown when a field is missing in the json.
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public static class JSONFieldNotFoundException extends RuntimeException{
        /**
         * Throws the appropriate HttpsStatus status codes. If a lock is specified,
         * the lock will be released.
         * This error sends a HttpsStatus.BAD_REQUEST 400.
         * @param message Message to be displayed
         */
        public JSONFieldNotFoundException(String message) {
            super(message);
        }

        /**
         * Throws the appropriate HttpsStatus status codes. If a lock is specified,
         * the lock will be released.
         * This error sends a HttpsStatus.BAD_REQUEST 400.
         * @param message Message to be displayed
         * @param lock Lock to be released
         */
        public JSONFieldNotFoundException(String message, Lock lock) {
            super(message);
            lock.unlock();
        }
    }

    /**
     * Exception thrown when a JSON has the wrong format.
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public static class JSONInvalidFormatException extends RuntimeException{

        /**
         * Throws the appropriate HttpsStatus status codes. If a lock is specified,
         * the lock will be released.
         * This error sends a HttpsStatus.BAD_REQUEST 400.
         * @param message Message to be displayed
         */
        public JSONInvalidFormatException(String message) {
            super(message);
        }

        /**
         *  Throws the appropriate HttpsStatus status codes. If a lock is specified,
         *  the lock will be released.
         *  This error sends a HttpsStatus.BAD_REQUEST 400.
         * @param message Message to be displayed
         * @param lock Lock to be released
         */
        public JSONInvalidFormatException(String message, Lock lock){
            super(message);
            lock.unlock();
        }
    }
    //</editor-fold>
}
