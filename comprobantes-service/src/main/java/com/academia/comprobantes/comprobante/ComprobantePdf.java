package com.academia.comprobantes.comprobante;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "comprobante_pdf")
@Getter @Setter @NoArgsConstructor
public class ComprobantePdf {
    @Id
    private Long comprobanteId;

    @Column(name = "contenido", nullable = false)
    private byte[] contenido;

    public ComprobantePdf(Long comprobanteId, byte[] contenido) {
        this.comprobanteId = comprobanteId;
        this.contenido = contenido;
    }
}
