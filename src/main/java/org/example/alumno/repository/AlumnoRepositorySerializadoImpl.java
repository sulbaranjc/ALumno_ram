package org.example.alumno.repository;

import org.example.alumno.model.Alumno;
import org.springframework.stereotype.Repository;

import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementacion de {@link AlumnoRepository} que persiste los alumnos
 * mediante SERIALIZACION DE OBJETOS ({@link ObjectOutputStream} /
 * {@link ObjectInputStream}), leyendo y escribiendo siempre en DISCO,
 * sin mantener ninguna lista de alumnos en memoria.
 *
 * ------------------------------------------------------------------
 * Punto de partida pedagogico: "hay demasiados datos para la RAM"
 * ------------------------------------------------------------------
 * A diferencia de la version con ArrayList (rama pers_disc) o de la
 * version con RandomAccessFile (rama pers_dis_bin, que SI mantenia una
 * cache y ademas hacia acceso directo), aqui partimos de la premisa de
 * que la tabla de alumnos puede ser demasiado grande para cargarla
 * entera en memoria. Consecuencia: ninguna consulta (buscar por id,
 * comprobar si existe...) guarda nada en un campo de la clase; cada
 * llamada abre el fichero, lee lo que necesita, y lo cierra.
 *
 * ------------------------------------------------------------------
 * Por que aqui la busqueda es SECUENCIAL (y no directa)
 * ------------------------------------------------------------------
 * Cada Alumno serializado ocupa un numero de bytes DISTINTO (depende de
 * la longitud de su nombre, apellido y correo). Al no haber un tamano
 * fijo de registro, no se puede calcular "el alumno con id=7 empieza en
 * el byte X": la unica forma de encontrarlo es leer los alumnos en el
 * orden en que estan guardados hasta dar con el (o hasta el final del
 * fichero). Es el precio que se paga por no tener que fijar de antemano
 * una longitud maxima para los textos, como si hacia falta en la rama
 * pers_dis_bin.
 *
 * ------------------------------------------------------------------
 * Formato de data/alumnos.dat
 * ------------------------------------------------------------------
 * NO es "una lista serializada de una vez" (writeObject(listaCompleta)):
 * eso obligaria a reescribir el fichero entero en cada alta, y a leerlo
 * entero para poder anadir un solo alumno mas. En su lugar, cada Alumno
 * se serializa POR SEPARADO, uno detras de otro:
 *
 *      [cabecera ObjectOutputStream] [Alumno 1] [Alumno 2] [Alumno 3] ...
 *
 * Para leerlos, se abre un unico ObjectInputStream y se llama a
 * readObject() en bucle hasta que salta EOFException: esa excepcion, en
 * este formato, NO es un error, es la forma normal de saber que "ya no
 * hay mas registros" (vease leerSiguiente()).
 *
 * ------------------------------------------------------------------
 * El problema de "anadir" objetos a un fichero ya existente
 * ------------------------------------------------------------------
 * Cada vez que se construye un ObjectOutputStream, escribe una pequena
 * cabecera al principio del flujo. Si para dar de alta UN alumno mas
 * abrimos el fichero en modo "append" y creamos un ObjectOutputStream
 * normal, escribira OTRA cabecera en mitad del fichero, y al releerlo
 * secuencialmente, readObject() interpretara esos bytes de cabecera
 * como si fueran datos de un objeto y lanzara StreamCorruptedException.
 *
 * La solucion estandar (y muy conocida en Java) es la subclase
 * {@link ObjectOutputStreamSinCabecera}: cuando el fichero YA tiene
 * contenido, se usa esa subclase, que se salta la escritura de la
 * cabecera.
 *
 * ------------------------------------------------------------------
 * Por que "modificar" y "eliminar" son mas caros aqui
 * ------------------------------------------------------------------
 * Como los registros tienen longitud variable, no se puede sobrescribir
 * uno "in situ" sin arriesgarse a descuadrar los siguientes. Por eso,
 * actualizar o eliminar un alumno obliga a REGENERAR el fichero completo
 * (vease regenerarFichero): se lee el fichero viejo alumno a alumno y se
 * va escribiendo un fichero nuevo, aplicando el cambio necesario solo al
 * registro afectado. Es una copia en STREAMING (nunca se tienen todos
 * los alumnos en memoria a la vez), pero el COSTE EN DISCO si depende
 * del tamano total del fichero. Es justo el trade-off contrario al de
 * la rama pers_dis_bin, donde actualizar es barato (in situ) pero hay
 * que fijar longitudes maximas de antemano.
 *
 * ------------------------------------------------------------------
 * Por que el contador de ids vive en OTRO fichero, aparte
 * ------------------------------------------------------------------
 * Si para dar de alta un alumno tuvieramos que leer TODO el fichero
 * grande solo para calcular "el mayor id + 1", cada alta seria cada vez
 * mas lenta a medida que crece la tabla: justo lo que queremos evitar.
 * Por eso el siguiente id disponible se guarda en un fichero aparte y
 * diminuto, data/secuencia.dat (un unico long), que se lee y actualiza
 * en tiempo constante sin importar cuantos alumnos haya.
 */
@Repository
public class AlumnoRepositorySerializadoImpl implements AlumnoRepository {

    /** Carpeta donde se guardan los ficheros de datos de la aplicacion. */
    private static final Path CARPETA_DATOS = Path.of("data");

    /** Fichero secuencial con los alumnos serializados, uno tras otro. */
    private static final Path FICHERO_ALUMNOS = CARPETA_DATOS.resolve("alumnos.dat");

    /** Fichero auxiliar, diminuto, que solo guarda el ultimo id asignado. */
    private static final Path FICHERO_SECUENCIA = CARPETA_DATOS.resolve("secuencia.dat");

    /** Fichero temporal usado mientras se regenera alumnos.dat. */
    private static final Path FICHERO_TEMPORAL = CARPETA_DATOS.resolve("alumnos.dat.tmp");

    @Override
    public List<Alumno> findAll() {
        List<Alumno> resultado = new ArrayList<>();
        if (ficheroVacioOInexistente(FICHERO_ALUMNOS)) {
            return resultado;
        }
        // OJO: listar TODOS los alumnos obliga a leer el fichero entero.
        // El acceso secuencial no tiene atajo para esta operacion; en un
        // sistema real con "demasiados datos para la RAM" esto se
        // resolveria con paginacion (leer solo los siguientes N alumnos),
        // no devolviendo una List<Alumno> completa como hace aqui.
        try (ObjectInputStream entrada = abrirLectura(FICHERO_ALUMNOS)) {
            Alumno alumno;
            while ((alumno = leerSiguiente(entrada)) != null) {
                resultado.add(alumno);
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "No se pudo leer el fichero de alumnos: " + FICHERO_ALUMNOS, e);
        }
        return resultado;
    }

    @Override
    public Optional<Alumno> findById(Long id) {
        if (id == null || ficheroVacioOInexistente(FICHERO_ALUMNOS)) {
            return Optional.empty();
        }
        try (ObjectInputStream entrada = abrirLectura(FICHERO_ALUMNOS)) {
            Alumno actual;
            while ((actual = leerSiguiente(entrada)) != null) {
                if (actual.getId().equals(id)) {
                    return Optional.of(actual); // encontrado: no hace falta leer el resto del fichero
                }
            }
            return Optional.empty(); // recorrimos todo el fichero y no estaba
        } catch (IOException e) {
            throw new IllegalStateException(
                    "No se pudo leer el alumno con ID " + id, e);
        }
    }

    @Override
    public boolean existsById(Long id) {
        return findById(id).isPresent();
    }

    @Override
    public Alumno save(Alumno alumno) {
        if (alumno.getId() == null) {
            alumno.setId(generarSiguienteId());
            anadirAlFinal(alumno); // alta: operacion barata, no toca los alumnos ya existentes
            return alumno;
        }
        boolean encontrado = regenerarFichero(alumno.getId(), alumno);
        if (!encontrado) {
            // El id no existia todavia: lo damos de alta igualmente (upsert),
            // igual que hacia la version en ArrayList de pers_disc.
            anadirAlFinal(alumno);
        }
        return alumno;
    }

    @Override
    public boolean deleteById(Long id) {
        if (id == null) {
            return false;
        }
        return regenerarFichero(id, null);
    }

    @Override
    public void deleteAll() {
        try {
            Files.deleteIfExists(FICHERO_ALUMNOS);
            Files.deleteIfExists(FICHERO_SECUENCIA);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudieron borrar los ficheros de datos", e);
        }
    }

    /**
     * Da de alta el siguiente id disponible SIN tocar el fichero grande
     * de alumnos: lee y actualiza un unico long en un fichero aparte.
     */
    private long generarSiguienteId() {
        try {
            Files.createDirectories(CARPETA_DATOS);
            try (RandomAccessFile contador = new RandomAccessFile(FICHERO_SECUENCIA.toFile(), "rw")) {
                long ultimoId = contador.length() >= Long.BYTES ? contador.readLong() : 0L;
                long nuevoId = ultimoId + 1;
                contador.seek(0);
                contador.writeLong(nuevoId);
                return nuevoId;
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "No se pudo generar el siguiente id: " + FICHERO_SECUENCIA, e);
        }
    }

    /**
     * Anade un alumno al FINAL del fichero sin tocar los que ya habia.
     * Si el fichero ya tenia contenido (y por tanto ya tiene una
     * cabecera de ObjectOutputStream escrita), se usa la variante que
     * NO vuelve a escribir cabecera (vease ObjectOutputStreamSinCabecera).
     */
    private void anadirAlFinal(Alumno alumno) {
        try {
            Files.createDirectories(CARPETA_DATOS);
            boolean ficheroConContenido = !ficheroVacioOInexistente(FICHERO_ALUMNOS);
            try (FileOutputStream destino = new FileOutputStream(FICHERO_ALUMNOS.toFile(), true);
                 ObjectOutputStream salida = ficheroConContenido
                         ? new ObjectOutputStreamSinCabecera(destino)
                         : new ObjectOutputStream(destino)) {
                salida.writeObject(alumno);
            }
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo guardar el alumno: " + alumno, e);
        }
    }

    /**
     * Recorre alumnos.dat de principio a fin y escribe un fichero nuevo:
     * - Si un alumno tiene el id buscado y reemplazo != null, se escribe
     *   reemplazo en su lugar (actualizar).
     * - Si un alumno tiene el id buscado y reemplazo == null, se omite
     *   (eliminar).
     * - Cualquier otro alumno se copia tal cual.
     *
     * Es una copia en streaming: en cada vuelta del bucle solo hay UN
     * alumno en memoria, nunca la lista completa. Al terminar, el
     * fichero temporal sustituye al original.
     *
     * @return true si se encontro (y por tanto se actualizo/elimino) el
     *         alumno con ese id; false si el fichero no lo contenia.
     */
    private boolean regenerarFichero(long idObjetivo, Alumno reemplazo) {
        if (ficheroVacioOInexistente(FICHERO_ALUMNOS)) {
            return false;
        }
        boolean encontrado = false;
        try (ObjectInputStream entrada = abrirLectura(FICHERO_ALUMNOS);
             ObjectOutputStream salida = new ObjectOutputStream(new FileOutputStream(FICHERO_TEMPORAL.toFile()))) {
            Alumno actual;
            while ((actual = leerSiguiente(entrada)) != null) {
                if (actual.getId() == idObjetivo) {
                    encontrado = true;
                    if (reemplazo != null) {
                        salida.writeObject(reemplazo);
                    }
                    // reemplazo == null (eliminar): no se escribe nada, el alumno desaparece
                } else {
                    salida.writeObject(actual);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo regenerar el fichero de alumnos", e);
        }
        try {
            Files.move(FICHERO_TEMPORAL, FICHERO_ALUMNOS, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo reemplazar el fichero de alumnos", e);
        }
        return encontrado;
    }

    private ObjectInputStream abrirLectura(Path fichero) throws IOException {
        return new ObjectInputStream(new FileInputStream(fichero.toFile()));
    }

    /**
     * Lee un unico Alumno del flujo, o null si se ha llegado al final.
     * EOFException aqui no es un error: es la senal, dentro de este
     * formato, de que ya no quedan mas registros por leer.
     */
    private Alumno leerSiguiente(ObjectInputStream entrada) throws IOException {
        try {
            return (Alumno) entrada.readObject();
        } catch (EOFException finDelFichero) {
            return null;
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Registro corrupto en el fichero de alumnos", e);
        }
    }

    private boolean ficheroVacioOInexistente(Path fichero) {
        return Files.notExists(fichero) || fichero.toFile().length() == 0;
    }

    /**
     * ObjectOutputStream que NO escribe la cabecera inicial. Se usa
     * exclusivamente para anadir un objeto mas a un fichero que ya tiene
     * contenido (y por tanto ya tiene su cabecera escrita al principio).
     * Sin este truco, cada alta escribiria una cabecera nueva en mitad
     * del fichero y lo dejaria corrupto para la lectura secuencial.
     */
    private static class ObjectOutputStreamSinCabecera extends ObjectOutputStream {

        ObjectOutputStreamSinCabecera(OutputStream salida) throws IOException {
            super(salida);
        }

        @Override
        protected void writeStreamHeader() throws IOException {
            reset(); // deliberadamente no se escribe cabecera: el fichero ya tiene una
        }
    }
}
