# CRUD de Alumno — Acceso a Datos (DAM)

Proyecto pedagógico de la asignatura **Acceso a Datos**, del ciclo de
Desarrollo de Aplicaciones Multiplataforma. Es un CRUD de `Alumno` con
interfaz de consola (Spring Boot), usado para estudiar, paso a paso,
distintas formas de persistir datos: desde memoria RAM hasta dos técnicas
distintas de ficheros binarios.

**El código no vive en una sola rama.** Cada rama de este repositorio es
una etapa o una técnica distinta, pensada para que se puedan **comparar
entre sí** — no para fusionarse unas con otras. Esta página es el mapa
para orientarse entre ellas.

---

## Mapa de ramas

| Rama | Qué contiene | Enlaces |
|---|---|---|
| [`pers_ram`](../../tree/pers_ram) | CRUD base: persistencia en memoria (`ArrayList`) | |
| [`pers_disc`](../../tree/pers_disc) | Misma base que `pers_ram`; punto de partida común de las dos técnicas de persistencia en disco, con un README que las compara | [README](../../blob/pers_disc/README.md) |
| [`pers_dis_bin`](../../tree/pers_dis_bin) | Persistencia en disco por **acceso directo** (`RandomAccessFile`, registros de longitud fija) | [README](../../blob/pers_dis_bin/README.md) · [PR #2](../../pull/2) |
| [`pers_dis_bin_serializados`](../../tree/pers_dis_bin_serializados) | Persistencia en disco por **serialización de objetos** (acceso secuencial) | [README](../../blob/pers_dis_bin_serializados/README.md) · [PR #1](../../pull/1) |

```
pers_ram
   |
   v
pers_disc  (base comun)
   |-------------------------------|
   v                                v
pers_dis_bin                pers_dis_bin_serializados
(acceso directo,            (serializacion,
 registros fijos)            acceso secuencial)
```

`pers_dis_bin` y `pers_dis_bin_serializados` son ramas **hermanas**: las
dos parten exactamente del mismo punto (`pers_disc`) y resuelven el mismo
problema — persistir en disco en vez de en RAM — con decisiones de diseño
distintas. Los pull requests #1 y #2 se dejan **abiertos a propósito, sin
fusionar**: sirven como material de estudio, para ver de un vistazo (en la
pestaña "Files changed" de GitHub) todo lo que cambió en cada una respecto
a la base común.

---

## Cómo clonar y comparar las ramas

```bash
git clone https://github.com/sulbaranjc/ALumno_ram.git
cd ALumno_ram

git checkout pers_ram                     # version original en memoria
./mvnw spring-boot:run

git checkout pers_dis_bin                 # acceso directo
./mvnw spring-boot:run

git checkout pers_dis_bin_serializados    # serializacion
./mvnw spring-boot:run
```

Cada rama es un proyecto completo e independiente: se puede hacer
`checkout` y ejecutar cualquiera de ellas sin que las demás interfieran.
Los datos de cada técnica de persistencia se guardan en una carpeta
`data/` (excluida de git) dentro del directorio desde el que se ejecute la
aplicación.

Para leer la explicación técnica detallada de cada una, entra al
`README.md` de esa rama (enlazados en la tabla de arriba) — ahí se explica
método a método cómo funciona, con ejercicios propuestos para estudiar el
código.

---

## Por dónde empezar

1. Lee este documento (ya lo has hecho).
2. Explora `pers_ram` para entender el CRUD base: capas
   (`ConsoleMenu` → `AlumnoService` → `AlumnoRepository`), y por qué
   `notaTotal` está encapsulada dentro de `Alumno`.
3. Lee el `README.md` de `pers_disc`: plantea las preguntas de reflexión
   antes de ver el código de las dos técnicas de persistencia en disco.
4. Estudia `pers_dis_bin` y `pers_dis_bin_serializados` en cualquier
   orden, ejecutando cada una y siguiendo sus ejercicios propuestos.
5. Compara: ¿qué operación es barata en una y cara en la otra? ¿Por qué?
