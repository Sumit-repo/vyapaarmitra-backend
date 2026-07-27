package com.vyapaarmitra.api.user;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.subscription.PlanCatalog.Feature;
import com.vyapaarmitra.api.subscription.PlanGuard;
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
    private final PlanGuard planGuard;

    public UserAdminController(UserService userService, PlanGuard planGuard) {
        this.userService = userService;
        this.planGuard = planGuard;
    }

    @GetMapping
    public List<UserResponse> list(@AuthenticationPrincipal AuthUser authUser) {
        return userService.list(authUser);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@AuthenticationPrincipal AuthUser authUser,
                               @Valid @RequestBody CreateUserRequest request) {
        planGuard.requireFeature(authUser, Feature.STAFF, "staff");
        return userService.create(authUser, request);
    }

    @PatchMapping("/{id}")
    public UserResponse update(@AuthenticationPrincipal AuthUser authUser,
                               @PathVariable UUID id,
                               @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(authUser, id, request);
    }
}
