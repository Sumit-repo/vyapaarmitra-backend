package com.vyapaarmitra.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class VyapaarmitraBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(VyapaarmitraBackendApplication.class, args);
	}

}
