package NameServer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Properties;

@SpringBootApplication
@EnableScheduling
public class NamingServerApp {
    public static void main(String[]  args) {
        SpringApplication app = new SpringApplication(NamingServerApp.class);
        Properties properties = new Properties();
        properties.put("server.port", "8081");
        app.setDefaultProperties(properties);
        app.run(args);
    }

}
