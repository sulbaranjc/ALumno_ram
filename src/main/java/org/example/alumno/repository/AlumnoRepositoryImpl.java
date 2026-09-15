package org.example.alumno.repository;

import org.example.alumno.model.Alumno;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class AlumnoRepositoryImpl implements AlumnoRepository {

    private final List<Alumno> alumnos = new ArrayList<>();
    private final AtomicLong secuenciaId = new AtomicLong(1);

    @Override
    public List<Alumno> findAll() {
        return new ArrayList<>(alumnos);
    }

    @Override
    public Optional<Alumno> findById(Long id) {
        return alumnos.stream()
                .filter(alumno -> alumno.getId().equals(id))
                .findFirst();
    }

    @Override
    public Alumno save(Alumno alumno) {
        if (alumno.getId() == null) {
            alumno.setId(secuenciaId.getAndIncrement());
            alumnos.add(alumno);
        } else {
            deleteById(alumno.getId());
            alumnos.add(alumno);
        }
        return alumno;
    }

    @Override
    public boolean deleteById(Long id) {
        return alumnos.removeIf(alumno -> alumno.getId().equals(id));
    }

    @Override
    public boolean existsById(Long id) {
        return alumnos.stream().anyMatch(alumno -> alumno.getId().equals(id));
    }
}
