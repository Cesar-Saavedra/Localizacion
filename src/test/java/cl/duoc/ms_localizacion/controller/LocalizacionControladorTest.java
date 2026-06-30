package cl.duoc.ms_localizacion.controller;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.duoc.ms_localizacion.dto.DireccionRespuestaDto;
import cl.duoc.ms_localizacion.dto.RegistrarDireccionDto;
import cl.duoc.ms_localizacion.security.JwtUtil;
import cl.duoc.ms_localizacion.service.LocalizacionServicio;
import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(LocalizacionControlador.class)
@AutoConfigureMockMvc(addFilters = false)
public class LocalizacionControladorTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LocalizacionServicio localizacionServicio;

    @MockitoBean
    private JwtUtil jwtUtil;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private DireccionRespuestaDto direccionEjemplo;

    @BeforeEach
    void setUp(){
        direccionEjemplo = new DireccionRespuestaDto(1, 3, "Carta Magica TCG", "Av. Siempre Viva 123",
                "Santiago", "Metropolitana", -33.4372, -70.6506, "Frente a la plaza", null);
    }

    // =====================================================================
    // POST /api/localizacion
    // =====================================================================

    @Test
    void registrarDireccion_sinToken_retorna401() throws Exception {
        RegistrarDireccionDto dto = new RegistrarDireccionDto();
        dto.setTiendaId(3);
        dto.setCalle("Av. Siempre Viva 123");
        dto.setCiudad("Santiago");
        dto.setRegion("Metropolitana");
        dto.setLatitud(-33.4372);
        dto.setLongitud(-70.6506);

        mockMvc.perform(post("/api/localizacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registrarDireccion_rolNoEsTienda_retorna401() throws Exception {
        RegistrarDireccionDto dto = new RegistrarDireccionDto();
        dto.setTiendaId(3);
        dto.setCalle("Av. Siempre Viva 123");
        dto.setCiudad("Santiago");
        dto.setRegion("Metropolitana");
        dto.setLatitud(-33.4372);
        dto.setLongitud(-70.6506);

        when(jwtUtil.obtenerTokenDelHeader("Bearer token-jugador")).thenReturn("token-jugador");
        when(jwtUtil.esTokenValido("token-jugador")).thenReturn(true);
        when(jwtUtil.extraerRol("token-jugador")).thenReturn("JUGADOR");

        mockMvc.perform(post("/api/localizacion")
                        .header("Authorization", "Bearer token-jugador")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registrarDireccion_exitoso_retorna201() throws Exception {
        RegistrarDireccionDto dto = new RegistrarDireccionDto();
        dto.setTiendaId(3);
        dto.setCalle("Av. Siempre Viva 123");
        dto.setCiudad("Santiago");
        dto.setRegion("Metropolitana");
        dto.setLatitud(-33.4372);
        dto.setLongitud(-70.6506);

        when(jwtUtil.obtenerTokenDelHeader("Bearer token-tienda")).thenReturn("token-tienda");
        when(jwtUtil.esTokenValido("token-tienda")).thenReturn(true);
        when(jwtUtil.extraerRol("token-tienda")).thenReturn("TIENDA");
        when(localizacionServicio.registrarDireccion(any(RegistrarDireccionDto.class), anyString()))
                .thenReturn(direccionEjemplo);

        mockMvc.perform(post("/api/localizacion")
                        .header("Authorization", "Bearer token-tienda")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void registrarDireccion_yaRegistrada_retorna400() throws Exception {
        RegistrarDireccionDto dto = new RegistrarDireccionDto();
        dto.setTiendaId(3);
        dto.setCalle("Av. Siempre Viva 123");
        dto.setCiudad("Santiago");
        dto.setRegion("Metropolitana");
        dto.setLatitud(-33.4372);
        dto.setLongitud(-70.6506);

        when(jwtUtil.obtenerTokenDelHeader("Bearer token-tienda")).thenReturn("token-tienda");
        when(jwtUtil.esTokenValido("token-tienda")).thenReturn(true);
        when(jwtUtil.extraerRol("token-tienda")).thenReturn("TIENDA");
        when(localizacionServicio.registrarDireccion(any(RegistrarDireccionDto.class), anyString()))
                .thenThrow(new RuntimeException("Esta tienda ya tiene una direccion registrada."));

        mockMvc.perform(post("/api/localizacion")
                        .header("Authorization", "Bearer token-tienda")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    // =====================================================================
    // GET /api/localizacion/tienda/{tiendaId}
    // =====================================================================

    @Test
    void verUbicacionTienda_sinToken_retorna401() throws Exception {
        mockMvc.perform(get("/api/localizacion/tienda/3"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verUbicacionTienda_encontrada_retorna200() throws Exception {
        when(jwtUtil.obtenerTokenDelHeader("Bearer token-bueno")).thenReturn("token-bueno");
        when(jwtUtil.esTokenValido("token-bueno")).thenReturn(true);
        when(localizacionServicio.obtenerPorTienda(3, "Bearer token-bueno")).thenReturn(direccionEjemplo);

        mockMvc.perform(get("/api/localizacion/tienda/3").header("Authorization", "Bearer token-bueno"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tiendaId").value(3));
    }

    @Test
    void verUbicacionTienda_noEncontrada_retorna404() throws Exception {
        when(jwtUtil.obtenerTokenDelHeader("Bearer token-bueno")).thenReturn("token-bueno");
        when(jwtUtil.esTokenValido("token-bueno")).thenReturn(true);
        when(localizacionServicio.obtenerPorTienda(99, "Bearer token-bueno"))
                .thenThrow(new RuntimeException("La tienda 99 no tiene direccion registrada todavia."));

        mockMvc.perform(get("/api/localizacion/tienda/99").header("Authorization", "Bearer token-bueno"))
                .andExpect(status().isNotFound());
    }

    // =====================================================================
    // PUT /api/localizacion/tienda/{tiendaId}
    // =====================================================================

    @Test
    void actualizarUbicacion_sinToken_retorna401() throws Exception {
        RegistrarDireccionDto dto = new RegistrarDireccionDto();
        dto.setCalle("Nueva calle");
        dto.setCiudad("Santiago");
        dto.setRegion("Metropolitana");
        dto.setLatitud(-33.4372);
        dto.setLongitud(-70.6506);

        mockMvc.perform(put("/api/localizacion/tienda/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void actualizarUbicacion_exitoso_retorna200() throws Exception {
        RegistrarDireccionDto dto = new RegistrarDireccionDto();
        dto.setCalle("Nueva calle");
        dto.setCiudad("Santiago");
        dto.setRegion("Metropolitana");
        dto.setLatitud(-33.4372);
        dto.setLongitud(-70.6506);

        when(jwtUtil.obtenerTokenDelHeader("Bearer token-tienda")).thenReturn("token-tienda");
        when(jwtUtil.esTokenValido("token-tienda")).thenReturn(true);
        when(jwtUtil.extraerRol("token-tienda")).thenReturn("TIENDA");
        when(localizacionServicio.actualizarDireccion(eq(3), any(RegistrarDireccionDto.class), anyString()))
                .thenReturn(direccionEjemplo);

        mockMvc.perform(put("/api/localizacion/tienda/3")
                        .header("Authorization", "Bearer token-tienda")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    // =====================================================================
    // GET /api/localizacion/cercanas
    // =====================================================================

    @Test
    void buscarCercanas_sinToken_retorna401() throws Exception {
        mockMvc.perform(get("/api/localizacion/cercanas")
                        .param("latitud", "-33.4372")
                        .param("longitud", "-70.6506"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void buscarCercanas_retorna200() throws Exception {
        when(jwtUtil.obtenerTokenDelHeader("Bearer token-bueno")).thenReturn("token-bueno");
        when(jwtUtil.esTokenValido("token-bueno")).thenReturn(true);
        when(localizacionServicio.buscarCercanas(anyDouble(), anyDouble(), anyDouble(), anyString()))
                .thenReturn(Arrays.asList(direccionEjemplo));

        mockMvc.perform(get("/api/localizacion/cercanas")
                        .param("latitud", "-33.4372")
                        .param("longitud", "-70.6506")
                        .param("radioKm", "5")
                        .header("Authorization", "Bearer token-bueno"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    // =====================================================================
    // GET /api/localizacion/ciudad/{ciudad}
    // =====================================================================

    @Test
    void buscarPorCiudad_sinToken_retorna401() throws Exception {
        mockMvc.perform(get("/api/localizacion/ciudad/Santiago"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void buscarPorCiudad_retorna200() throws Exception {
        when(jwtUtil.obtenerTokenDelHeader("Bearer token-bueno")).thenReturn("token-bueno");
        when(jwtUtil.esTokenValido("token-bueno")).thenReturn(true);
        when(localizacionServicio.buscarPorCiudad("Santiago", "Bearer token-bueno"))
                .thenReturn(Arrays.asList(direccionEjemplo));

        mockMvc.perform(get("/api/localizacion/ciudad/Santiago").header("Authorization", "Bearer token-bueno"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ciudad").value("Santiago"));
    }
}
