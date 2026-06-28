package dio.budgeting.infraestructure.http;

import dio.budgeting.application.auth.AuthService;
import dio.budgeting.application.auth.AuthenticatedUser;
import dio.budgeting.application.auth.DuplicateEmailException;
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

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
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

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
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

    public record AuthRequest(String email, String password) {
    }

    public record ErrorResponse(String error, String message) {
    }
}
