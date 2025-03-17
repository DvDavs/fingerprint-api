package com.example.fingerprint_api.repository;

import com.example.fingerprint_api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    // Con Spring Data JPA los métodos CRUD básicos ya están definidos.
}
