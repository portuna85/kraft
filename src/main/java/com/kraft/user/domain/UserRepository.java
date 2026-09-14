package com.kraft.user.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailHash(String emailHash);

    boolean existsByEmailHash(String emailHash);

    boolean existsByName(String name);
}
