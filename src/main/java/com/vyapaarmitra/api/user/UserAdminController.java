package com.vyapaarmitra.api.user;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.user.UserDtos.CreateUserRequest;
import com.vyapaarmitra.api.user.UserDtos.UpdateUserRequest;
import com.vyapaarmitra.api.user.UserDtos.UserResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('OWNER')")
public class UserAdminController {

    private final UserService userService;

    public UserAdminController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserResponse> list(@AuthenticationPrincipal AuthUser authUser) {
        return userService.list(authUser);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@AuthenticationPrincipal AuthUser authUser,
                               @Valid @RequestBody CreateUserRequest request) {
        return userService.create(authUser, request);
    }

    @PatchMapping("/{id}")
    public UserResponse update(@AuthenticationPrincipal AuthUser authUser,
                               @PathVariable UUID id,
                               @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(authUser, id, request);
    }
}
