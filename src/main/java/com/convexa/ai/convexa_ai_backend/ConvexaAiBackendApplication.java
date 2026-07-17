package com.convexa.ai.convexa_ai_backend;

import com.convexa.ai.convexa_ai_backend.entity.Company;
import com.convexa.ai.convexa_ai_backend.entity.User;
import com.convexa.ai.convexa_ai_backend.repository.CompanyRepository;
import com.convexa.ai.convexa_ai_backend.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.List;

@SpringBootApplication
public class ConvexaAiBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(ConvexaAiBackendApplication.class, args);
	}

	@Bean
	public CommandLineRunner demoSeeder(CompanyRepository companyRepository, UserRepository userRepository) {
		return args -> {
			Company demoCompany = companyRepository.findByCompanySlug("convexa-demo").orElseGet(() -> {
				Company company = Company.builder()
						.companyName("Convexa Demo")
						.companySlug("convexa-demo")
						.industry("Technology")
						.companySize("50-250")
						.website("https://convexa.ai")
						.build();
				return companyRepository.save(company);
			});

			List<User> users = userRepository.findAll();
			for (User user : users) {
				if (user.getCompany() == null) {
					user.setCompany(demoCompany);
					userRepository.save(user);
				}
			}
		};
	}
}
