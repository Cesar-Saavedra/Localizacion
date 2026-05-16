package cl.duoc.ms_localizacion.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cl.duoc.ms_localizacion.model.Direccion;

@Repository
public interface DireccionRepositorio extends JpaRepository<Direccion, Integer>{

    Optional<Direccion> findByTiendaId(Integer tiendaId);


    List<Direccion> FindbyCiudad(String ciudad);


    boolean existsByTiendaId(Integer tiendaId);


}
