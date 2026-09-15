package org.example.alumno.repository;

import org.example.alumno.model.Alumno;

import java.util.List;
import java.util.Optional;

public interface AlumnoRepository {

    List<Alumno> findAll();

    Optional<Alumno> findById(Long id);

    Alumno save(Alumno alumno);

    boolean deleteById(Long id);

    boolean existsById(Long id);

    void deleteAll();
}
