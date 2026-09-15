package org.example.alumno.service;

import org.example.alumno.model.Alumno;

import java.util.List;
import java.util.Optional;

public interface AlumnoService {

    List<Alumno> listarAlumnos();

    Optional<Alumno> buscarPorId(Long id);

    Alumno crearAlumno(String nombre, String apellido, String correo,
                        double nota1, double nota2, double nota3);

    Optional<Alumno> actualizarAlumno(Long id, String nombre, String apellido, String correo,
                                       double nota1, double nota2, double nota3);

    boolean eliminarAlumno(Long id);

    void resetearDatos();
}
