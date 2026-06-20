package cl.duoc.ms_localizacion.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/*
 * DTO que representa el resumen de una tienda recibido desde ms-tiendas.
 * ms-localizacion usa el nombre de la tienda para enriquecer las respuestas
 * de las direcciones.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TiendaResumenDTO {

    private Integer id;
    private String nombre;
    private String horarioAtencion;
    private String estado;
}
