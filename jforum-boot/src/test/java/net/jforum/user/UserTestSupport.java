package net.jforum.user;

import java.time.Instant;

import net.jforum.domain.User;
import net.jforum.repository.UserRepository;
import net.jforum.security.ForumUserDetails;
import net.jforum.support.SequenceService;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Test helpers to create forum users and authenticate MockMvc requests as them. */
public final class UserTestSupport {

    private UserTestSupport() {
    }

    public static User createUser(UserRepository users, SequenceService sequences, PasswordEncoder encoder, String username, String password) {
        User u = new User();
        u.setId(sequences.next("users"));
        u.setUsername(username);
        u.setPassword(encoder.encode(password));
        u.setEmail(username.toLowerCase() + "@example.com");
        u.setRegistrationDate(Instant.now());
        u.getGroupIds().add(1);
        return users.save(u);
    }

    public static RequestPostProcessor as(User u) {
        return SecurityMockMvcRequestPostProcessors.user(new ForumUserDetails(u.getId(), u.getUsername(), u.getPassword(), true));
    }
}
