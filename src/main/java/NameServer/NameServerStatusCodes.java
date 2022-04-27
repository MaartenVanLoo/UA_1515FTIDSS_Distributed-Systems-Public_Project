package NameServer;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.concurrent.locks.Lock;

//note: this also works!
// throw new ResponseStatusException(HttpStatus.CONFLICT, "Node with id " + id + " already exists on the network at " + ip);

public class NameServerStatusCodes {
    //<editor-fold desc="200 status codes">
    @ResponseStatus(HttpStatus.CREATED)
    public static void NodeCreated(){};

    //</editor-fold>

    //<editor-fold desc="Node errors">
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
         * @param nodeId NodeId of the node that already exists
         */
        public NodeNotFoundException(int nodeId) {
            super("Node with id " + nodeId + " can not be found on the network");
        }
    }

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
            lock.unlock();
        }
    }
    //</editor-fold>

    //<editor-fold desc="json errors">
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
