package cl.duoc.ms_localizacion.dto;

import lombok.Data;

@Data
public class RegistrarDireccionDto {

    private Integer tiendaId;
    private String  calle;
    private String  ciudad;
    private String  region;
    private Double  latitud;
    private Double  longitud;
    private String  referencia;

}
