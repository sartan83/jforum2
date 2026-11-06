package net.jforum.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "net.jforum")
@EnableScheduling
public class JForumApplication
{
	public static void main(String[] args)
	{
		SpringApplication.run(JForumApplication.class, args);
	}
}
