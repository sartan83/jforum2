package net.jforum.repository;

import java.util.List;
import java.util.Optional;
import net.jforum.domain.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserRepository extends MongoRepository<User, Integer> {

    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    List<User> findByGroupIdsContaining(Integer groupId);

    Page<User> findByDeletedFalse(Pageable pageable);

    Page<User> findByUsernameContainingIgnoreCase(String username, Pageable pageable);

    Optional<User> findTopByDeletedFalseOrderByIdDesc();

    long countByDeletedFalse();

    Page<User> findByGroupIdsContaining(Integer groupId, Pageable pageable);

    long countByGroupIdsContaining(Integer groupId);

    boolean existsByUsernameIgnoreCase(String username);

    Optional<User> findBySecurityHashAndEmailIgnoreCase(String securityHash, String email);
}
