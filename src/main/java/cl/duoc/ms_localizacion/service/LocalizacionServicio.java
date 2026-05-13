package cl.duoc.ms_localizacion.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import cl.duoc.ms_localizacion.model.Direccion;
import cl.duoc.ms_localizacion.repository.DireccionRepositorio;

@Service
public class LocalizacionServicio {

    @Autowired
    private DireccionRepositorio direccionRepositorio;


    @Autowired
    private RestTemplate restTemplate;

    @Value("${ms.tiendas.url}")
    private String urlMsTiendas;

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
        Map<String, Object> datosTienda = consultarResumenTienda(dto.getTiendaId(), authHeader);
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
        String nombreTienda = (String) datosTienda.get("nombre");
        return construirRespuesta(guardada, nombreTienda, null);
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
        Map<String, Object> datosTienda = consultarResumenTienda(tiendaId, authHeader);
        String nombreTienda = datosTienda != null
                ? (String) datosTienda.get("nombre")
                : "Tienda desconocida";

        // distanciaKm = null porque no es una busqueda por cercania
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
        // Si un campo es null, significa que el usuario no quiere cambiarlo
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
        Map<String, Object> datosTienda = consultarResumenTienda(tiendaId, authHeader);
        String nombreTienda = datosTienda != null
                ? (String) datosTienda.get("nombre")
                : "Tienda desconocida";

        return construirRespuesta(actualizada, nombreTienda, null);
    }

    // =========================================================
    // BUSCAR TIENDAS CERCANAS A UNA UBICACION
    // =========================================================

    /*
     * Devuelve las tiendas que estan dentro de un radio de distancia
     * desde la posicion del jugador.
     *
     * Usa la Formula de Haversine para calcular distancias reales
     * entre dos puntos GPS en la superficie de la Tierra.
     *
     * Ejemplo de uso:
     * GET /api/localizacion/cercanas?latitud=-33.4372&longitud=-70.6506&radioKm=5
     * -> Devuelve todas las tiendas en un radio de 5 km desde esa posicion
     *
     * @param latitudUsuario   latitud de la posicion del jugador
     * @param longitudUsuario  longitud de la posicion del jugador
     * @param radioKm          radio de busqueda en kilometros (default 10)
     * @param authHeader       header para enriquecer con nombres de tiendas
     */
    public List<DireccionRespuestaDto> buscarCercanas(Double latitudUsuario,Double longitudUsuario,Double radioKm,String authHeader) {

        // Traer todas las direcciones registradas en la BD
        List<Direccion> todasLasDirecciones = direccionRepositorio.findAll();

        // Lista donde vamos acumulando las tiendas que quedan dentro del radio
        List<DireccionRespuestaDto> tiendasCercanas = new ArrayList<>();

        for (Direccion dir : todasLasDirecciones) {

            // Calcular la distancia real entre el jugador y esta tienda
            double distancia = calcularDistanciaKm(
                    latitudUsuario, longitudUsuario,
                    dir.getLatitud(), dir.getLongitud()
            );

            // Solo incluir las tiendas dentro del radio pedido
            if (distancia <= radioKm) {
                Map<String, Object> datosTienda = consultarResumenTienda(dir.getTiendaId(), authHeader);
                String nombreTienda = datosTienda != null
                        ? (String) datosTienda.get("nombre")
                        : "Tienda desconocida";

                // Agregar la tienda con su distancia calculada
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
     * Util para jugadores que buscan tiendas en otra ciudad.
     *
     * Ejemplo: GET /api/localizacion/ciudad/Valparaiso
     *
     * @param ciudad      nombre de la ciudad a buscar
     * @param authHeader  header para llamar a ms-tiendas
     */
    public List<DireccionRespuestaDto> buscarPorCiudad(String ciudad, String authHeader) {

        List<Direccion> direcciones = direccionRepositorio.findByCiudad(ciudad);

        List<DireccionRespuestaDto> listaRespuesta = new ArrayList<>();
        for (Direccion dir : direcciones) {
            Map<String, Object> datosTienda = consultarResumenTienda(dir.getTiendaId(), authHeader);
            String nombreTienda = datosTienda != null
                    ? (String) datosTienda.get("nombre")
                    : "Tienda desconocida";
            // distanciaKm = null porque no es busqueda por cercania
            listaRespuesta.add(construirRespuesta(dir, nombreTienda, null));
        }

        return listaRespuesta;
    }

    // =========================================================
    // METODOS PRIVADOS DE AYUDA
    // =========================================================

    /*
     * Calcula la distancia en kilometros entre dos puntos GPS.
     *
     * Usa la Formula de Haversine, que tiene en cuenta que la Tierra
     * no es plana sino esferica. Es la misma logica que usa Google Maps.
     *
     * Ejemplo:
     * Santiago (-33.4372, -70.6506) a Valparaiso (-33.0458, -71.6197)
     * Resultado: aprox. 113 km en linea recta
     *
     * @param lat1  latitud del punto 1 (el jugador)
     * @param lon1  longitud del punto 1
     * @param lat2  latitud del punto 2 (la tienda)
     * @param lon2  longitud del punto 2
     * @return distancia en km redondeada a 2 decimales
     */
    private double calcularDistanciaKm(double lat1, double lon1,
                                        double lat2, double lon2) {

        // Radio de la Tierra en kilometros (valor oficial)
        final int RADIO_TIERRA_KM = 6371;

        // Convertir las diferencias de coordenadas de grados a radianes
        // Los metodos Math.sin() y Math.cos() trabajan en radianes, no en grados
        double difLat = Math.toRadians(lat2 - lat1);
        double difLon = Math.toRadians(lon2 - lon1);

        // Calcular "a": cuadrado de la mitad de la cuerda entre los dos puntos
        double a = Math.sin(difLat / 2) * Math.sin(difLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(difLon / 2) * Math.sin(difLon / 2);

        // Calcular "c": angulo central entre los dos puntos (en radianes)
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        // Distancia final = radio de la Tierra * angulo central
        double distancia = RADIO_TIERRA_KM * c;

        // Redondear a 2 decimales (ej: 3.14159 -> 3.14)
        return Math.round(distancia * 100.0) / 100.0;
    }

    /*
     * Llama a ms-tiendas para obtener el nombre y estado de una tienda.
     *
     * Usa RestTemplate para hacer una peticion HTTP GET al endpoint
     * /api/tiendas/{id}/resumen de ms-tiendas.
     *
     * Devuelve un Map con los datos o null si ms-tiendas no responde.
     *
     * @param tiendaId    id de la tienda a consultar
     * @param authHeader  header completo "Bearer eyJ..." para la peticion
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> consultarResumenTienda(Integer tiendaId, String authHeader) {
        try {
            // URL del endpoint de resumen en ms-tiendas
            String url = urlMsTiendas + "/api/tiendas/" + tiendaId + "/resumen";

            // Crear los headers HTTP con el token JWT
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", authHeader); // "Bearer eyJ..."

            // Crear la entidad HTTP (headers + sin body porque es GET)
            HttpEntity<Void> peticion = new HttpEntity<>(headers);

            // Hacer la llamada GET y mapear la respuesta JSON a Map de Java
            ResponseEntity<Map> respuesta = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    peticion,
                    Map.class
            );

            return respuesta.getBody();

        } catch (Exception e) {
            // Si ms-tiendas no responde, devolvemos null
            // El metodo que llama decide si lanzar error o usar un valor por defecto
            System.out.println("[ms-localizacion] No se pudo consultar ms-tiendas para tienda "
                    + tiendaId + ": " + e.getMessage());
            return null;
        }
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
