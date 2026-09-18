package org.example.alumno.model;

import java.io.Serializable;

/**
 * Entidad Alumno.
 *
 * Implementa {@link Serializable} porque la persistencia de esta rama
 * (vease AlumnoRepositorySerializadoImpl) usa SERIALIZACION DE OBJETOS:
 * Java convierte el objeto entero en una secuencia de bytes de forma
 * automatica con {@link java.io.ObjectOutputStream}, y lo reconstruye
 * despues con {@link java.io.ObjectInputStream}. Serializable es una
 * interfaz "marcadora" (no declara metodos): solo le dice a la JVM
 * "autorizo a que esta clase se convierta en bytes".
 *
 * Con esta tecnica cada Alumno ocupa un numero de bytes DISTINTO (segun
 * la longitud de su nombre, apellido y correo), asi que no hay forma de
 * calcular de antemano en que byte del fichero empieza cada uno. Por eso
 * las busquedas se hacen leyendo el fichero de forma SECUENCIAL, alumno
 * a alumno, en vez de saltar directamente a una posicion calculada (esa
 * es la tecnica de la rama hermana pers_dis_bin, que usa RandomAccessFile
 * con registros de longitud fija).
 */
public class Alumno implements Serializable {

    /**
     * Identificador de version de la clase para la serializacion.
     * Se fija a mano (en vez de dejar que el IDE lo autogenere) para
     * controlar nosotros cuando cambia el "contrato binario" de la clase:
     * si en el futuro anadimos o quitamos atributos y esta version no
     * coincide con la del fichero .dat ya guardado, Java avisara con un
     * InvalidClassException en vez de fallar de forma silenciosa.
     */
    private static final long serialVersionUID = 1L;

    private Long id;
    private String nombre;
    private String apellido;
    private String correo;
    private double nota1;
    private double nota2;
    private double nota3;
    private double notaTotal;

    public Alumno() {
    }

    public Alumno(Long id, String nombre, String apellido, String correo,
                  double nota1, double nota2, double nota3) {
        this.id = id;
        this.nombre = nombre;
        this.apellido = apellido;
        this.correo = correo;
        this.nota1 = nota1;
        this.nota2 = nota2;
        this.nota3 = nota3;
        recalcularNotaTotal();
    }

    private void recalcularNotaTotal() {
        this.notaTotal = (nota1 + nota2 + nota3) / 3.0;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getApellido() {
        return apellido;
    }

    public void setApellido(String apellido) {
        this.apellido = apellido;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public double getNota1() {
        return nota1;
    }

    public void setNota1(double nota1) {
        this.nota1 = nota1;
        recalcularNotaTotal();
    }

    public double getNota2() {
        return nota2;
    }

    public void setNota2(double nota2) {
        this.nota2 = nota2;
        recalcularNotaTotal();
    }

    public double getNota3() {
        return nota3;
    }

    public void setNota3(double nota3) {
        this.nota3 = nota3;
        recalcularNotaTotal();
    }

    public double getNotaTotal() {
        return notaTotal;
    }

    @Override
    public String toString() {
        return String.format(
                "ID: %d | %s %s | Correo: %s | Nota1: %.2f | Nota2: %.2f | Nota3: %.2f | Nota Total: %.2f",
                id, nombre, apellido, correo, nota1, nota2, nota3, notaTotal);
    }
}
