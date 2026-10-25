package com.academia.pagos.pago;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "webhook_eventos")
@Getter @Setter @NoArgsConstructor
public class WebhookEvento {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String requestId;
    private String tipo;
    private String dataId;
    private boolean firmaValida;
    private boolean procesado;
    private String error;
    private String payload;
    private Instant recibidoEn = Instant.now();
}
