package cl.duoc.ms_localizacion.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cl.duoc.ms_localizacion.dto.DireccionRespuestaDto;
import cl.duoc.ms_localizacion.dto.RegistrarDireccionDto;
import cl.duoc.ms_localizacion.security.JwtUtil;
import cl.duoc.ms_localizacion.service.LocalizacionServicio;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/localizacion")
@Tag(name = "Localizacion", description = "Gestión de ubicaciones geográficas de tiendas")
public class LocalizacionControlador {

    @Autowired
    private LocalizacionServicio localizacionServicio;

    @Autowired
    private JwtUtil jwtUtil;



    @PostMapping
    @Operation(summary = "Registrar ubicación", description = "Registra la dirección GPS de una tienda. Solo rol TIENDA.")
    public ResponseEntity<?> registrarDireccion(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody RegistrarDireccionDto dto) {

        // Validar que el token sea correcto
        String token = validarHeader(authHeader);
        if (token == null) {
            return respuestaNoAutorizado("Token requerido para registrar una ubicacion.");
        }

        // Solo el rol TIENDA puede registrar su ubicacion
        String rol = jwtUtil.extraerRol(token);
        if (!"TIENDA".equals(rol)) {
            return respuestaNoAutorizado("Solo los usuarios con rol TIENDA pueden registrar ubicaciones.");
        }

        try {
            DireccionRespuestaDto direccion = localizacionServicio.registrarDireccion(dto, authHeader);
            return ResponseEntity.status(HttpStatus.CREATED).body(direccion);
        } catch (RuntimeException e) {
            return respuestaError(e.getMessage());
        }
    }


    @GetMapping("/tienda/{tiendaId}")
    @Operation(summary = "Ver ubicación de tienda", description = "Devuelve la dirección registrada de una tienda.")
    public ResponseEntity<?> verUbicacionTienda(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable Integer tiendaId) {

        String token = validarHeader(authHeader);
        if (token == null) {
            return respuestaNoAutorizado("Token requerido para ver la ubicacion.");
        }

        try {
            DireccionRespuestaDto direccion = localizacionServicio.obtenerPorTienda(tiendaId, authHeader);
            return ResponseEntity.ok(direccion);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/tienda/{tiendaId}")
    @Operation(summary = "Actualizar ubicación", description = "Actualiza la dirección de una tienda. Solo rol TIENDA.")
    public ResponseEntity<?> actualizarUbicacion(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable Integer tiendaId,
            @Valid @RequestBody RegistrarDireccionDto dto) {

        String token = validarHeader(authHeader);
        if (token == null) {
            return respuestaNoAutorizado("Token requerido.");
        }

        String rol = jwtUtil.extraerRol(token);
        if (!"TIENDA".equals(rol)) {
            return respuestaNoAutorizado("Solo los usuarios con rol TIENDA pueden actualizar ubicaciones.");
        }

        try {
            DireccionRespuestaDto actualizada = localizacionServicio.actualizarDireccion(
                    tiendaId, dto, authHeader);
            return ResponseEntity.ok(actualizada);
        } catch (RuntimeException e) {
            return respuestaError(e.getMessage());
        }
    }
   // =========================================================
    // GET /api/localizacion/cercanas
    // Buscar tiendas dentro de un radio de distancia GPS
    // =========================================================
    /*
     * Header: Authorization: Bearer {token}
     * Query params:
     *   latitud   = tu latitud actual  (ej: -33.4372)
     *   longitud  = tu longitud actual (ej: -70.6506)
     *   radioKm   = radio de busqueda en km (opcional, default 10)
     *
     * Como obtener tu latitud y longitud actual:
     * - En el celular: Google Maps -> punto azul -> "Compartir" -> copia coordenadas
     * - En Postman: puedes usar las coordenadas de Google Maps
     *
     * Ejemplo completo en Postman:
     * GET http://localhost:8086/api/localizacion/cercanas?latitud=-33.4372&longitud=-70.6506&radioKm=5
     *
     * Respuesta 200: lista de DireccionRespuestaDto ordenados por cercania
     * Cada elemento incluye el campo "distanciaKm" con la distancia calculada
     */
    @GetMapping("/cercanas")
    @Operation(summary = "Buscar tiendas cercanas", description = "Devuelve tiendas dentro del radio GPS indicado (default 10 km). Params: latitud, longitud, radioKm.")
    public ResponseEntity<?> buscarCercanas(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam Double latitud,
            @RequestParam Double longitud,
            @RequestParam(defaultValue = "10.0") Double radioKm) {

        String token = validarHeader(authHeader);
        if (token == null) {
            return respuestaNoAutorizado("Token requerido para buscar tiendas cercanas.");
        }

        try {
            List<DireccionRespuestaDto> cercanas = localizacionServicio.buscarCercanas(
                    latitud, longitud, radioKm, authHeader);
            return ResponseEntity.ok(cercanas);
        } catch (RuntimeException e) {
            return respuestaError(e.getMessage());
        }
    }

    // =========================================================
    // GET /api/localizacion/ciudad/{ciudad}
    // Buscar todas las tiendas de una ciudad
    // =========================================================
    /*
     * Header: Authorization: Bearer {token}
     * Path param: ciudad = nombre de la ciudad (ej: "Santiago")
     *
     * Util para jugadores que buscan tiendas en otra ciudad,
     * sin necesitar su posicion GPS.
     *
     * Ejemplo en Postman:
     * GET http://localhost:8086/api/localizacion/ciudad/Valparaiso
     *
     * Respuesta 200: lista de DireccionRespuestaDto
     * (distanciaKm = null porque no es busqueda por cercania)
     */
    @GetMapping("/ciudad/{ciudad}")
    @Operation(summary = "Buscar tiendas por ciudad", description = "Devuelve todas las tiendas registradas en la ciudad indicada.")
    public ResponseEntity<?> buscarPorCiudad(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String ciudad) {

        String token = validarHeader(authHeader);
        if (token == null) {
            return respuestaNoAutorizado("Token requerido.");
        }

        try {
            List<DireccionRespuestaDto> tiendas = localizacionServicio.buscarPorCiudad(ciudad, authHeader);
            return ResponseEntity.ok(tiendas);
        } catch (RuntimeException e) {
            return respuestaError(e.getMessage());
        }
    }

    // =========================================================
    // METODOS PRIVADOS DE AYUDA
    // =========================================================

    /*
     * Valida el header Authorization y extrae el token limpio.
     * Devuelve el token si es valido, null si es invalido o falta.
     */
    private String validarHeader(String authHeader) {
        String token = jwtUtil.obtenerTokenDelHeader(authHeader);
        if (token == null || !jwtUtil.esTokenValido(token)) {
            return null;
        }
        return token;
    }

    // Respuesta estandar 401 Unauthorized
    private ResponseEntity<?> respuestaNoAutorizado(String mensaje) {
        Map<String, String> error = new HashMap<>();
        error.put("error", mensaje);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    // Respuesta estandar 400 Bad Request para errores de negocio
    private ResponseEntity<?> respuestaError(String mensaje) {
        Map<String, String> error = new HashMap<>();
        error.put("error", mensaje);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
}
