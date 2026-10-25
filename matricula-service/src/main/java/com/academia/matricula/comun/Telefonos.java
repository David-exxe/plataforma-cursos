package com.academia.matricula.comun;

/** Normaliza telefonos al formato que usa WhatsApp Cloud API (solo digitos con codigo de pais). */
public final class Telefonos {
    private Telefonos() {}

    public static String normalizar(String telefono) {
        if (telefono == null) return null;
        String d = telefono.replaceAll("\\D", "");
        if (d.length() == 9 && d.startsWith("9")) d = "51" + d;   // celular peruano sin codigo de pais
        return d;
    }
}
