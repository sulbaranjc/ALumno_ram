package org.example.alumno.console;

import org.example.alumno.model.Alumno;
import org.example.alumno.service.AlumnoService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Scanner;

@Component
public class ConsoleMenu implements CommandLineRunner {

    private final AlumnoService alumnoService;
    private final Scanner scanner = new Scanner(System.in);

    public ConsoleMenu(AlumnoService alumnoService) {
        this.alumnoService = alumnoService;
    }

    @Override
    public void run(String... args) {
        int opcion;
        do {
            mostrarMenu();
            opcion = leerEntero("Seleccione una opcion: ");
            switch (opcion) {
                case 1 -> listarAlumnos();
                case 2 -> buscarAlumno();
                case 3 -> crearAlumno();
                case 4 -> actualizarAlumno();
                case 5 -> eliminarAlumno();
                case 6 -> resetearDatos();
                case 0 -> System.out.println("Saliendo... Hasta luego.");
                default -> System.out.println("Opcion invalida. Intente nuevamente.");
            }
        } while (opcion != 0);
    }

    private void mostrarMenu() {
        System.out.println("\n===== CRUD ALUMNOS (fichero de objetos serializados) =====");
        System.out.println("1. Listar alumnos");
        System.out.println("2. Buscar alumno por ID");
        System.out.println("3. Crear alumno");
        System.out.println("4. Actualizar alumno");
        System.out.println("5. Eliminar alumno");
        System.out.println("6. Resetear datos (cargar datos de ejemplo)");
        System.out.println("0. Salir");
    }

    private void listarAlumnos() {
        List<Alumno> alumnos = alumnoService.listarAlumnos();
        if (alumnos.isEmpty()) {
            System.out.println("No hay alumnos registrados.");
            return;
        }
        System.out.println("\n-- Listado de alumnos --");
        alumnos.forEach(System.out::println);
    }

    private void buscarAlumno() {
        Long id = leerLong("Ingrese el ID del alumno: ");
        alumnoService.buscarPorId(id)
                .ifPresentOrElse(
                        System.out::println,
                        () -> System.out.println("No se encontro un alumno con ID " + id));
    }

    private void crearAlumno() {
        System.out.println("\n-- Crear alumno --");
        String nombre = leerTexto("Nombre: ");
        String apellido = leerTexto("Apellido: ");
        String correo = leerTexto("Correo: ");
        double nota1 = leerNota("Nota 1: ");
        double nota2 = leerNota("Nota 2: ");
        double nota3 = leerNota("Nota 3: ");

        Alumno alumno = alumnoService.crearAlumno(nombre, apellido, correo, nota1, nota2, nota3);
        System.out.println("Alumno creado exitosamente:");
        System.out.println(alumno);
    }

    private void actualizarAlumno() {
        Long id = leerLong("Ingrese el ID del alumno a actualizar: ");
        if (alumnoService.buscarPorId(id).isEmpty()) {
            System.out.println("No se encontro un alumno con ID " + id);
            return;
        }

        System.out.println("-- Actualizar alumno --");
        String nombre = leerTexto("Nombre: ");
        String apellido = leerTexto("Apellido: ");
        String correo = leerTexto("Correo: ");
        double nota1 = leerNota("Nota 1: ");
        double nota2 = leerNota("Nota 2: ");
        double nota3 = leerNota("Nota 3: ");

        alumnoService.actualizarAlumno(id, nombre, apellido, correo, nota1, nota2, nota3)
                .ifPresent(alumno -> {
                    System.out.println("Alumno actualizado exitosamente:");
                    System.out.println(alumno);
                });
    }

    private void eliminarAlumno() {
        Long id = leerLong("Ingrese el ID del alumno a eliminar: ");
        boolean eliminado = alumnoService.eliminarAlumno(id);
        System.out.println(eliminado
                ? "Alumno eliminado exitosamente."
                : "No se encontro un alumno con ID " + id);
    }

    private void resetearDatos() {
        String confirmacion = leerTexto("Esto borrara todos los alumnos actuales y cargara los datos de ejemplo. ¿Continuar? (s/n): ");
        if (!confirmacion.equalsIgnoreCase("s")) {
            System.out.println("Operacion cancelada.");
            return;
        }
        alumnoService.resetearDatos();
        System.out.println("Datos reseteados. Se cargaron los alumnos de ejemplo:");
        listarAlumnos();
    }

    private String leerTexto(String mensaje) {
        System.out.print(mensaje);
        return scanner.nextLine().trim();
    }

    private int leerEntero(String mensaje) {
        while (true) {
            System.out.print(mensaje);
            String entrada = scanner.nextLine().trim();
            try {
                return Integer.parseInt(entrada);
            } catch (NumberFormatException e) {
                System.out.println("Debe ingresar un numero entero valido.");
            }
        }
    }

    private Long leerLong(String mensaje) {
        while (true) {
            System.out.print(mensaje);
            String entrada = scanner.nextLine().trim();
            try {
                return Long.parseLong(entrada);
            } catch (NumberFormatException e) {
                System.out.println("Debe ingresar un ID numerico valido.");
            }
        }
    }

    private double leerNota(String mensaje) {
        while (true) {
            System.out.print(mensaje);
            String entrada = scanner.nextLine().trim();
            try {
                double nota = Double.parseDouble(entrada);
                if (nota < 0 || nota > 20) {
                    System.out.println("La nota debe estar entre 0 y 20.");
                    continue;
                }
                return nota;
            } catch (NumberFormatException e) {
                System.out.println("Debe ingresar un valor numerico valido.");
            }
        }
    }
}
