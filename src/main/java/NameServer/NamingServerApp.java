package NameServer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Properties;

/**
 * Class for launching the Java Spring REST Application.
 */
@SpringBootApplication
@EnableScheduling
public class NamingServerApp {
    /**
     * Main method for launching the Java Spring REST Application.
     * @param args Command line arguments
     */
    public static void main(String[]  args) {
        SpringApplication app = new SpringApplication(NamingServerApp.class);
        Properties properties = new Properties();
        properties.put("server.port", "8081");      // the REST aplication will be available at http://[NS-IP]:8081/
        properties.put("server.error.include-message","always");
        app.setDefaultProperties(properties);
        app.run(args);
    }

}
