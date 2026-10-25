package com.academia.comprobantes.comprobante;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Arma el PDF del comprobante con OpenPDF. */
@Component
public class PdfComprobanteGenerator {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("America/Lima"));
    private static final BigDecimal IGV = new BigDecimal("1.18");
    private static final Color AZUL = new Color(31, 58, 147);

    private final String empresa;
    private final String ruc;
    private final String direccion;

    public PdfComprobanteGenerator(@Value("${comprobante.empresa}") String empresa,
                                   @Value("${comprobante.ruc}") String ruc,
                                   @Value("${comprobante.direccion}") String direccion) {
        this.empresa = empresa;
        this.ruc = ruc;
        this.direccion = direccion;
    }

    public byte[] generar(Comprobante c) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A5, 36, 36, 36, 36);
        try {
            PdfWriter.getInstance(doc, out);
            doc.addTitle("Comprobante " + c.getNumero());
            doc.open();

            Font titulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, AZUL);
            Font negrita = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
            Font normal = FontFactory.getFont(FontFactory.HELVETICA, 9);
            Font chico = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 7, Color.GRAY);

            // Cabecera: empresa a la izquierda, recuadro con RUC y numero a la derecha
            PdfPTable cab = new PdfPTable(new float[]{3, 2});
            cab.setWidthPercentage(100);
            PdfPCell emp = new PdfPCell();
            emp.setBorder(Rectangle.NO_BORDER);
            emp.addElement(new Paragraph(empresa, titulo));
            emp.addElement(new Paragraph(direccion, normal));
            cab.addCell(emp);
            PdfPCell num = new PdfPCell();
            num.setBorderColor(AZUL);
            num.setPadding(6);
            num.addElement(centrado("RUC " + ruc, negrita));
            num.addElement(centrado("COMPROBANTE DE PAGO", negrita));
            num.addElement(centrado(c.getNumero(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, AZUL)));
            cab.addCell(num);
            doc.add(cab);
            doc.add(new Paragraph(" "));

            // Datos del cliente
            PdfPTable datos = new PdfPTable(new float[]{1.3f, 3});
            datos.setWidthPercentage(100);
            fila(datos, "Cliente:", c.getEstudianteNombre(), negrita, normal);
            fila(datos, "Documento:", c.getDocumento() == null ? "-" : c.getDocumento(), negrita, normal);
            fila(datos, "Correo:", c.getEmail(), negrita, normal);
            fila(datos, "Fecha de emision:", FECHA.format(c.getFechaEmision()), negrita, normal);
            fila(datos, "Fecha de pago:", c.getFechaPago() == null ? "-" : FECHA.format(c.getFechaPago()), negrita, normal);
            doc.add(datos);
            doc.add(new Paragraph(" "));

            // Detalle
            PdfPTable det = new PdfPTable(new float[]{4, 1, 1.5f, 1.5f});
            det.setWidthPercentage(100);
            for (String h : new String[]{"Descripcion", "Cant.", "P. Unit.", "Importe"}) {
                PdfPCell hc = new PdfPCell(new Phrase(h, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE)));
                hc.setBackgroundColor(AZUL);
                hc.setPadding(4);
                det.addCell(hc);
            }
            det.addCell(celda("Curso: " + c.getCursoNombre(), normal, Element.ALIGN_LEFT));
            det.addCell(celda("1", normal, Element.ALIGN_CENTER));
            det.addCell(celda(dinero(c.getMonto()), normal, Element.ALIGN_RIGHT));
            det.addCell(celda(dinero(c.getMonto()), normal, Element.ALIGN_RIGHT));
            doc.add(det);

            // Totales (precio incluye IGV 18%)
            BigDecimal base = c.getMonto().divide(IGV, 2, RoundingMode.HALF_UP);
            BigDecimal igv = c.getMonto().subtract(base);
            PdfPTable tot = new PdfPTable(new float[]{5.5f, 2.5f});
            tot.setWidthPercentage(100);
            totalFila(tot, "Op. gravada", c.getMoneda() + " " + dinero(base), normal);
            totalFila(tot, "IGV (18%)", c.getMoneda() + " " + dinero(igv), normal);
            totalFila(tot, "TOTAL", c.getMoneda() + " " + dinero(c.getMonto()), negrita);
            doc.add(tot);
            doc.add(new Paragraph(" "));

            doc.add(new Paragraph("Medio de pago: Mercado Pago" + (c.getMetodoPago() != null ? " (" + c.getMetodoPago() + ")" : ""), normal));
            doc.add(new Paragraph("N. de operacion: " + (c.getMpPaymentId() == null ? "-" : c.getMpPaymentId()), normal));
            doc.add(new Paragraph("Inscripcion N. " + c.getInscripcionId(), normal));
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("Documento de control interno generado automaticamente. Para efectos tributarios "
                    + "se emite el comprobante electronico correspondiente.", chico));
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el PDF", e);
        } finally {
            doc.close();
        }
        return out.toByteArray();
    }

    private static Paragraph centrado(String t, Font f) {
        Paragraph p = new Paragraph(t, f);
        p.setAlignment(Element.ALIGN_CENTER);
        return p;
    }

    private static void fila(PdfPTable t, String k, String v, Font fk, Font fv) {
        PdfPCell a = new PdfPCell(new Phrase(k, fk));
        PdfPCell b = new PdfPCell(new Phrase(v, fv));
        a.setBorder(Rectangle.NO_BORDER);
        b.setBorder(Rectangle.NO_BORDER);
        t.addCell(a);
        t.addCell(b);
    }

    private static PdfPCell celda(String t, Font f, int alin) {
        PdfPCell c = new PdfPCell(new Phrase(t, f));
        c.setHorizontalAlignment(alin);
        c.setPadding(4);
        return c;
    }

    private static void totalFila(PdfPTable t, String k, String v, Font f) {
        PdfPCell a = new PdfPCell(new Phrase(k, f));
        a.setHorizontalAlignment(Element.ALIGN_RIGHT);
        a.setBorder(Rectangle.NO_BORDER);
        PdfPCell b = new PdfPCell(new Phrase(v, f));
        b.setHorizontalAlignment(Element.ALIGN_RIGHT);
        b.setBorder(Rectangle.NO_BORDER);
        t.addCell(a);
        t.addCell(b);
    }

    private static String dinero(BigDecimal b) {
        return b.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
