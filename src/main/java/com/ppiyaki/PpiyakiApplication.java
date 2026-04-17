package com.ppiyaki;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PpiyakiApplication {

    public static void main(final String[] args) {
        SpringApplication.run(PpiyakiApplication.class, args);
    }

}
