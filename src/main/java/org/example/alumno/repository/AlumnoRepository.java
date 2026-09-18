package org.example.alumno.repository;

import org.example.alumno.model.Alumno;

import java.util.List;
import java.util.Optional;

/**
 * Contrato de acceso a datos de Alumno.
 *
 * Fijate que esta interfaz no menciona en ningun sitio "ArrayList",
 * "fichero" ni "binario": solo describe QUE operaciones se pueden hacer,
 * nunca COMO se implementan. Esto es el patron Repository combinado con
 * el principio de inversion de dependencias (la "D" de SOLID).
 *
 * Gracias a ello, en esta rama podemos sustituir la implementacion en
 * memoria (ArrayList) por una implementacion que persiste en un fichero
 * binario (AlumnoRepositoryBinarioImpl) sin tener que tocar ni una linea
 * de AlumnoService ni de ConsoleMenu: ambos dependen unicamente de esta
 * interfaz, no de los detalles de implementacion.
 */
public interface AlumnoRepository {

    List<Alumno> findAll();

    Optional<Alumno> findById(Long id);

    Alumno save(Alumno alumno);

    boolean deleteById(Long id);

    boolean existsById(Long id);

    void deleteAll();
}
