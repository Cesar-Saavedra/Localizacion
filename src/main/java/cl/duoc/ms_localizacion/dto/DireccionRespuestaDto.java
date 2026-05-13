package cl.duoc.ms_localizacion.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DireccionRespuestaDto {

    private Integer id;
    private Integer tiendaId;
    private String  nombreTienda; 
    // obtiene info desde ms-tiendas
    private String  calle;
    private String  ciudad;
    private String  region;
    private Double  latitud;
    private Double  longitud;
    private String  referencia;
    private Double  distanciaKm;  
    // null si no es busqueda por cercania

}



