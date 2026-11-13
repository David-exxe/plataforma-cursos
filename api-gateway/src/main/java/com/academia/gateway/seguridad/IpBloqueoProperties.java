package com.academia.gateway.seguridad;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "seguridad.ip")
public record IpBloqueoProperties(
        List<String> listaNegra,
        List<String> listaBlanca,
        List<String> cabecerasIpCliente,
        int maxPeticionesPorMinuto,
        int maxLoginsFallidos,
        int ventanaLoginsFallidosMinutos,
        int minutosBloqueo
) {
    public IpBloqueoProperties {
        listaNegra = limpiar(listaNegra);
        listaBlanca = limpiar(listaBlanca);
        cabecerasIpCliente = limpiar(cabecerasIpCliente);
    }

    private static List<String> limpiar(List<String> l) {
        return l == null ? List.of() : l.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
