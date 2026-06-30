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
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Dirección registrada", content = @Content(
                    examples = @ExampleObject(name = "DireccionCreada", value = """
                            {
                              "id": 1,
                              "tiendaId": 3,
                              "calle": "Av. Providencia 1234",
                              "ciudad": "Santiago",
                              "latitud": -33.4259,
                              "longitud": -70.6147
                            }
                            """))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o la tienda ya tiene una dirección registrada"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido, o rol distinto de TIENDA")
    })
    public ResponseEntity<?> registrarDireccion(
            @Parameter(description = "Token JWT con formato 'Bearer {token}', debe pertenecer a un usuario con rol TIENDA", required = true)
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Datos de la dirección a registrar", required = true,
                    content = @Content(examples = @ExampleObject(value = """
                            {
                              "tiendaId": 3,
                              "calle": "Av. Providencia 1234",
                              "ciudad": "Santiago",
                              "latitud": -33.4259,
                              "longitud": -70.6147
                            }
                            """)))
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
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Dirección encontrada"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o expirado"),
            @ApiResponse(responseCode = "404", description = "La tienda no tiene dirección registrada")
    })
    public ResponseEntity<?> verUbicacionTienda(
            @Parameter(description = "Token JWT con formato 'Bearer {token}'", required = true)
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Parameter(description = "ID de la tienda", required = true, example = "3")
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
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Dirección actualizada correctamente"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido, o rol distinto de TIENDA")
    })
    public ResponseEntity<?> actualizarUbicacion(
            @Parameter(description = "Token JWT con formato 'Bearer {token}', debe pertenecer a un usuario con rol TIENDA", required = true)
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Parameter(description = "ID de la tienda", required = true, example = "3")
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
     * Como obtener tu latitud y longitud actual:
     * - En el celular: Google Maps -> punto azul -> "Compartir" -> copia coordenadas
     * - En Postman: puedes usar las coordenadas de Google Maps
     *
     * Cada elemento incluye el campo "distanciaKm" con la distancia calculada
     */
    @GetMapping("/cercanas")
    @Operation(summary = "Buscar tiendas cercanas", description = "Devuelve tiendas dentro del radio GPS indicado (default 10 km). Params: latitud, longitud, radioKm.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de tiendas cercanas ordenadas por distancia"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o expirado")
    })
    public ResponseEntity<?> buscarCercanas(
            @Parameter(description = "Token JWT con formato 'Bearer {token}'", required = true)
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Parameter(description = "Latitud del punto de búsqueda", required = true, example = "-33.4372")
            @RequestParam Double latitud,
            @Parameter(description = "Longitud del punto de búsqueda", required = true, example = "-70.6506")
            @RequestParam Double longitud,
            @Parameter(description = "Radio de búsqueda en kilómetros", example = "10.0")
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
    @GetMapping("/ciudad/{ciudad}")
    @Operation(summary = "Buscar tiendas por ciudad", description = "Devuelve todas las tiendas registradas en la ciudad indicada.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de tiendas de la ciudad indicada"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o expirado")
    })
    public ResponseEntity<?> buscarPorCiudad(
            @Parameter(description = "Token JWT con formato 'Bearer {token}'", required = true)
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Parameter(description = "Nombre de la ciudad a buscar", required = true, example = "Valparaiso")
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
