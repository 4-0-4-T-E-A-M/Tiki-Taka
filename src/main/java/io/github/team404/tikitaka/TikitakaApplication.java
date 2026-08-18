package io.github.team404.tikitaka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class TikitakaApplication {

	public static void main(String[] args) {
		SpringApplication.run(TikitakaApplication.class, args);
	}

}
