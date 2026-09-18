package org.example.alumno.model;

/**
 * Entidad Alumno.
 *
 * A proposito, esta clase NO implementa {@link java.io.Serializable}.
 * Hay dos tecnicas distintas para guardar objetos en ficheros binarios:
 *
 *  1) SERIALIZACION (ObjectOutputStream + Serializable): Java convierte el
 *     objeto entero en bytes automaticamente. Es comoda, pero cada objeto
 *     ocupa un numero de bytes VARIABLE (depende de la longitud de sus
 *     Strings), asi que solo permite ACCESO SECUENCIAL: para encontrar un
 *     registro hay que leer desde el principio hasta dar con el.
 *
 *  2) ACCESO DIRECTO con registros de longitud FIJA (RandomAccessFile):
 *     es la tecnica que usa AlumnoRepositoryBinarioImpl. Cada campo se
 *     escribe "a mano" (writeLong, writeChars, writeDouble) ocupando
 *     siempre el mismo numero de bytes, de forma que el registro del
 *     alumno con ID n SIEMPRE empieza en el byte (n-1) * TAMANO_REGISTRO.
 *     Esto permite ir DIRECTOS a un registro con raf.seek(posicion), sin
 *     leer los anteriores, pero exige fijar de antemano un tamano maximo
 *     para nombre/apellido/correo (se trunca o se rellena con espacios).
 *
 * Por eso Alumno se mantiene como una clase Java "normal": es el propio
 * repositorio quien decide como convertir sus campos a bytes de longitud
 * fija y viceversa.
 */
public class Alumno {

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
