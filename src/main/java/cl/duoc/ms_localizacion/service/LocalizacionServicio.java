package cl.duoc.ms_localizacion.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import cl.duoc.ms_localizacion.client.TiendaFeignClient;
import cl.duoc.ms_localizacion.dto.DireccionRespuestaDto;
import cl.duoc.ms_localizacion.dto.RegistrarDireccionDto;
import cl.duoc.ms_localizacion.dto.TiendaResumenDTO;
import cl.duoc.ms_localizacion.model.Direccion;
import cl.duoc.ms_localizacion.repository.DireccionRepositorio;

@Service
public class LocalizacionServicio {

    @Autowired
    private DireccionRepositorio direccionRepositorio;

    // Cliente Feign para consultar ms-tiendas via Eureka (sin URL hardcodeada)
    @Autowired
    private TiendaFeignClient tiendaFeignClient;

    // =========================================================
    // REGISTRAR DIRECCION DE UNA TIENDA
    // =========================================================

    /*
     * Guarda por primera vez la ubicacion de una tienda.
     *
     * Proceso:
     * 1. Consulta ms-tiendas para verificar que la tienda existe.
     * 2. Verifica que la tienda no tenga ya una direccion registrada.
     * 3. Valida que llegaron las coordenadas GPS (son obligatorias).
     * 4. Guarda la direccion en la BD.
     * 5. Devuelve la direccion creada con el nombre de la tienda.
     *
     * @param dto         datos del formulario con la direccion y coordenadas
     * @param authHeader  header completo "Bearer eyJ..." para llamar a ms-tiendas
     */
    public DireccionRespuestaDto registrarDireccion(RegistrarDireccionDto dto, String authHeader) {

        // Paso 1: verificar que la tienda existe en ms-tiendas
        TiendaResumenDTO datosTienda = consultarResumenTienda(dto.getTiendaId(), authHeader);
        if (datosTienda == null) {
            throw new RuntimeException("No se encontro la tienda con id: " + dto.getTiendaId()
                    + ". Verifica que ms-tiendas este corriendo y que la tienda exista.");
        }

        // Paso 2: verificar que esta tienda no tenga ya una direccion
        if (direccionRepositorio.existsByTiendaId(dto.getTiendaId())) {
            throw new RuntimeException("Esta tienda ya tiene una direccion registrada. "
                    + "Usa PUT /api/localizacion/tienda/" + dto.getTiendaId() + " para actualizarla.");
        }

        // Paso 3: las coordenadas son obligatorias para el mapa
        if (dto.getLatitud() == null || dto.getLongitud() == null) {
            throw new RuntimeException("Las coordenadas GPS (latitud y longitud) son obligatorias.");
        }

        // Paso 4: crear y guardar la direccion en la BD
        Direccion nueva = new Direccion();
        nueva.setTiendaId(dto.getTiendaId());
        nueva.setCalle(dto.getCalle());
        nueva.setCiudad(dto.getCiudad());
        nueva.setRegion(dto.getRegion());
        nueva.setLatitud(dto.getLatitud());
        nueva.setLongitud(dto.getLongitud());
        nueva.setReferencia(dto.getReferencia());

        Direccion guardada = direccionRepositorio.save(nueva);

        // Paso 5: devolver con nombre de la tienda
        return construirRespuesta(guardada, datosTienda.getNombre(), null);
    }

    // =========================================================
    // VER LA UBICACION DE UNA TIENDA
    // =========================================================

    /*
     * Devuelve la direccion y coordenadas de una tienda especifica.
     * Lo usan los jugadores para ver donde esta una tienda en el mapa.
     *
     * @param tiendaId    id de la tienda en ms-tiendas
     * @param authHeader  header para llamar a ms-tiendas y obtener el nombre
     */
    public DireccionRespuestaDto obtenerPorTienda(Integer tiendaId, String authHeader) {

        // Buscar la direccion en la BD
        Direccion direccion = direccionRepositorio.findByTiendaId(tiendaId)
                .orElseThrow(() -> new RuntimeException(
                        "La tienda " + tiendaId + " no tiene direccion registrada todavia."));

        // Consultar el nombre de la tienda en ms-tiendas
        TiendaResumenDTO datosTienda = consultarResumenTienda(tiendaId, authHeader);
        String nombreTienda = datosTienda != null ? datosTienda.getNombre() : "Tienda desconocida";

        return construirRespuesta(direccion, nombreTienda, null);
    }

    // =========================================================
    // ACTUALIZAR LA DIRECCION DE UNA TIENDA
    // =========================================================

    /*
     * Actualiza la direccion ya registrada de una tienda.
     * Solo actualiza los campos que lleguen con valor (no nulos).
     *
     * @param tiendaId    id de la tienda cuya direccion se actualiza
     * @param dto         nuevos datos (solo los que cambiaron)
     * @param authHeader  header para llamar a ms-tiendas
     */
    public DireccionRespuestaDto actualizarDireccion(Integer tiendaId, RegistrarDireccionDto dto, String authHeader) {

        // Buscar la direccion existente
        Direccion direccion = direccionRepositorio.findByTiendaId(tiendaId)
                .orElseThrow(() -> new RuntimeException(
                        "La tienda " + tiendaId + " no tiene direccion registrada. "
                        + "Usa POST /api/localizacion para registrarla primero."));

        // Actualizar solo los campos que llegaron con valor
        if (dto.getCalle() != null && !dto.getCalle().isBlank()) {
            direccion.setCalle(dto.getCalle());
        }
        if (dto.getCiudad() != null && !dto.getCiudad().isBlank()) {
            direccion.setCiudad(dto.getCiudad());
        }
        if (dto.getRegion() != null) {
            direccion.setRegion(dto.getRegion());
        }
        if (dto.getLatitud() != null) {
            direccion.setLatitud(dto.getLatitud());
        }
        if (dto.getLongitud() != null) {
            direccion.setLongitud(dto.getLongitud());
        }
        if (dto.getReferencia() != null) {
            direccion.setReferencia(dto.getReferencia());
        }

        Direccion actualizada = direccionRepositorio.save(direccion);

        // Consultar nombre de la tienda para la respuesta
        TiendaResumenDTO datosTienda = consultarResumenTienda(tiendaId, authHeader);
        String nombreTienda = datosTienda != null ? datosTienda.getNombre() : "Tienda desconocida";

        return construirRespuesta(actualizada, nombreTienda, null);
    }

    // =========================================================
    // BUSCAR TIENDAS CERCANAS A UNA UBICACION
    // =========================================================

    /*
     * Devuelve las tiendas que estan dentro de un radio de distancia
     * desde la posicion del jugador. Usa la Formula de Haversine.
     *
     * @param latitudUsuario   latitud de la posicion del jugador
     * @param longitudUsuario  longitud de la posicion del jugador
     * @param radioKm          radio de busqueda en kilometros (default 10)
     * @param authHeader       header para enriquecer con nombres de tiendas
     */
    public List<DireccionRespuestaDto> buscarCercanas(Double latitudUsuario, Double longitudUsuario,
                                                       Double radioKm, String authHeader) {

        List<Direccion> todasLasDirecciones = direccionRepositorio.findAll();
        List<DireccionRespuestaDto> tiendasCercanas = new ArrayList<>();

        for (Direccion dir : todasLasDirecciones) {
            double distancia = calcularDistanciaKm(
                    latitudUsuario, longitudUsuario,
                    dir.getLatitud(), dir.getLongitud()
            );

            if (distancia <= radioKm) {
                TiendaResumenDTO datosTienda = consultarResumenTienda(dir.getTiendaId(), authHeader);
                String nombreTienda = datosTienda != null ? datosTienda.getNombre() : "Tienda desconocida";
                tiendasCercanas.add(construirRespuesta(dir, nombreTienda, distancia));
            }
        }

        return tiendasCercanas;
    }

    // =========================================================
    // BUSCAR TIENDAS POR CIUDAD
    // =========================================================

    /*
     * Devuelve todas las tiendas ubicadas en una ciudad especifica.
     *
     * @param ciudad      nombre de la ciudad a buscar
     * @param authHeader  header para llamar a ms-tiendas
     */
    public List<DireccionRespuestaDto> buscarPorCiudad(String ciudad, String authHeader) {

        List<Direccion> direcciones = direccionRepositorio.findByCiudad(ciudad);
        List<DireccionRespuestaDto> listaRespuesta = new ArrayList<>();

        for (Direccion dir : direcciones) {
            TiendaResumenDTO datosTienda = consultarResumenTienda(dir.getTiendaId(), authHeader);
            String nombreTienda = datosTienda != null ? datosTienda.getNombre() : "Tienda desconocida";
            listaRespuesta.add(construirRespuesta(dir, nombreTienda, null));
        }

        return listaRespuesta;
    }

    // =========================================================
    // METODOS PRIVADOS DE AYUDA
    // =========================================================

    /*
     * Llama a ms-tiendas via Feign para obtener el resumen de una tienda.
     * Feign usa Eureka para resolver "ms-tiendas" sin URL hardcodeada.
     * Devuelve null si ms-tiendas no responde.
     */
    private TiendaResumenDTO consultarResumenTienda(Integer tiendaId, String authHeader) {
        try {
            return tiendaFeignClient.obtenerResumenTienda(tiendaId, authHeader);
        } catch (Exception e) {
            System.out.println("[ms-localizacion] No se pudo consultar ms-tiendas para tienda "
                    + tiendaId + ": " + e.getMessage());
            return null;
        }
    }

    /*
     * Calcula la distancia en kilometros entre dos puntos GPS
     * usando la Formula de Haversine.
     */
    private double calcularDistanciaKm(double lat1, double lon1,
                                        double lat2, double lon2) {
        final int RADIO_TIERRA_KM = 6371;
        double difLat = Math.toRadians(lat2 - lat1);
        double difLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(difLat / 2) * Math.sin(difLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(difLon / 2) * Math.sin(difLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.round(RADIO_TIERRA_KM * c * 100.0) / 100.0;
    }

    /*
     * Convierte una entidad Direccion al DTO de respuesta.
     *
     * @param direccion    entidad con los datos de la BD
     * @param nombreTienda nombre obtenido desde ms-tiendas
     * @param distanciaKm  distancia calculada (null si no es busqueda cercana)
     */
    private DireccionRespuestaDto construirRespuesta(Direccion direccion,
                                                      String nombreTienda,
                                                      Double distanciaKm) {
        DireccionRespuestaDto respuesta = new DireccionRespuestaDto();
        respuesta.setId(direccion.getId());
        respuesta.setTiendaId(direccion.getTiendaId());
        respuesta.setNombreTienda(nombreTienda);
        respuesta.setCalle(direccion.getCalle());
        respuesta.setCiudad(direccion.getCiudad());
        respuesta.setRegion(direccion.getRegion());
        respuesta.setLatitud(direccion.getLatitud());
        respuesta.setLongitud(direccion.getLongitud());
        respuesta.setReferencia(direccion.getReferencia());
        respuesta.setDistanciaKm(distanciaKm);
        return respuesta;
    }
}
