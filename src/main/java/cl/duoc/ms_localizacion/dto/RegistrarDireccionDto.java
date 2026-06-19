package cl.duoc.ms_localizacion.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RegistrarDireccionDto {

    private Integer tiendaId;

    @NotBlank(message = "La calle es obligatoria")
    private String  calle;

    @NotBlank(message = "La ciudad es obligatoria")
    private String  ciudad;

    @NotBlank(message = "La region es obligatoria")
    private String  region;

    @NotNull(message = "La latitud es obligatoria")
    @DecimalMin(value = "-90.0", message = "La latitud debe estar entre -90 y 90")
    @DecimalMax(value = "90.0", message = "La latitud debe estar entre -90 y 90")
    private Double  latitud;

    @NotNull(message = "La longitud es obligatoria")
    @DecimalMin(value = "-180.0", message = "La longitud debe estar entre -180 y 180")
    @DecimalMax(value = "180.0", message = "La longitud debe estar entre -180 y 180")
    private Double  longitud;

    private String  referencia;

}
