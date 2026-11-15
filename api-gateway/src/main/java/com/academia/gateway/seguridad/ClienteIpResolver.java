package com.academia.gateway.seguridad;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.net.InetSocketAddress;

/**
 * Obtiene la IP real del cliente.
 *  - Local / Docker: la IP de la conexion TCP.
 *  - Detras de un proxy (Render usa Cloudflare): la primera cabecera configurada que venga con valor,
 *    por ejemplo CF-Connecting-IP (la pone Cloudflare y el cliente no la puede falsificar).
 *    X-Forwarded-For se deja al final porque el cliente puede inventar su primer valor.
 * Para comprobar que IP ve el gateway: GET /api/mi-ip (tambien en la pagina "Estado del sistema").
 */
@Component
public class ClienteIpResolver {

    private final IpBloqueoProperties props;

    public ClienteIpResolver(IpBloqueoProperties props) {
        this.props = props;
    }

    public String resolver(ServerWebExchange exchange) {
        for (String cabecera : props.cabecerasIpCliente()) {
            String valor = exchange.getRequest().getHeaders().getFirst(cabecera);
            if (valor != null && !valor.isBlank()) return valor.split(",")[0].trim();
        }
        InetSocketAddress remoto = exchange.getRequest().getRemoteAddress();
        return (remoto != null && remoto.getAddress() != null) ? remoto.getAddress().getHostAddress() : "desconocida";
    }
}
