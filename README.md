# CRUD de Alumno — Base común y comparación de técnicas de persistencia

Proyecto pedagógico de la asignatura **Acceso a Datos** (ciclo de Desarrollo
de Aplicaciones Multiplataforma). Esta rama, `pers_disc`, es la **base
común** de la que parten dos ramas hermanas, cada una implementando una
técnica distinta de persistencia en fichero binario. Este documento explica
la base y sirve de guía para comparar ambas técnicas.

---

## 1. Qué hay en esta rama

Un CRUD de `Alumno` con persistencia **en memoria** (`ArrayList`) y una
interfaz de consola. Arquitectura en capas:

```
ConsoleMenu  --------->  AlumnoService  --------->  AlumnoRepository (interfaz)
(consola)                (reglas de negocio)              ^
                                                            |
                                                AlumnoRepositoryImpl
                                                (ArrayList<Alumno> en RAM)
```

- [`Alumno`](src/main/java/org/example/alumno/model/Alumno.java) — el
  modelo. `notaTotal` está encapsulada: se recalcula sola cada vez que
  cambia `nota1`, `nota2` o `nota3`, así que nunca puede quedar
  desincronizada.
- [`AlumnoRepository`](src/main/java/org/example/alumno/repository/AlumnoRepository.java) —
  el contrato de acceso a datos (`findAll`, `findById`, `save`,
  `deleteById`, `existsById`, `deleteAll`). No menciona ArrayList, ficheros
  ni bases de datos: solo el **qué**, nunca el **cómo**.
- [`AlumnoRepositoryImpl`](src/main/java/org/example/alumno/repository/AlumnoRepositoryImpl.java) —
  la implementación de esta rama: una simple lista en memoria.
- [`AlumnoService`](src/main/java/org/example/alumno/service/AlumnoServiceImpl.java) /
  [`ConsoleMenu`](src/main/java/org/example/alumno/console/ConsoleMenu.java) —
  reglas de negocio y presentación. Dependen únicamente de la **interfaz**
  `AlumnoRepository`, nunca de su implementación.

**Por qué importa esto para lo que viene después:** al depender solo de la
interfaz (patrón Repository + inversión de dependencias), es posible
sustituir `AlumnoRepositoryImpl` por una implementación que persista de
verdad en disco **sin tocar** `AlumnoService` ni `ConsoleMenu`. Eso es
exactamente lo que hacen las dos ramas hijas de `pers_disc`.

---

## 2. Las dos ramas hijas: mismo problema, dos técnicas distintas

A partir de este mismo punto de partida, se desarrollaron dos soluciones
para el mismo problema — "persistir los alumnos en disco, no solo en
RAM" — con premisas de diseño diferentes:

| | [`pers_dis_bin`](../../tree/pers_dis_bin) | [`pers_dis_bin_serializados`](../../tree/pers_dis_bin_serializados) |
|---|---|---|
| **Pull request** | [#2](../../pull/2) | [#1](../../pull/1) |
| **README de la rama** | [README.md](../../blob/pers_dis_bin/README.md) | [README.md](../../blob/pers_dis_bin_serializados/README.md) |
| **Técnica** | `RandomAccessFile`, registros de longitud **fija** | `ObjectOutputStream`/`ObjectInputStream`, serialización de objetos |
| **Premisa de partida** | Se puede fijar de antemano un tamaño máximo por campo | La tabla puede ser demasiado grande para caber en RAM |
| **`Alumno` implementa `Serializable`** | No (los campos se escriben "a mano") | Sí |
| **Buscar por ID** | **Directo**: `seek((id-1) * TAMANO_REGISTRO)`, sin leer nada anterior | **Secuencial**: leer desde el principio hasta encontrarlo (con salida anticipada) |
| **Listar todos** | Recorre todos los huecos (inevitable) | Recorre todo el fichero (inevitable) |
| **Alta** | Al final del fichero | Al final (`append`), con contador de IDs en `data/secuencia.dat` para no escanear el fichero grande |
| **Actualizar** | Sobrescritura **in situ** (barata, no toca el resto) | **Regenera** el fichero completo en *streaming* (O(1) en memoria, O(n) en disco) |
| **Eliminar** | Baja lógica (*tombstone*): se marca el hueco, no se reutiliza | Regenera el fichero completo: el registro desaparece de verdad |
| **Longitud de los textos** | Fija: se trunca o se rellena con espacios | Variable: cada alumno ocupa lo que necesite |
| **Cuándo conviene** | Se conocen límites razonables de longitud y se buscan muchas veces registros individuales por clave | Los textos no tienen un límite claro, o interesa un formato más simple aunque las modificaciones sean más caras |

**La pregunta que resume la diferencia:** ¿el fichero tiene registros de
**tamaño fijo** (se puede calcular su posición con una fórmula) o de
**tamaño variable** (hay que leerlos en orden para encontrarlos)? Esa
única decisión de diseño arrastra todas las demás: qué tan cara es cada
operación, si hace falta `Serializable`, y cómo se gestionan las bajas.

---

## 3. Cómo explorar cada rama

Cada rama hija tiene su propio `README.md` con una explicación mucho más
detallada, método a método, de su propia técnica — este documento es solo
el punto de comparación. Para estudiarlas:

```bash
git checkout pers_dis_bin              # acceso directo, registros fijos
git checkout pers_dis_bin_serializados # serializacion, acceso secuencial
```

También se puede ver el diff completo de cada una frente a esta base
directamente en GitHub, en sus respectivos pull requests
([#2](../../pull/2) y [#1](../../pull/1)) — quedan abiertos a propósito,
sin fusionar, precisamente para que sirvan de material de estudio: el
diff muestra, de un vistazo, todo lo que cambió para pasar de "ArrayList
en RAM" a cada técnica de persistencia en disco.

---

## 4. Ejercicio propuesto

Antes de leer el README de cada rama hija, intenta responder (razonando,
sin mirar el código todavía):

1. Si quisiera guardar un alumno con un nombre de 500 caracteres, ¿qué
   pasaría en cada una de las dos ramas?
2. Si tuviera un millón de alumnos y actualizara uno solo, ¿qué técnica
   sería más rápida?
3. Si tuviera un millón de alumnos y diera de alta uno nuevo, ¿cambiaría
   la respuesta?
4. ¿En cuál de las dos ramas sobra la interfaz `Serializable`, y por qué?

Después, comprueba tus respuestas leyendo el README de
[`pers_dis_bin`](../../blob/pers_dis_bin/README.md) y de
[`pers_dis_bin_serializados`](../../blob/pers_dis_bin_serializados/README.md).

---

## 5. Cómo ejecutar esta rama

```bash
./mvnw spring-boot:run
```

Al ser persistencia en memoria (`ArrayList`), los datos **no** sobreviven
a un reinicio de la aplicación: cada arranque se carga de nuevo con los 5
alumnos de ejemplo. Esa es, precisamente, la limitación que las dos ramas
hijas resuelven, cada una a su manera.
