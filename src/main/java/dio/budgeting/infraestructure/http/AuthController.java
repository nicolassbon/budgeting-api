package dio.budgeting.infraestructure.http;

import dio.budgeting.application.auth.AuthService;
import dio.budgeting.application.auth.AuthenticatedUser;
import dio.budgeting.application.auth.DuplicateEmailException;
import dio.budgeting.application.auth.PasswordResetService;
import dio.budgeting.application.auth.PasswordResetTokenInvalidException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService, PasswordResetService passwordResetService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthenticatedUser register(@RequestBody AuthRequest request) {
        return authService.register(request.email(), request.password());
    }

    @PostMapping("/login")
    public AuthenticatedUser login(@RequestBody AuthRequest request,
                                   HttpServletRequest servletRequest,
                                   HttpServletResponse servletResponse) {
        Authentication authentication = authService.authenticate(request.email(), request.password());
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        HttpSession session = servletRequest.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        new HttpSessionSecurityContextRepository().saveContext(context, servletRequest, servletResponse);
        servletResponse.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(session.getId(), servletRequest).toString());
        return authService.currentUser(authentication.getName());
    }

    private static ResponseCookie sessionCookie(String sessionId, HttpServletRequest servletRequest) {
        String contextPath = servletRequest.getContextPath();
        String path = contextPath == null || contextPath.isBlank() ? "/" : contextPath;
        return ResponseCookie.from("JSESSIONID", sessionId)
                .httpOnly(true)
                .path(path)
                .sameSite("Lax")
                .build();
    }

    @GetMapping("/me")
    public AuthenticatedUser me(Authentication authentication) {
        return authService.currentUser(authentication.getName());
    }

    @GetMapping("/me/weekly-budget")
    public WeeklyBudget weeklyBudget(Authentication authentication) {
        return new WeeklyBudget(authService.currentWeeklyBudget(authentication.getName()));
    }

    @PutMapping("/me/weekly-budget")
    public WeeklyBudget updateWeeklyBudget(Authentication authentication,
                                           @Valid @RequestBody WeeklyBudgetRequest request) {
        return new WeeklyBudget(authService.updateWeeklyBudget(authentication.getName(), request.amount()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.email());
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
    }

    @ExceptionHandler(DuplicateEmailException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ErrorResponse duplicateEmail(DuplicateEmailException exception) {
        return new ErrorResponse("duplicate_email", exception.getMessage());
    }

    @ExceptionHandler({BadCredentialsException.class, IllegalArgumentException.class})
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    ErrorResponse unauthorized(RuntimeException exception) {
        return new ErrorResponse("authentication_failed", "Invalid email or password");
    }

    @ExceptionHandler(PasswordResetTokenInvalidException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    ErrorResponse invalidPasswordResetToken(PasswordResetTokenInvalidException exception) {
        return new ErrorResponse("reset_token_invalid", exception.getMessage());
    }

    public record AuthRequest(String email, String password) {
    }

    public record ForgotPasswordRequest(@NotBlank String email) {
    }

    public record ResetPasswordRequest(@NotBlank String token, @NotBlank String newPassword) {
    }

    public record WeeklyBudget(BigDecimal amount) {
    }

    public record WeeklyBudgetRequest(@PositiveOrZero BigDecimal amount) {
    }

    public record ErrorResponse(String error, String message) {
    }
}
