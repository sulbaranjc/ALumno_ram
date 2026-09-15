package org.example.alumno.service;

import jakarta.annotation.PostConstruct;
import org.example.alumno.model.Alumno;
import org.example.alumno.repository.AlumnoRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AlumnoServiceImpl implements AlumnoService {

    private final AlumnoRepository alumnoRepository;

    public AlumnoServiceImpl(AlumnoRepository alumnoRepository) {
        this.alumnoRepository = alumnoRepository;
    }

    @PostConstruct
    private void inicializarDatosSemilla() {
        cargarDatosSemilla();
    }

    @Override
    public List<Alumno> listarAlumnos() {
        return alumnoRepository.findAll();
    }

    @Override
    public Optional<Alumno> buscarPorId(Long id) {
        return alumnoRepository.findById(id);
    }

    @Override
    public Alumno crearAlumno(String nombre, String apellido, String correo,
                               double nota1, double nota2, double nota3) {
        Alumno alumno = new Alumno(null, nombre, apellido, correo, nota1, nota2, nota3);
        return alumnoRepository.save(alumno);
    }

    @Override
    public Optional<Alumno> actualizarAlumno(Long id, String nombre, String apellido, String correo,
                                              double nota1, double nota2, double nota3) {
        return alumnoRepository.findById(id).map(alumnoExistente -> {
            alumnoExistente.setNombre(nombre);
            alumnoExistente.setApellido(apellido);
            alumnoExistente.setCorreo(correo);
            alumnoExistente.setNota1(nota1);
            alumnoExistente.setNota2(nota2);
            alumnoExistente.setNota3(nota3);
            return alumnoRepository.save(alumnoExistente);
        });
    }

    @Override
    public boolean eliminarAlumno(Long id) {
        return alumnoRepository.deleteById(id);
    }

    @Override
    public void resetearDatos() {
        alumnoRepository.deleteAll();
        cargarDatosSemilla();
    }

    private void cargarDatosSemilla() {
        crearAlumno("Ana", "Gomez", "ana.gomez@example.com", 18, 16, 17);
        crearAlumno("Luis", "Martinez", "luis.martinez@example.com", 14, 15, 13);
        crearAlumno("Maria", "Rodriguez", "maria.rodriguez@example.com", 20, 19, 20);
        crearAlumno("Carlos", "Fernandez", "carlos.fernandez@example.com", 12, 11, 10);
        crearAlumno("Sofia", "Lopez", "sofia.lopez@example.com", 16, 18, 15);
    }
}
