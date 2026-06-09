package com.callcenter.iam.infrastructure.security;

import com.callcenter.observability.logging.MdcUtils;
import com.callcenter.observability.logging.StructuredLogFields;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Map<String, String> previous = MdcUtils.copy();
        try {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null && header.startsWith("Bearer ")) {
                try {
                    JwtTokenProvider.TokenClaims claims = tokenProvider.parse(header.substring(7));
                    ArrayList<SimpleGrantedAuthority> authorities = new ArrayList<>();
                    if (claims.tenantId() == null) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"));
                    } else {
                        authorities.add(new SimpleGrantedAuthority("ROLE_TENANT_USER"));
                    }
                    authorities.addAll(claims.roleIds().stream()
                            .map(roleId -> new SimpleGrantedAuthority("ROLE_" + roleId))
                            .collect(Collectors.toList()));
                    UsernamePasswordAuthenticationToken authenticationToken =
                            new UsernamePasswordAuthenticationToken(
                                    claims.userId(),
                                    null,
                                    authorities
                            );
                    authenticationToken.setDetails(claims);
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                    MdcUtils.put(StructuredLogFields.USER_ID, claims.userId());
                    MdcUtils.put(StructuredLogFields.TENANT_ID, claims.tenantId());
                } catch (IllegalArgumentException ignored) {
                    SecurityContextHolder.clearContext();
                    MdcUtils.put(StructuredLogFields.USER_ID, null);
                    MdcUtils.put(StructuredLogFields.TENANT_ID, null);
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            MdcUtils.restore(previous);
        }
    }
}
