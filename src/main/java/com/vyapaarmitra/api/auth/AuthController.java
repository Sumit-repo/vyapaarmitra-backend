package com.vyapaarmitra.api.auth;

import com.vyapaarmitra.api.auth.AuthDtos.GoogleAuthRequest;
import com.vyapaarmitra.api.auth.AuthDtos.GoogleAuthResponse;
import com.vyapaarmitra.api.auth.AuthDtos.LoginRequest;
import com.vyapaarmitra.api.auth.AuthDtos.MeResponse;
import com.vyapaarmitra.api.auth.AuthDtos.OtpRequestRequest;
import com.vyapaarmitra.api.auth.AuthDtos.OtpRequestResponse;
import com.vyapaarmitra.api.auth.AuthDtos.OtpVerifyRequest;
import com.vyapaarmitra.api.auth.AuthDtos.RefreshRequest;
import com.vyapaarmitra.api.auth.AuthDtos.RegisterRequest;
import com.vyapaarmitra.api.auth.AuthDtos.TokenResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private final AuthService authService;
    private final OtpService otpService;
    private final GoogleAuthService googleAuthService;

    public AuthController(AuthService authService, OtpService otpService,
                          GoogleAuthService googleAuthService) {
        this.authService = authService;
        this.otpService = otpService;
        this.googleAuthService = googleAuthService;
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/auth/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.email(), request.password());
    }

    /** Request an email one-time code for passwordless login or verified signup. */
    @PostMapping("/auth/otp/request")
    public OtpRequestResponse otpRequest(@Valid @RequestBody OtpRequestRequest request) {
        return otpService.request(request.email(), request.purpose());
    }

    /** Verify an email one-time code and issue a session. */
    @PostMapping("/auth/otp/verify")
    public TokenResponse otpVerify(@Valid @RequestBody OtpVerifyRequest request) {
        return otpService.verify(request);
    }

    /** Sign in / sign up with a Google ID token. */
    @PostMapping("/auth/google")
    public GoogleAuthResponse google(@Valid @RequestBody GoogleAuthRequest request) {
        return googleAuthService.signIn(request);
    }

    @PostMapping("/auth/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AuthUser authUser) {
        return authService.me(authUser);
    }
}
