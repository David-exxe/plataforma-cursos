package com.academia.gateway.seguridad;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "seguridad.ip")
public record IpBloqueoProperties(
        List<String> listaNegra,
        List<String> listaBlanca,
        boolean confiarXForwardedFor,
        int maxPeticionesPorMinuto,
        int maxLoginsFallidos,
        int ventanaLoginsFallidosMinutos,
        int minutosBloqueo
) {
    public IpBloqueoProperties {
        listaNegra = listaNegra == null ? List.of() : listaNegra.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        listaBlanca = listaBlanca == null ? List.of() : listaBlanca.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
