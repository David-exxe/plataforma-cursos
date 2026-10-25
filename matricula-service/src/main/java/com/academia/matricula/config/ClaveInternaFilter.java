package com.academia.matricula.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Las rutas /internal/** solo las pueden llamar otros microservicios que conozcan la clave interna. */
public class ClaveInternaFilter extends OncePerRequestFilter {

    private final byte[] clave;

    public ClaveInternaFilter(String clave) {
        this.clave = clave.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String recibida = req.getHeader("X-Internal-Key");
        if (recibida == null || !MessageDigest.isEqual(clave, recibida.getBytes(StandardCharsets.UTF_8))) {
            res.setStatus(401);
            res.setContentType("application/json");
            res.getWriter().write("{\"error\":\"Clave interna invalida\"}");
            return;
        }
        chain.doFilter(req, res);
    }
}
