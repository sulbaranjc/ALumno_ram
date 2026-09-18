# CRUD de Alumno — Persistencia en fichero binario de acceso directo

Proyecto pedagógico de la asignatura **Acceso a Datos** (ciclo de Desarrollo
de Aplicaciones Multiplataforma). Este documento explica, a nivel de código,
cómo funciona la rama `pers_dis_bin`, cuyo objetivo es sustituir la
persistencia en memoria (`ArrayList`) por persistencia real en disco usando
**ficheros binarios de acceso directo**.

No es un manual de usuario: es un documento para **estudiar el código**.
Cada sección remite a las clases y métodos reales del proyecto.

---

## 1. Evolución del proyecto (para situarse)

| Rama | Persistencia | Técnica |
|---|---|---|
| `pers_disc` | En memoria (RAM) | `ArrayList<Alumno>` |
| `pers_dis_bin` (esta rama) | En disco | Fichero binario de **acceso directo** (`RandomAccessFile`) |

Lo interesante pedagógicamente es que **cambiar la tecnología de
persistencia no ha exigido tocar casi nada del resto de la aplicación**.
Eso no es casualidad: es la consecuencia de haber diseñado el código en
capas con el **patrón Repository**.

---

## 2. Arquitectura en capas

```
ConsoleMenu  --------->  AlumnoService  --------->  AlumnoRepository (interfaz)
(consola)                (reglas de negocio)              ^
                                                            |
                                          AlumnoRepositoryBinarioImpl
                                          (RandomAccessFile + data/alumnos.dat)
```

- [`ConsoleMenu`](src/main/java/org/example/alumno/console/ConsoleMenu.java)
  — capa de presentación. Lee opciones del usuario por teclado y llama al
  `Service`. No sabe nada de cómo se guardan los datos.
- [`AlumnoService`](src/main/java/org/example/alumno/service/AlumnoService.java) /
  [`AlumnoServiceImpl`](src/main/java/org/example/alumno/service/AlumnoServiceImpl.java)
  — reglas de negocio (por ejemplo, la semilla de datos de ejemplo). Depende
  únicamente de la **interfaz** `AlumnoRepository`, nunca de su implementación.
- [`AlumnoRepository`](src/main/java/org/example/alumno/repository/AlumnoRepository.java)
  — el **contrato** de acceso a datos: `findAll`, `findById`, `save`,
  `deleteById`, `existsById`, `deleteAll`. No menciona ArrayList ni ficheros
  ni bases de datos: solo el **qué**, nunca el **cómo**.
- [`AlumnoRepositoryBinarioImpl`](src/main/java/org/example/alumno/repository/AlumnoRepositoryBinarioImpl.java)
  — la implementación real de esta rama: sabe leer y escribir
  `data/alumnos.dat` con `RandomAccessFile`.

**Por qué importa esto:** Spring inyecta automáticamente
`AlumnoRepositoryBinarioImpl` en `AlumnoServiceImpl` porque es la única clase
que implementa `AlumnoRepository` (anotada con `@Repository`). Si mañana se
crea `AlumnoRepositoryJpaImpl` para una base de datos, bastaría con
sustituir esa implementación: ni `Service` ni `ConsoleMenu` se enterarían.
Esto es el **principio de inversión de dependencias** (la "D" de SOLID) en
la práctica.

---

## 3. El modelo: `Alumno`

Ver [`Alumno.java`](src/main/java/org/example/alumno/model/Alumno.java).

Dos detalles de diseño a destacar:

1. **`notaTotal` está encapsulada**: no tiene `setNotaTotal()`. Cada vez que
   se cambia `nota1`, `nota2` o `nota3` (o al construir el objeto), se llama
   automáticamente a `recalcularNotaTotal()`. Esto garantiza que
   `notaTotal` **nunca** queda desincronizada — es imposible tener un
   `Alumno` con notas y una media que no le corresponde.
2. **No implementa `Serializable`**. Esto es intencionado y se explica en
   la siguiente sección: esta rama usa una técnica de persistencia binaria
   que NO necesita esa interfaz.

---

## 4. Dos formas de guardar objetos en binario (y por qué elegimos una)

Antes de leer el repositorio, hay que entender la disyuntiva de diseño.

### Opción A — Serialización de objetos (`Serializable` + `ObjectOutputStream`)

Java convierte el objeto entero a bytes automáticamente. Es muy cómoda,
pero cada objeto ocupa un número de bytes **variable** (depende de la
longitud de sus `String`). Consecuencia: para encontrar un registro solo
se puede leer el fichero **secuencialmente**, desde el principio, hasta
dar con él (u hoja tras hoja hasta el final si no está).

### Opción B — Acceso directo con registros de longitud FIJA (`RandomAccessFile`)

Es la que usa este proyecto. Cada campo se escribe **a mano**
(`writeLong`, `writeChars`, `writeDouble`), reservando siempre el mismo
número de bytes por campo. Si **todos** los registros ocupan exactamente
lo mismo, el registro del alumno con `id = n` empieza siempre en el byte:

```
posicion = (n - 1) * TAMANO_REGISTRO
```

Eso permite ir **directos** a un registro con `raf.seek(posicion)`, sin
leer (ni descartar) los anteriores — de ahí el nombre "acceso directo" o
"acceso aleatorio", en contraste con el "acceso secuencial" de la opción A.

| | Serialización (A) | Acceso directo (B) — **esta rama** |
|---|---|---|
| Tamaño de cada registro | Variable | **Fijo** |
| Buscar por ID | Secuencial (leer desde el principio) | Directo (`seek` calculado) |
| Actualizar un registro | Reescribir todo el fichero | Sobrescribir solo ese registro |
| Strings | Cualquier longitud | Se trunca si es más larga de lo previsto |
| Clase modelo | Debe implementar `Serializable` | Clase Java normal |

**El precio a pagar** por el acceso directo: hay que fijar de antemano una
longitud máxima para `nombre`, `apellido` y `correo`. Si un valor es más
corto, se rellena con espacios; si es más largo, se trunca. Es una
limitación real de este tipo de ficheros y se puede comprobar
experimentalmente (ver [sección 8](#8-ejercicios-propuestos)).

---

## 5. El formato exacto del registro

Toda la lógica vive en
[`AlumnoRepositoryBinarioImpl`](src/main/java/org/example/alumno/repository/AlumnoRepositoryBinarioImpl.java).
Cada alumno ocupa **192 bytes**, siempre en este orden:

| Campo | Tipo Java | Bytes | Método usado |
|---|---|---|---|
| `id` | `long` | 8 | `writeLong` / `readLong` |
| `nombre` | texto fijo de 20 caracteres | 40 (20 × 2) | `writeChars` / `readChar` en bucle |
| `apellido` | texto fijo de 20 caracteres | 40 (20 × 2) | ídem |
| `correo` | texto fijo de 40 caracteres | 80 (40 × 2) | ídem |
| `nota1` | `double` | 8 | `writeDouble` / `readDouble` |
| `nota2` | `double` | 8 | ídem |
| `nota3` | `double` | 8 | ídem |
| **Total** | | **192 bytes** | |

Cada carácter Java ocupa **2 bytes** (codificación UTF-16), por eso
`writeChars`/`readChar` en vez de `writeByte` — importante para no
confundir "20 caracteres" con "20 bytes".

`notaTotal` **no se guarda**: al reconstruir el `Alumno` con su
constructor, se recalcula sola (ver sección 3). Guardarla sería
información redundante y, peor, podría desincronizarse si algún día se
edita el fichero a mano.

Estas constantes están todas juntas al principio de la clase para que
sea fácil comprobar la cuenta:

```java
private static final int TAMANO_REGISTRO =
        BYTES_LONG                               // id
        + LONGITUD_NOMBRE * BYTES_POR_CARACTER    // nombre
        + LONGITUD_APELLIDO * BYTES_POR_CARACTER  // apellido
        + LONGITUD_CORREO * BYTES_POR_CARACTER    // correo
        + BYTES_DOUBLE * 3;                       // nota1, nota2, nota3
```

---

## 6. Recorrido método a método

### `calcularPosicion(id)` — la fórmula que lo hace todo posible

```java
private long calcularPosicion(long id) {
    return (id - 1) * TAMANO_REGISTRO;
}
```

El alumno 1 empieza en el byte 0, el alumno 2 en el byte 192, el alumno 3
en el 384... Es aritmética pura, no hay que leer el fichero para saberlo.

### `findById(id)` — la estrella: acceso directo real

```java
raf.seek(posicion); // <-- vamos al byte exacto, sin leer nada anterior
return Optional.ofNullable(leerRegistro(raf));
```

Antes de la última refactorización, esta búsqueda recorría una lista en
memoria (`O(n)`, secuencial). Ahora `seek()` salta directamente al byte
donde empieza el registro — no importa si el fichero tiene 5 alumnos o
5 millones, el coste es el mismo.

### `findAll()` — aquí SÍ hace falta recorrer todo

```java
for (long numeroHueco = 1; numeroHueco <= totalHuecos; numeroHueco++) {
    raf.seek(calcularPosicion(numeroHueco));
    Alumno alumno = leerRegistro(raf);
    if (alumno != null) resultado.add(alumno);
}
```

**Importante para entender bien la técnica:** el acceso directo solo
acelera la búsqueda de **un** registro por su clave. Para *listar todos*
los alumnos no hay atajo posible: hay que visitar cada hueco, uno a uno.

### `save(alumno)` — alta (al final) o modificación (in situ)

```java
if (alumno.getId() == null) {
    long totalHuecos = raf.length() / TAMANO_REGISTRO;
    alumno.setId(totalHuecos + 1);   // alta: siguiente hueco libre, al final
}
raf.seek(calcularPosicion(alumno.getId()));
escribirRegistro(raf, alumno);
```

Si el alumno ya tiene `id`, `seek()` lo lleva exactamente a su hueco y lo
**sobrescribe** — sin tocar ni un byte de los demás registros. Esta es la
gran ventaja frente a la serialización de listas completas: actualizar
un alumno cuesta lo mismo tenga el fichero 5 registros o 5.000.

### `deleteById(id)` — baja lógica ("tombstone")

```java
raf.seek(posicion);
raf.writeLong(MARCA_BORRADO); // -1L: solo se marca el hueco, no se desplaza nada
```

No se acorta el fichero ni se desplazan los registros siguientes (eso
rompería el cálculo de posiciones de todos los alumnos posteriores). Se
sobrescribe solo el campo `id` con `-1`, dejando el hueco "vacío" pero
reservado. Al leer, cualquier registro con `id <= 0` se descarta
(`leerRegistro` devuelve `null`).

**Consecuencia importante:** ese hueco queda desperdiciado para siempre —
un alta posterior nunca lo reutiliza, siempre va al final
(`raf.length() / TAMANO_REGISTRO + 1`). Es una simplificación deliberada:
reutilizar huecos libres exigiría llevar una lista de "huecos libres" y
complicaría bastante el ejercicio.

### `deleteAll()` — resetear

```java
Files.deleteIfExists(FICHERO_ALUMNOS);
```

Simplemente se borra el fichero. El siguiente alumno que se cree
empezará otra vez en `id = 1`.

### `escribirTextoFijo` / `leerTextoFijo` — el truco de longitud fija

```java
private void escribirTextoFijo(RandomAccessFile raf, String texto, int longitud) {
    String valor = texto == null ? "" : texto;
    if (valor.length() > longitud) valor = valor.substring(0, longitud); // truncar
    while (valor no llegue a "longitud") valor += " ";                  // rellenar
    raf.writeChars(valor);
}
```

Esto es lo que garantiza que **todos** los registros midan exactamente lo
mismo, sin importar si el alumno se llama "Ana" o
"Maximiliano Fernández". Al leer (`leerTextoFijo`), se hace `.trim()`
para quitar el relleno de espacios.

---

## 7. Lo que NO cambió (y por qué es la parte más importante)

[`AlumnoServiceImpl`](src/main/java/org/example/alumno/service/AlumnoServiceImpl.java)
y [`ConsoleMenu`](src/main/java/org/example/alumno/console/ConsoleMenu.java)
apenas se tocaron al pasar de `ArrayList` a fichero binario. Solo hubo un
ajuste necesario, y es revelador:

```java
@PostConstruct
private void inicializarDatosSemilla() {
    if (alumnoRepository.findAll().isEmpty()) {   // <-- unico cambio real
        cargarDatosSemilla();
    }
}
```

Con `ArrayList` en RAM, cada arranque de la aplicación empezaba
**siempre** vacío (la memoria no sobrevive a un reinicio), así que cargar
la semilla incondicionalmente no era un problema. Ahora que los datos
persisten de verdad en disco, cargarla sin comprobar nada duplicaría los
5 alumnos de ejemplo en cada reinicio. Este es un ejemplo perfecto de por
qué "pasar a persistencia real" no es solo un cambio técnico: cambia
suposiciones que el resto del código daba por hechas.

---

## 8. Ejercicios propuestos

1. **Calcular una posición a mano.** Sin mirar el código, calcula en qué
   byte empieza el alumno con `id = 4` (pista: `TAMANO_REGISTRO = 192`).
   Comprueba tu resultado añadiendo un `System.out.println(posicion)` en
   `findById`.
2. **Ver los bytes en crudo.** Ejecuta la aplicación, crea un par de
   alumnos y luego inspecciona `data/alumnos.dat` con un visor hexadecimal:
   ```bash
   xxd data/alumnos.dat | head -20
   ```
   Localiza dónde empieza el segundo registro (byte 192) y compáralo con
   el primero.
3. **Provocar un truncamiento.** Crea un alumno con un nombre de más de
   20 caracteres. Observa que el mensaje de confirmación (en memoria)
   muestra el nombre completo, pero si luego lo listas (leído del disco),
   aparece cortado. ¿Por qué ocurre esa diferencia?
4. **Comprobar que la baja es lógica.** Elimina un alumno y vuelve a mirar
   el tamaño del fichero (`ls -l data/alumnos.dat`) antes y después. ¿Ha
   cambiado? Crea un alumno nuevo justo después: ¿qué `id` recibe? ¿Por
   qué no se reutiliza el hueco que acabas de dejar libre?
5. **Medir la ventaja del acceso directo.** Añade temporalmente un
   contador de "registros leídos" dentro de `findById` y otro dentro de
   una hipotética búsqueda secuencial (recorriendo `findAll()` y
   filtrando). Compara cuántos registros hay que leer en cada caso para
   encontrar el último alumno del fichero.
6. **Diseñar la reutilización de huecos.** Como reto, plantea (en papel o
   en un diagrama) cómo modificarías `save()` para que las altas
   reutilicen huecos marcados como borrados en lugar de ir siempre al
   final. ¿Qué estructura de datos adicional necesitarías?

---

## 9. Cómo ejecutar el proyecto

```bash
./mvnw spring-boot:run
```

Los datos se guardan en `data/alumnos.dat`, dentro del directorio desde el
que se ejecuta la aplicación. Esa carpeta está excluida de git
(ver [`.gitignore`](.gitignore)): es un artefacto que se genera en tiempo
de ejecución, no código fuente.

Para volver a empezar desde cero, basta con borrar el fichero:

```bash
rm -rf data/
```

o usar la opción **6. Resetear datos** del menú de consola.

---

## 10. Glosario rápido

- **Acceso secuencial**: leer un fichero desde el principio, en orden,
  hasta encontrar lo que se busca (o llegar al final).
- **Acceso directo / aleatorio**: saltar directamente a una posición
  concreta del fichero (`seek`) sin leer lo anterior.
- **Registro**: cada "fila" de datos guardada en el fichero (aquí, un
  `Alumno`).
- **Registro de longitud fija**: registro que siempre ocupa el mismo
  número de bytes, lo que permite calcular su posición con una fórmula.
- **Tombstone (baja lógica)**: marcar un registro como borrado sin
  eliminarlo físicamente ni desplazar los demás.
- **`RandomAccessFile`**: clase de `java.io` que permite leer y escribir
  en cualquier posición de un fichero, en lugar de solo secuencialmente.
