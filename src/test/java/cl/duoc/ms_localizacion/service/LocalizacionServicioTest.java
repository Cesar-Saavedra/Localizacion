package cl.duoc.ms_localizacion.service;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import cl.duoc.ms_localizacion.client.TiendaFeignClient;
import cl.duoc.ms_localizacion.dto.DireccionRespuestaDto;
import cl.duoc.ms_localizacion.dto.RegistrarDireccionDto;
import cl.duoc.ms_localizacion.dto.TiendaResumenDTO;
import cl.duoc.ms_localizacion.model.Direccion;
import cl.duoc.ms_localizacion.repository.DireccionRepositorio;

/*
 * El TiendaFeignClient (dependencia hacia ms-tiendas) se mockea con Mockito
 * en lugar de invocarse de verdad: estas pruebas validan solo las reglas de
 * negocio de LocalizacionServicio (direccion duplicada, coordenadas
 * obligatorias, calculo de cercania), no la comunicacion HTTP real entre
 * microservicios.
 */
@ExtendWith(MockitoExtension.class)
class LocalizacionServicioTest {

    @Mock
    private DireccionRepositorio direccionRepositorio;

    @Mock
    private TiendaFeignClient tiendaFeignClient;

    @InjectMocks
    private LocalizacionServicio localizacionServicio;

    private static final String AUTH_HEADER = "Bearer token-fake";
    private static final Integer TIENDA_ID = 3;

    private TiendaResumenDTO tienda;
    private RegistrarDireccionDto registrarDto;

    @BeforeEach
    void setUp() {
        tienda = new TiendaResumenDTO(TIENDA_ID, "Carta Magica TCG", "Lun-Sab 11:00-20:00", "ACTIVA");

        registrarDto = new RegistrarDireccionDto();
        registrarDto.setTiendaId(TIENDA_ID);
        registrarDto.setCalle("Av. Providencia 1234");
        registrarDto.setCiudad("Santiago");
        registrarDto.setRegion("Metropolitana");
        registrarDto.setLatitud(-33.4259);
        registrarDto.setLongitud(-70.6147);
    }

    // =====================================================================
    // registrarDireccion
    // =====================================================================

    @Test
    void registrarDireccion_exitoso() {
        when(tiendaFeignClient.obtenerResumenTienda(TIENDA_ID, AUTH_HEADER)).thenReturn(tienda);
        when(direccionRepositorio.existsByTiendaId(TIENDA_ID)).thenReturn(false);
        when(direccionRepositorio.save(any(Direccion.class))).thenAnswer(invocacion -> {
            Direccion d = invocacion.getArgument(0);
            d.setId(1);
            return d;
        });

        DireccionRespuestaDto respuesta = localizacionServicio.registrarDireccion(registrarDto, AUTH_HEADER);

        assertEquals(1, respuesta.getId());
        assertEquals("Carta Magica TCG", respuesta.getNombreTienda());
        assertEquals("Santiago", respuesta.getCiudad());
    }

    @Test
    void registrarDireccion_tiendaNoExiste() {
        when(tiendaFeignClient.obtenerResumenTienda(TIENDA_ID, AUTH_HEADER))
                .thenThrow(new RuntimeException("404"));

        RuntimeException error = assertThrows(RuntimeException.class, () ->
                localizacionServicio.registrarDireccion(registrarDto, AUTH_HEADER));

        assertTrue(error.getMessage().contains("No se encontro la tienda"));
    }

    @Test
    void registrarDireccion_yaTieneDireccionRegistrada() {
        when(tiendaFeignClient.obtenerResumenTienda(TIENDA_ID, AUTH_HEADER)).thenReturn(tienda);
        when(direccionRepositorio.existsByTiendaId(TIENDA_ID)).thenReturn(true);

        RuntimeException error = assertThrows(RuntimeException.class, () ->
                localizacionServicio.registrarDireccion(registrarDto, AUTH_HEADER));

        assertTrue(error.getMessage().contains("ya tiene una direccion registrada"));
    }

    @Test
    void registrarDireccion_sinCoordenadas() {
        when(tiendaFeignClient.obtenerResumenTienda(TIENDA_ID, AUTH_HEADER)).thenReturn(tienda);
        when(direccionRepositorio.existsByTiendaId(TIENDA_ID)).thenReturn(false);
        registrarDto.setLatitud(null);

        RuntimeException error = assertThrows(RuntimeException.class, () ->
                localizacionServicio.registrarDireccion(registrarDto, AUTH_HEADER));

        assertTrue(error.getMessage().contains("coordenadas"));
    }

    // =====================================================================
    // obtenerPorTienda
    // =====================================================================

    @Test
    void obtenerPorTienda_encontrada() {
        Direccion direccion = construirDireccion(1, -33.4259, -70.6147);
        when(direccionRepositorio.findByTiendaId(TIENDA_ID)).thenReturn(Optional.of(direccion));
        when(tiendaFeignClient.obtenerResumenTienda(TIENDA_ID, AUTH_HEADER)).thenReturn(tienda);

        DireccionRespuestaDto respuesta = localizacionServicio.obtenerPorTienda(TIENDA_ID, AUTH_HEADER);

        assertEquals("Carta Magica TCG", respuesta.getNombreTienda());
    }

    @Test
    void obtenerPorTienda_sinDireccionRegistrada() {
        when(direccionRepositorio.findByTiendaId(TIENDA_ID)).thenReturn(Optional.empty());

        RuntimeException error = assertThrows(RuntimeException.class, () ->
                localizacionServicio.obtenerPorTienda(TIENDA_ID, AUTH_HEADER));

        assertTrue(error.getMessage().contains("no tiene direccion registrada"));
    }

    // =====================================================================
    // actualizarDireccion
    // =====================================================================

    @Test
    void actualizarDireccion_actualizaSoloLosCamposEnviados() {
        Direccion direccion = construirDireccion(1, -33.4259, -70.6147);
        when(direccionRepositorio.findByTiendaId(TIENDA_ID)).thenReturn(Optional.of(direccion));
        when(direccionRepositorio.save(any(Direccion.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(tiendaFeignClient.obtenerResumenTienda(TIENDA_ID, AUTH_HEADER)).thenReturn(tienda);

        RegistrarDireccionDto dto = new RegistrarDireccionDto();
        dto.setCiudad("Valparaiso");

        DireccionRespuestaDto respuesta = localizacionServicio.actualizarDireccion(TIENDA_ID, dto, AUTH_HEADER);

        assertEquals("Valparaiso", respuesta.getCiudad());
        // La calle original no se toco porque el dto no la traia
        assertEquals("Av. Providencia 1234", respuesta.getCalle());
    }

    @Test
    void actualizarDireccion_sinDireccionPrevia() {
        when(direccionRepositorio.findByTiendaId(TIENDA_ID)).thenReturn(Optional.empty());

        RuntimeException error = assertThrows(RuntimeException.class, () ->
                localizacionServicio.actualizarDireccion(TIENDA_ID, registrarDto, AUTH_HEADER));

        assertTrue(error.getMessage().contains("no tiene direccion registrada"));
    }

    // =====================================================================
    // buscarCercanas
    // =====================================================================

    @Test
    void buscarCercanas_filtraPorRadio() {
        // Santiago centro: dentro del radio
        Direccion cercana = construirDireccion(1, -33.4372, -70.6506);
        // Antofagasta: muy lejos de Santiago, fuera del radio de 10km
        Direccion lejana = construirDireccion(2, -23.6509, -70.3975);

        when(direccionRepositorio.findAll()).thenReturn(List.of(cercana, lejana));
        when(tiendaFeignClient.obtenerResumenTienda(anyInt(), anyString())).thenReturn(tienda);

        List<DireccionRespuestaDto> cercanas = localizacionServicio.buscarCercanas(
                -33.4372, -70.6506, 10.0, AUTH_HEADER);

        assertEquals(1, cercanas.size());
        assertEquals(1, cercanas.get(0).getId());
        assertTrue(cercanas.get(0).getDistanciaKm() <= 10.0);
    }

    // =====================================================================
    // buscarPorCiudad
    // =====================================================================

    @Test
    void buscarPorCiudad_devuelveTiendasDeLaCiudad() {
        Direccion direccion = construirDireccion(1, -33.4259, -70.6147);
        when(direccionRepositorio.findByCiudad("Santiago")).thenReturn(List.of(direccion));
        when(tiendaFeignClient.obtenerResumenTienda(TIENDA_ID, AUTH_HEADER)).thenReturn(tienda);

        List<DireccionRespuestaDto> tiendas = localizacionServicio.buscarPorCiudad("Santiago", AUTH_HEADER);

        assertEquals(1, tiendas.size());
        assertEquals("Carta Magica TCG", tiendas.get(0).getNombreTienda());
    }

    @Test
    void buscarPorCiudad_sinResultados() {
        when(direccionRepositorio.findByCiudad("Punta Arenas")).thenReturn(List.of());

        List<DireccionRespuestaDto> tiendas = localizacionServicio.buscarPorCiudad("Punta Arenas", AUTH_HEADER);

        assertTrue(tiendas.isEmpty());
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private Direccion construirDireccion(Integer id, double lat, double lon) {
        Direccion direccion = new Direccion();
        direccion.setId(id);
        direccion.setTiendaId(TIENDA_ID);
        direccion.setCalle("Av. Providencia 1234");
        direccion.setCiudad("Santiago");
        direccion.setRegion("Metropolitana");
        direccion.setLatitud(lat);
        direccion.setLongitud(lon);
        return direccion;
    }
}
