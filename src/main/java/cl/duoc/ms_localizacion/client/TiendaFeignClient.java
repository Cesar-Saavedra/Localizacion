package cl.duoc.ms_localizacion.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import cl.duoc.ms_localizacion.dto.TiendaResumenDTO;

/*
 * Cliente Feign para comunicarse con ms-tiendas.
 *
 * name = "ms-tiendas" → Feign resuelve el host via Eureka (lb://ms-tiendas),
 * sin URLs hardcodeadas en el yaml.
 */
@FeignClient(name = "ms-tiendas")
public interface TiendaFeignClient {

    /*
     * GET /api/tiendas/{id}/resumen
     * Devuelve nombre y estado de una tienda.
     * ms-localizacion lo usa para enriquecer las respuestas de direcciones
     * con el nombre de la tienda correspondiente.
     */
    @GetMapping("/api/tiendas/{id}/resumen")
    TiendaResumenDTO obtenerResumenTienda(
            @PathVariable("id") Integer idTienda,
            @RequestHeader("Authorization") String authHeader
    );
}
