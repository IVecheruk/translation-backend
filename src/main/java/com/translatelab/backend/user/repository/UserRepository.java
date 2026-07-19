package com.translatelab.backend.user.repository;

import com.translatelab.backend.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email); // Поиск пользователя при авторизации
    boolean existsByEmail(String email); // Проверка уникальности email при регистрации
}