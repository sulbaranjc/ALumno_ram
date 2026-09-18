# CRUD de Alumno — Persistencia por serialización de objetos (acceso secuencial)

Proyecto pedagógico de la asignatura **Acceso a Datos** (ciclo de Desarrollo
de Aplicaciones Multiplataforma). Este documento explica, a nivel de código,
cómo funciona la rama `pers_dis_bin_serializados`, cuyo objetivo es persistir
los alumnos mediante **serialización de objetos Java**, leyendo y escribiendo
siempre en disco, **sin mantener ninguna lista en memoria**.

No es un manual de usuario: es un documento para **estudiar el código**.
Cada sección remite a las clases y métodos reales del proyecto.

---

## 1. Punto de partida: "hay demasiados datos para la RAM"

Esta rama es **hermana** de `pers_dis_bin` (ambas parten de `pers_disc`, la
versión con `ArrayList`), pero resuelve el mismo problema — persistir en
disco — con una premisa de diseño distinta y una técnica distinta:

| Rama | Técnica | Premisa de partida |
|---|---|---|
| `pers_dis_bin` | `RandomAccessFile`, registros de longitud **fija** | Se puede fijar de antemano un tamaño máximo por campo; a cambio, se gana acceso **directo** (`seek` calculado) |
| `pers_dis_bin_serializados` (esta rama) | `ObjectOutputStream`/`ObjectInputStream`, serialización | La tabla puede ser tan grande que **no cabe en memoria**; ninguna consulta guarda una lista en un campo de la clase — cada llamada lee del disco |

Esa premisa ("no se puede cargar todo en memoria") es la que justifica cada
decisión de diseño de esta rama: por qué la búsqueda es secuencial, por qué
el contador de IDs vive en su propio fichero, y por qué actualizar/eliminar
cuesta más caro que en la rama hermana.

---

## 2. Arquitectura en capas (no cambia)

```
ConsoleMenu  --------->  AlumnoService  --------->  AlumnoRepository (interfaz)
(consola)                (reglas de negocio)              ^
                                                            |
                                        AlumnoRepositorySerializadoImpl
                                     (ObjectOutputStream/InputStream + data/*.dat)
```

Igual que en la rama hermana, el
[`AlumnoService`](src/main/java/org/example/alumno/service/AlumnoServiceImpl.java)
y el
[`ConsoleMenu`](src/main/java/org/example/alumno/console/ConsoleMenu.java)
dependen únicamente de la interfaz
[`AlumnoRepository`](src/main/java/org/example/alumno/repository/AlumnoRepository.java),
nunca de su implementación concreta. Por eso, cambiar la tecnología de
persistencia (de `ArrayList`, a acceso directo, a serialización) nunca ha
exigido tocar la capa de negocio ni la de consola — es la consecuencia
práctica del **patrón Repository** y la **inversión de dependencias**.

---

## 3. El modelo: `Alumno` SÍ implementa `Serializable`

Ver [`Alumno.java`](src/main/java/org/example/alumno/model/Alumno.java).

A diferencia de la rama `pers_dis_bin` (donde `Alumno` es una clase Java
normal, porque los campos se escriben "a mano" con `RandomAccessFile`),
aquí `Alumno` **sí** implementa `java.io.Serializable`:

```java
public class Alumno implements Serializable {
    private static final long serialVersionUID = 1L;
    ...
```

`Serializable` es una interfaz **marcadora** (no declara métodos): solo le
dice a la JVM "autorizo a que los objetos de esta clase se conviertan en
una secuencia de bytes". Gracias a eso, `ObjectOutputStream.writeObject(alumno)`
sabe convertir automáticamente el objeto entero en bytes, y
`ObjectInputStream.readObject()` sabe reconstruirlo, sin que tengamos que
escribir campo a campo como en la rama hermana.

`serialVersionUID` se fija a mano (en vez de dejar que el IDE lo
autogenere) para controlar nosotros cuándo cambia el "contrato binario" de
la clase: si en el futuro se añaden o quitan atributos sin actualizar este
número, Java avisará con `InvalidClassException` al leer un fichero
guardado con la versión anterior, en vez de fallar de forma silenciosa o
con datos corruptos.

---

## 4. Por qué la búsqueda es secuencial (no directa)

Con serialización, cada `Alumno` ocupa un número de bytes **distinto**:
depende de la longitud real de su nombre, apellido y correo (a diferencia
de la rama `pers_dis_bin`, donde esos campos se rellenan/truncan a una
longitud fija). Sin un tamaño de registro constante, **no existe una
fórmula** que diga "el alumno con id=7 empieza en el byte X".

La única forma de encontrar un alumno es leerlos en el orden en que están
guardados, uno a uno, hasta dar con él (o hasta el final del fichero si no
está). Es el precio que se paga por no tener que fijar de antemano una
longitud máxima para los textos.

---

## 5. El formato de `data/alumnos.dat`

Toda la lógica vive en
[`AlumnoRepositorySerializadoImpl`](src/main/java/org/example/alumno/repository/AlumnoRepositorySerializadoImpl.java).

Una opción ingenua sería serializar **una `List<Alumno>` entera** de una
sola vez (`writeObject(listaCompleta)`). Se descartó a propósito: eso
obligaría a leer y reescribir el fichero completo incluso para dar de alta
un único alumno — justo lo que se quiere evitar cuando "hay demasiados
datos para la RAM".

En su lugar, cada `Alumno` se serializa **por separado**, uno detrás de
otro:

```
[cabecera ObjectOutputStream] [Alumno 1] [Alumno 2] [Alumno 3] ...
```

Para leerlos, se abre un único `ObjectInputStream` y se llama a
`readObject()` en bucle. Cuando ya no quedan más registros, salta
`EOFException` — y aquí eso **no es un error**, es la señal normal de "fin
del fichero" (ver
[`leerSiguiente`](src/main/java/org/example/alumno/repository/AlumnoRepositorySerializadoImpl.java:295)):

```java
private Alumno leerSiguiente(ObjectInputStream entrada) throws IOException {
    try {
        return (Alumno) entrada.readObject();
    } catch (EOFException finDelFichero) {
        return null; // no es un error: asi se sabe que no quedan mas registros
    } catch (ClassNotFoundException e) {
        throw new IllegalStateException("Registro corrupto en el fichero de alumnos", e);
    }
}
```

---

## 6. Recorrido método a método

### `findById(id)` / `existsById(id)` — secuencial, con salida anticipada

```java
try (ObjectInputStream entrada = abrirLectura(FICHERO_ALUMNOS)) {
    Alumno actual;
    while ((actual = leerSiguiente(entrada)) != null) {
        if (actual.getId().equals(id)) {
            return Optional.of(actual); // encontrado: no hace falta leer el resto
        }
    }
    return Optional.empty();
}
```

No hay ninguna lista en memoria consultándose aquí: cada llamada abre el
fichero desde el principio y lee objeto a objeto. Si el alumno buscado
está cerca del principio, se para pronto; si está al final (o no existe),
hay que leer el fichero entero. `existsById` simplemente reutiliza
`findById(id).isPresent()`.

### `findAll()` — aquí no hay atajo posible

```java
try (ObjectInputStream entrada = abrirLectura(FICHERO_ALUMNOS)) {
    Alumno alumno;
    while ((alumno = leerSiguiente(entrada)) != null) {
        resultado.add(alumno);
    }
}
```

**Importante:** listar *todos* los alumnos obliga a leer el fichero
completo, sin excepción. El acceso secuencial no acelera esta operación
(a diferencia del acceso directo, que tampoco la acelera, por cierto: ver
el README de `pers_dis_bin`). En un sistema real con "demasiados datos
para la RAM", esta operación se resolvería con **paginación** (leer solo
los siguientes N alumnos cada vez) en lugar de devolver una
`List<Alumno>` completa como hace este ejercicio por simplicidad.

### `save(alumno)` con id nulo — alta barata, al final

```java
if (alumno.getId() == null) {
    alumno.setId(generarSiguienteId());
    anadirAlFinal(alumno);
    return alumno;
}
```

Dar de alta un alumno **no** obliga a tocar los que ya había: solo se
añaden bytes al final del fichero. Es una operación barata, igual de
rápida tenga el fichero 5 alumnos o 5 millones.

### El contador de IDs vive en *otro* fichero

```java
private long generarSiguienteId() {
    try (RandomAccessFile contador = new RandomAccessFile(FICHERO_SECUENCIA.toFile(), "rw")) {
        long ultimoId = contador.length() >= Long.BYTES ? contador.readLong() : 0L;
        long nuevoId = ultimoId + 1;
        contador.seek(0);
        contador.writeLong(nuevoId);
        return nuevoId;
    }
}
```

Si para calcular "el siguiente id" tuviéramos que leer *todo* el fichero
grande buscando el mayor id existente, cada alta sería cada vez más lenta
a medida que crece la tabla — justo lo contrario de lo que se busca. Por
eso el último id asignado se guarda en un fichero aparte y diminuto,
`data/secuencia.dat` (un único `long`, 8 bytes), que se lee y actualiza en
tiempo **constante**, sin importar cuántos alumnos haya. Es la misma idea
que un contador `AUTO_INCREMENT` en una base de datos real: vive en un
sitio aparte, no se recalcula escaneando toda la tabla.

### El truco para poder anexar objetos sin romper el fichero

```java
private void anadirAlFinal(Alumno alumno) {
    boolean ficheroConContenido = !ficheroVacioOInexistente(FICHERO_ALUMNOS);
    try (FileOutputStream destino = new FileOutputStream(FICHERO_ALUMNOS.toFile(), true);
         ObjectOutputStream salida = ficheroConContenido
                 ? new ObjectOutputStreamSinCabecera(destino)
                 : new ObjectOutputStream(destino)) {
        salida.writeObject(alumno);
    }
}
```

Este es uno de los "gotchas" más conocidos de `java.io`: **cada vez** que
se construye un `ObjectOutputStream`, escribe automáticamente una pequeña
cabecera al principio del flujo (unos bytes mágicos + versión). Si para
añadir un alumno más abrimos el fichero en modo *append* y creamos un
`ObjectOutputStream` normal, escribirá **otra** cabecera en mitad del
fichero. Al releerlo secuencialmente más tarde, `readObject()` intentará
interpretar esos bytes de cabecera como si fueran datos de un objeto, y
lanzará `StreamCorruptedException`.

La solución (ver
[`ObjectOutputStreamSinCabecera`](src/main/java/org/example/alumno/repository/AlumnoRepositorySerializadoImpl.java:316))
es una subclase que sobrescribe `writeStreamHeader()` para no escribir
nada, y se usa **solo** cuando el fichero ya tiene contenido (y por tanto
ya tiene su cabecera):

```java
private static class ObjectOutputStreamSinCabecera extends ObjectOutputStream {
    ObjectOutputStreamSinCabecera(OutputStream salida) throws IOException {
        super(salida);
    }
    @Override
    protected void writeStreamHeader() throws IOException {
        reset(); // deliberadamente no se escribe cabecera: el fichero ya tiene una
    }
}
```

### `save(alumno)` con id existente / `deleteById(id)` — regenerar el fichero

Como los registros tienen longitud **variable**, no se puede sobrescribir
uno "in situ": si el nuevo valor ocupa más o menos bytes que el antiguo,
se descuadrarían todos los registros siguientes. La única forma correcta
de modificar o eliminar un registro es **regenerar el fichero completo**
(ver
[`regenerarFichero`](src/main/java/org/example/alumno/repository/AlumnoRepositorySerializadoImpl.java:256)):

```java
private boolean regenerarFichero(long idObjetivo, Alumno reemplazo) {
    try (ObjectInputStream entrada = abrirLectura(FICHERO_ALUMNOS);
         ObjectOutputStream salida = new ObjectOutputStream(new FileOutputStream(FICHERO_TEMPORAL.toFile()))) {
        Alumno actual;
        while ((actual = leerSiguiente(entrada)) != null) {
            if (actual.getId() == idObjetivo) {
                encontrado = true;
                if (reemplazo != null) salida.writeObject(reemplazo); // actualizar
                // reemplazo == null: no se escribe nada -> el alumno desaparece (eliminar)
            } else {
                salida.writeObject(actual); // los demas se copian tal cual
            }
        }
    }
    Files.move(FICHERO_TEMPORAL, FICHERO_ALUMNOS, StandardCopyOption.REPLACE_EXISTING);
    ...
}
```

Este mismo método sirve tanto para **actualizar** (`reemplazo != null`)
como para **eliminar** (`reemplazo == null`) — la única diferencia es qué
se escribe (o no) cuando se encuentra el registro objetivo.

**El detalle más importante para entender el trade-off de esta técnica:**
es una copia en *streaming* — en cada vuelta del bucle solo hay **un**
alumno en memoria a la vez, nunca la lista completa. El consumo de
memoria no depende del tamaño de la tabla. Pero el **coste en disco** sí:
hay que leer y volver a escribir el fichero entero. Es exactamente el
trade-off contrario al de la rama `pers_dis_bin`, donde actualizar es
barato (sobrescritura in situ) a cambio de tener que fijar longitudes
máximas de antemano.

### `deleteAll()` — resetear

```java
Files.deleteIfExists(FICHERO_ALUMNOS);
Files.deleteIfExists(FICHERO_SECUENCIA);
```

Se borran los dos ficheros: el de datos y el del contador. Así el
siguiente alumno que se cree vuelve a empezar en `id = 1`.

---

## 7. Comparativa con la rama hermana `pers_dis_bin`

| | `pers_dis_bin` (acceso directo) | `pers_dis_bin_serializados` (esta rama) |
|---|---|---|
| Técnica | `RandomAccessFile`, registros de longitud fija | `ObjectOutputStream`/`ObjectInputStream`, serialización |
| `Alumno` | Clase normal (sin `Serializable`) | Implementa `Serializable` |
| Buscar por ID | **Directo**: `seek((id-1)*TAMANO_REGISTRO)` | **Secuencial**: leer desde el principio hasta encontrarlo |
| Listar todos | Recorre todos los huecos (inevitable) | Recorre todo el fichero (inevitable) |
| Alta | Al final; puede colisionar con huecos borrados no reutilizados | Al final (`append`), con contador de IDs en fichero aparte |
| Actualizar | Sobrescritura **in situ** (barata) | **Regenera** el fichero completo (streaming, pero O(n) en disco) |
| Eliminar | Baja lógica (*tombstone*), hueco no reutilizado | Regenera el fichero completo (el registro desaparece de verdad) |
| Longitud de texto | Fija: se trunca o se rellena con espacios | Variable: cada alumno ocupa lo que necesite |
| Cuándo conviene | Se conocen límites razonables de longitud y se buscan muchas veces registros individuales por clave | Los textos no tienen un límite claro, o interesa un formato más simple aunque las modificaciones sean más caras |

---

## 8. Ejercicios propuestos

1. **Provocar el `StreamCorruptedException` a propósito.** Comenta
   temporalmente la clase `ObjectOutputStreamSinCabecera` en `anadirAlFinal`
   (usa siempre `new ObjectOutputStream(destino)`), crea dos alumnos en
   ejecuciones separadas y observa el error al listar. Vuelve a
   descomentarlo y comprueba que se soluciona.
2. **Medir el coste de una actualización.** Añade un contador de "objetos
   leídos" y otro de "objetos escritos" dentro de `regenerarFichero`.
   Crea 20 alumnos y actualiza el primero: ¿cuántas lecturas/escrituras
   se hacen? ¿Cambiaría si actualizases el último en vez del primero?
3. **Comparar con la rama hermana.** En `pers_dis_bin`, mide (o razona)
   cuántas operaciones de E/S hacen falta para lo mismo. ¿En qué escenario
   preferirías cada técnica?
4. **Ver los bytes en crudo.** Con `xxd data/alumnos.dat | head -20`,
   localiza la cabecera del `ObjectOutputStream` al principio del fichero
   (bytes `ac ed 00 05`). Comprueba que solo aparece **una vez**, aunque
   hayas creado varios alumnos en ejecuciones distintas.
5. **Simular "muchos datos".** Crea un bucle (fuera de la consola, en un
   pequeño test o script) que dé de alta varios miles de alumnos. Observa
   que el tiempo de alta no crece apreciablemente, pero que buscar el
   último alumno por ID sí tarda más que buscar el primero.
6. **Diseñar un índice.** Como reto, plantea (en papel) cómo se podría
   acelerar `findById` sin volver a la técnica de longitud fija: por
   ejemplo, manteniendo un fichero de índice aparte que mapee
   `id -> posición en el fichero`. ¿Qué pasaría con ese índice cada vez
   que se regenera `alumnos.dat`?

---

## 9. Cómo ejecutar el proyecto

```bash
./mvnw spring-boot:run
```

Los datos se guardan en `data/alumnos.dat` (los alumnos) y
`data/secuencia.dat` (el contador de IDs), dentro del directorio desde el
que se ejecuta la aplicación. Esa carpeta está excluida de git
(ver [`.gitignore`](.gitignore)): son artefactos que se generan en tiempo
de ejecución, no código fuente.

Para volver a empezar desde cero:

```bash
rm -rf data/
```

o usar la opción **6. Resetear datos** del menú de consola.

---

## 10. Glosario rápido

- **Acceso secuencial**: leer un fichero desde el principio, en orden,
  hasta encontrar lo que se busca (o llegar al final).
- **Serialización**: mecanismo de Java que convierte un objeto entero en
  una secuencia de bytes automáticamente (y viceversa), a través de
  `Serializable` + `ObjectOutputStream`/`ObjectInputStream`.
- **Registro de longitud variable**: registro que ocupa un número de
  bytes distinto según su contenido — impide calcular su posición con una
  fórmula, a diferencia de un registro de longitud fija.
- **Cabecera de stream (`stream header`)**: bytes que `ObjectOutputStream`
  escribe automáticamente al principio de cada flujo que crea; escribir
  más de una en el mismo fichero lo corrompe para la lectura secuencial.
- **Regeneración de fichero**: técnica para modificar/eliminar registros
  de longitud variable: se lee el fichero viejo y se escribe uno nuevo con
  el cambio aplicado, en lugar de sobrescribir en el sitio.
- **Copia en *streaming***: procesar un fichero grande leyendo y
  escribiendo de a un registro cada vez, sin cargar nunca el conjunto
  completo en memoria.
