package com.fresh.traceability.config;

import com.fresh.traceability.entity.User;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final long TOKEN_TTL_MILLIS = 12L * 60 * 60 * 1000;

    public static final Map<String, UserSession> TOKEN_STORE = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        if (path.startsWith("/api/login")
                || path.startsWith("/api/trace/query/")
                || path.startsWith("/api/trace/public-config")) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeUnauthorized(response, "未登录或登录已失效");
            return false;
        }

        String token = authHeader.substring(7);
        UserSession session = TOKEN_STORE.get(token);
        if (session == null || session.isExpired()) {
            TOKEN_STORE.remove(token);
            writeUnauthorized(response, "登录凭证无效");
            return false;
        }
        User user = session.getUser();

        request.setAttribute("currentUserId", user.getUserId());
        request.setAttribute("currentUserRole", user.getRole());
        request.setAttribute("currentUser", user);
        request.setAttribute("currentToken", token);
        return true;
    }

    public static String createSession(User user) {
        String token = java.util.UUID.randomUUID().toString().replace("-", "");
        TOKEN_STORE.put(token, new UserSession(user, System.currentTimeMillis() + TOKEN_TTL_MILLIS));
        return token;
    }

    public static void removeToken(String token) {
        if (token != null && !token.trim().isEmpty()) {
            TOKEN_STORE.remove(token);
        }
    }

    public static void removeSessionsByUserId(Integer userId) {
        if (userId == null) {
            return;
        }
        for (Map.Entry<String, UserSession> entry : TOKEN_STORE.entrySet()) {
            UserSession session = entry.getValue();
            if (session != null && session.getUser() != null && userId.equals(session.getUser().getUserId())) {
                TOKEN_STORE.remove(entry.getKey());
            }
        }
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"" + message + "\"}");
    }

    public static class UserSession {
        private final User user;
        private final long expiresAt;

        public UserSession(User user, long expiresAt) {
            this.user = user;
            this.expiresAt = expiresAt;
        }

        public User getUser() {
            return user;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}
