package cl.duoc.ms_localizacion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "direcciones")
public class Direccion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer Id;

    @Column(nullable = false, unique = true)
    private Integer tiendaId;

    @Column(nullable = false)
    private String calle;


    @Column(nullable = false, length = 100)
    private String ciudad;


    @Column(length = 100)
    private String region;

    // Latitud GPS de la tienda (negativo = hemisferio sur)
    // Ejemplo: -33.4372 (Santiago)
    @Column(nullable = false)
    private Double latitud;

    // Longitud GPS de la tienda (negativo = lado oeste del planeta)
    // Ejemplo: -70.6506 (Santiago)
    @Column(nullable = false)
    private Double longitud;

    // Texto de ayuda para encontrar la tienda mas facilmente
    // Ejemplo: "Frente al metro Baquedano, subida por calle Loreto"
    @Column(length = 300)
    private String referencia;

}
