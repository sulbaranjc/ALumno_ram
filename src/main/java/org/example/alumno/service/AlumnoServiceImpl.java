package org.example.alumno.service;

import jakarta.annotation.PostConstruct;
import org.example.alumno.model.Alumno;
import org.example.alumno.repository.AlumnoRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Nota pedagogica: casi todo este servicio es IDENTICO a la version de la
 * rama pers_disc (persistencia en ArrayList). No ha hecho falta tocarlo al
 * cambiar la tecnologia de persistencia porque depende de la interfaz
 * {@link AlumnoRepository}, no de su implementacion concreta (Spring
 * inyecta aqui, sin que lo sepamos, AlumnoRepositorySerializadoImpl en
 * lugar del antiguo AlumnoRepositoryImpl). La UNICA adaptacion necesaria
 * esta en {@link #inicializarDatosSemilla()}, justo porque ahora los
 * datos SI persisten entre ejecuciones.
 */
@Service
public class AlumnoServiceImpl implements AlumnoService {

    private final AlumnoRepository alumnoRepository;

    public AlumnoServiceImpl(AlumnoRepository alumnoRepository) {
        this.alumnoRepository = alumnoRepository;
    }

    /**
     * Con la persistencia en ArrayList (rama pers_disc) esta comprobacion
     * no hacia falta: al vivir solo en RAM, cada arranque empezaba
     * siempre vacio. Ahora que el repositorio persiste en disco, los
     * datos SI sobreviven entre ejecuciones, asi que hay que cargar la
     * semilla solo la primera vez (repositorio vacio); en caso contrario
     * duplicariamos los alumnos de ejemplo en cada reinicio.
     */
    @PostConstruct
    private void inicializarDatosSemilla() {
        if (alumnoRepository.findAll().isEmpty()) {
            cargarDatosSemilla();
        }
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
