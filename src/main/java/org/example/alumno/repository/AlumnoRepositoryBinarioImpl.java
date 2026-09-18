package org.example.alumno.repository;

import org.example.alumno.model.Alumno;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementacion de {@link AlumnoRepository} que persiste los alumnos en un
 * fichero binario de ACCESO DIRECTO (tambien llamado "acceso aleatorio"),
 * usando {@link RandomAccessFile} y registros de tamano FIJO.
 *
 * ------------------------------------------------------------------
 * La idea clave: registros de longitud fija = posiciones calculables
 * ------------------------------------------------------------------
 * Si todos los registros del fichero ocupan exactamente el mismo numero
 * de bytes (TAMANO_REGISTRO), entonces el registro del alumno con
 * ID = n empieza SIEMPRE en el byte:
 *
 *      posicion = (n - 1) * TAMANO_REGISTRO
 *
 * Esto nos permite ir DIRECTOS a un alumno con raf.seek(posicion) sin
 * tener que leer (ni descartar) los registros anteriores. Es la gran
 * diferencia frente a la serializacion de objetos (ObjectOutputStream),
 * donde cada objeto ocupa un numero de bytes distinto y por tanto solo
 * se puede buscar leyendo de forma secuencial desde el principio.
 *
 * El precio a pagar: como los campos de texto (nombre, apellido, correo)
 * tienen longitud variable en la vida real, aqui se fija un maximo para
 * cada uno. Si un valor es mas corto se RELLENA con espacios; si es mas
 * largo, se TRUNCA. Esa es una limitacion real (y muy didactica) de este
 * tipo de ficheros.
 *
 * ------------------------------------------------------------------
 * Formato de cada registro (192 bytes, en este orden)
 * ------------------------------------------------------------------
 *   long    id                                  ->  8 bytes
 *   char[LONGITUD_NOMBRE]    nombre              -> 40 bytes (20 caracteres x 2)
 *   char[LONGITUD_APELLIDO]  apellido            -> 40 bytes (20 caracteres x 2)
 *   char[LONGITUD_CORREO]    correo              -> 80 bytes (40 caracteres x 2)
 *   double  nota1                                ->  8 bytes
 *   double  nota2                                ->  8 bytes
 *   double  nota3                                ->  8 bytes
 *
 * Nota: notaTotal NO se guarda en el fichero. Se recalcula sola al
 * reconstruir el Alumno (vease Alumno.recalcularNotaTotal()), asi que
 * guardarla seria informacion redundante.
 *
 * Cada caracter Java ocupa 2 bytes (UTF-16), por eso se usan
 * raf.writeChar()/raf.readChar() para el texto en lugar de writeByte().
 *
 * ------------------------------------------------------------------
 * Bajas logicas ("tombstones") y altas
 * ------------------------------------------------------------------
 * Al eliminar un alumno NO se acorta el fichero (eso obligaria a
 * desplazar todos los registros siguientes, perdiendo la ventaja del
 * acceso directo). En su lugar, se sobrescribe el campo id de ese hueco
 * con la marca MARCA_BORRADO: el hueco queda "vacio" pero reservado.
 *
 * Al dar de alta un alumno nuevo, se le asigna el siguiente hueco libre
 * al FINAL del fichero (no se reaprovechan huecos borrados intermedios,
 * para no complicar el calculo de la posicion). El id se calcula como
 * (numero de registros ya reservados en el fichero) + 1.
 */
@Repository
public class AlumnoRepositoryBinarioImpl implements AlumnoRepository {

    /** Carpeta donde se guardan los ficheros de datos de la aplicacion. */
    private static final Path CARPETA_DATOS = Path.of("data");

    /** Fichero de acceso directo donde se guardan los alumnos. */
    private static final Path FICHERO_ALUMNOS = CARPETA_DATOS.resolve("alumnos.dat");

    /** Longitud maxima (en caracteres) reservada para cada campo de texto. */
    private static final int LONGITUD_NOMBRE = 20;
    private static final int LONGITUD_APELLIDO = 20;
    private static final int LONGITUD_CORREO = 40;

    /** Cada char Java ocupa 2 bytes al escribirse con writeChar/readChar. */
    private static final int BYTES_POR_CARACTER = 2;

    /** Tamano en bytes de un id (long) o de una nota (double). */
    private static final int BYTES_LONG = Long.BYTES;
    private static final int BYTES_DOUBLE = Double.BYTES;

    /** Tamano total, FIJO, de cada registro del fichero. */
    private static final int TAMANO_REGISTRO =
            BYTES_LONG                                            // id
            + LONGITUD_NOMBRE * BYTES_POR_CARACTER                // nombre
            + LONGITUD_APELLIDO * BYTES_POR_CARACTER              // apellido
            + LONGITUD_CORREO * BYTES_POR_CARACTER                // correo
            + BYTES_DOUBLE * 3;                                   // nota1, nota2, nota3

    /** Valor especial de id que marca un hueco borrado / vacio. */
    private static final long MARCA_BORRADO = -1L;

    @Override
    public List<Alumno> findAll() {
        List<Alumno> resultado = new ArrayList<>();
        if (Files.notExists(FICHERO_ALUMNOS)) {
            return resultado;
        }
        try (RandomAccessFile raf = new RandomAccessFile(FICHERO_ALUMNOS.toFile(), "r")) {
            long totalHuecos = raf.length() / TAMANO_REGISTRO;
            // Listar TODOS los alumnos si exige recorrer todo el fichero
            // (acceso secuencial): el acceso directo solo acelera la
            // busqueda de UN registro concreto por su id.
            for (long numeroHueco = 1; numeroHueco <= totalHuecos; numeroHueco++) {
                raf.seek(calcularPosicion(numeroHueco));
                Alumno alumno = leerRegistro(raf);
                if (alumno != null) {
                    resultado.add(alumno);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "No se pudo leer el fichero de alumnos: " + FICHERO_ALUMNOS, e);
        }
        return resultado;
    }

    @Override
    public Optional<Alumno> findById(Long id) {
        if (id == null || id < 1 || Files.notExists(FICHERO_ALUMNOS)) {
            return Optional.empty();
        }
        try (RandomAccessFile raf = new RandomAccessFile(FICHERO_ALUMNOS.toFile(), "r")) {
            long posicion = calcularPosicion(id);
            if (posicion + TAMANO_REGISTRO > raf.length()) {
                return Optional.empty(); // el fichero no tiene (todavia) ese hueco
            }
            raf.seek(posicion); // <-- acceso DIRECTO: vamos al byte exacto, sin leer nada anterior
            return Optional.ofNullable(leerRegistro(raf));
        } catch (IOException e) {
            throw new IllegalStateException(
                    "No se pudo leer el alumno con ID " + id, e);
        }
    }

    @Override
    public Alumno save(Alumno alumno) {
        try {
            Files.createDirectories(CARPETA_DATOS);
            try (RandomAccessFile raf = new RandomAccessFile(FICHERO_ALUMNOS.toFile(), "rw")) {
                if (alumno.getId() == null) {
                    // Alta: se reserva el siguiente hueco libre al final del fichero.
                    long totalHuecos = raf.length() / TAMANO_REGISTRO;
                    alumno.setId(totalHuecos + 1);
                }
                raf.seek(calcularPosicion(alumno.getId()));
                escribirRegistro(raf, alumno);
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "No se pudo guardar el alumno: " + alumno, e);
        }
        return alumno;
    }

    @Override
    public boolean deleteById(Long id) {
        if (id == null || id < 1 || Files.notExists(FICHERO_ALUMNOS)) {
            return false;
        }
        try (RandomAccessFile raf = new RandomAccessFile(FICHERO_ALUMNOS.toFile(), "rw")) {
            long posicion = calcularPosicion(id);
            if (posicion + TAMANO_REGISTRO > raf.length()) {
                return false;
            }
            raf.seek(posicion);
            long idGuardado = raf.readLong();
            if (idGuardado != id) {
                return false; // ya estaba borrado (o el hueco nunca fue ese alumno)
            }
            raf.seek(posicion);
            raf.writeLong(MARCA_BORRADO); // baja logica: solo se marca el hueco, no se desplaza nada
            return true;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "No se pudo eliminar el alumno con ID " + id, e);
        }
    }

    @Override
    public boolean existsById(Long id) {
        return findById(id).isPresent();
    }

    @Override
    public void deleteAll() {
        try {
            Files.deleteIfExists(FICHERO_ALUMNOS);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "No se pudo borrar el fichero de alumnos: " + FICHERO_ALUMNOS, e);
        }
    }

    /**
     * Calcula el byte donde empieza el registro del hueco/id indicado.
     * Es la formula que convierte un id en una posicion de fichero y la
     * que hace posible el acceso directo.
     */
    private long calcularPosicion(long id) {
        return (id - 1) * TAMANO_REGISTRO;
    }

    /**
     * Escribe un Alumno completo en la posicion actual del fichero,
     * ocupando siempre TAMANO_REGISTRO bytes exactos.
     */
    private void escribirRegistro(RandomAccessFile raf, Alumno alumno) throws IOException {
        raf.writeLong(alumno.getId());
        escribirTextoFijo(raf, alumno.getNombre(), LONGITUD_NOMBRE);
        escribirTextoFijo(raf, alumno.getApellido(), LONGITUD_APELLIDO);
        escribirTextoFijo(raf, alumno.getCorreo(), LONGITUD_CORREO);
        raf.writeDouble(alumno.getNota1());
        raf.writeDouble(alumno.getNota2());
        raf.writeDouble(alumno.getNota3());
    }

    /**
     * Lee un registro completo desde la posicion actual del fichero.
     * Siempre se leen TODOS los bytes del registro (aunque este
     * marcado como borrado), para dejar el puntero justo al principio
     * del siguiente registro.
     *
     * @return el Alumno reconstruido, o null si el hueco esta vacio/borrado.
     */
    private Alumno leerRegistro(RandomAccessFile raf) throws IOException {
        long id = raf.readLong();
        String nombre = leerTextoFijo(raf, LONGITUD_NOMBRE);
        String apellido = leerTextoFijo(raf, LONGITUD_APELLIDO);
        String correo = leerTextoFijo(raf, LONGITUD_CORREO);
        double nota1 = raf.readDouble();
        double nota2 = raf.readDouble();
        double nota3 = raf.readDouble();

        if (id <= 0) {
            return null; // MARCA_BORRADO (-1) o un hueco que nunca se llego a escribir (0)
        }
        return new Alumno(id, nombre, apellido, correo, nota1, nota2, nota3);
    }

    /**
     * Escribe un texto ocupando EXACTAMENTE longitud caracteres: lo trunca
     * si es mas largo, o lo rellena con espacios si es mas corto. Esto es
     * lo que hace posible que el registro tenga siempre el mismo tamano.
     */
    private void escribirTextoFijo(RandomAccessFile raf, String texto, int longitud) throws IOException {
        String valor = texto == null ? "" : texto;
        if (valor.length() > longitud) {
            valor = valor.substring(0, longitud);
        }
        StringBuilder relleno = new StringBuilder(valor);
        while (relleno.length() < longitud) {
            relleno.append(' ');
        }
        raf.writeChars(relleno.toString());
    }

    /**
     * Lee exactamente longitud caracteres y quita el relleno de espacios
     * anadido por escribirTextoFijo.
     */
    private String leerTextoFijo(RandomAccessFile raf, int longitud) throws IOException {
        char[] caracteres = new char[longitud];
        for (int i = 0; i < longitud; i++) {
            caracteres[i] = raf.readChar();
        }
        return new String(caracteres).trim();
    }
}
