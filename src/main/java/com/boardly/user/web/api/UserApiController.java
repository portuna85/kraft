package com.boardly.user.web.api;

import com.boardly.core.response.ApiResponse;
import com.boardly.user.service.UserService;
import com.boardly.user.web.dto.SignUpRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserApiController {
    private final UserService userService;

    @PostMapping("/signup")
    public ResponseEntity<?> signUp(@RequestBody @Valid SignUpRequest req){
        Long id = userService.register(req.username(), req.password(), req.nickname(), req.email());
        return ResponseEntity.ok().body(java.util.Map.of("id", id));
    }
}