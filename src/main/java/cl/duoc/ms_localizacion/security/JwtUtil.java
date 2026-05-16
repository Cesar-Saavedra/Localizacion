package cl.duoc.ms_localizacion.security;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtUtil {
 // Clave secreta compartida (se lee desde application.properties)
    @Value("${jwt.secret}")
    private String secret;

    // Convierte el texto del secret a una clave criptografica que jjwt entiende
    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    /*
     * Extrae todos los datos del token (el "payload").
     * Lanza una excepcion si el token fue alterado o ya vencio.
     */
    public Claims extraerClaims(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseClaimsJws(token)
                .getPayload();
    }

    /*
     * Retorna true si el token es correcto y no esta vencido.
     * Retorna false si fue alterado, tiene mala firma o vencio.
     */
    public boolean esTokenValido(String token) {
        try {
            extraerClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // --- Metodos para extraer datos especificos del token ---

    // Id del usuario autenticado (Integer, igual que en ms-login)
    public Integer extraerId(String token) {
        return extraerClaims(token).get("id", Integer.class);
    }

    // Rol del usuario: JUGADOR, TIENDA u ORGANIZADOR
    public String extraerRol(String token) {
        return extraerClaims(token).get("rol", String.class);
    }

    /*
     * Extrae el token limpio del header Authorization.
     *
     * El header llega como: "Bearer eyJhbGci..."
     * Devuelve solo:        "eyJhbGci..."
     *
     * Devuelve null si el formato es incorrecto.
     */
    public String obtenerTokenDelHeader(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

}
