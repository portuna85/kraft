package com.boardly.user.service;


import com.boardly.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;


@SpringBootTest
@Transactional
class UserServiceTest {
    @Autowired UserService userService;


    @Test
    void duplicate_username_throws() {
        userService.register("user1","pw","n","e@e.com");
        assertThatThrownBy(() -> userService.register("user1","pw2","n2","e2@e.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}