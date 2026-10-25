package com.academia.matricula.inscripcion;

import com.academia.matricula.auth.Usuario;
import com.academia.matricula.auth.UsuarioRepository;
import com.academia.matricula.comun.ApiException;
import com.academia.matricula.curso.Curso;
import com.academia.matricula.curso.CursoRepository;
import com.academia.matricula.outbox.OutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Flujo de inscripcion:
 *  1. (TX) valida curso/cupos/duplicado y guarda la inscripcion PENDIENTE_PAGO.
 *  2. (sin TX) pide a pagos-service la preferencia de Mercado Pago -> link de pago.
 *     No se mantiene una transaccion abierta durante una llamada HTTP.
 *  3. (TX) guarda el link + evento inscripcion.creada en el outbox.
 *  4. (async) correo con informacion del curso y link de pago.
 */
@Service
public class InscripcionService {

    private static final Logger log = LoggerFactory.getLogger(InscripcionService.class);
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final InscripcionRepository repo;
    private final CursoRepository cursos;
    private final UsuarioRepository usuarios;
    private final PagosClient pagos;
    private final OutboxService outbox;
    private final CorreoInscripcionService correo;
    private final TransactionTemplate tx;

    public InscripcionService(InscripcionRepository repo, CursoRepository cursos, UsuarioRepository usuarios,
                              PagosClient pagos, OutboxService outbox, CorreoInscripcionService correo,
                              PlatformTransactionManager tm) {
        this.repo = repo;
        this.cursos = cursos;
        this.usuarios = usuarios;
        this.pagos = pagos;
        this.outbox = outbox;
        this.correo = correo;
        this.tx = new TransactionTemplate(tm);
    }

    public InscripcionDto inscribir(Long usuarioId, Long cursoId) {
        Long id = tx.execute(s -> crear(usuarioId, cursoId));
        PagosClient.PreferenciaResponse pref = pedirLinkPago(id);

        Inscripcion fin = tx.execute(s -> {
            Inscripcion i = repo.buscarCompleta(id).orElseThrow();
            if (pref != null) {
                i.setPagoId(pref.pagoId());
                i.setLinkPago(pref.linkPago());
            }
            outbox.registrar("inscripcion.creada", Eventos.inscripcionCreada(i));
            return i;
        });
        correo.enviar(id);
        return InscripcionDto.de(fin);
    }

    private Long crear(Long usuarioId, Long cursoId) {
        Usuario u = usuarios.findById(usuarioId).orElseThrow(() -> ApiException.noEncontrado("Usuario no encontrado"));
        // Bloqueo pesimista de la fila del curso: dos inscripciones simultaneas no pueden pasarse del cupo
        Curso c = cursos.findById(cursoId).orElseThrow(() -> ApiException.noEncontrado("Curso no encontrado"));
        cursos.bloquear(cursoId);
        if (!c.isActivo()) throw ApiException.invalido("El curso no esta disponible");
        if (c.getFechaInicio().isBefore(LocalDate.now(LIMA))) throw ApiException.invalido("El curso ya inicio");
        if (repo.contarOcupados(cursoId) >= c.getCupos()) throw ApiException.conflicto("No hay cupos disponibles");

        boolean yaInscrito = repo.delUsuario(usuarioId).stream().anyMatch(i -> i.getCurso().getId().equals(cursoId)
                && (i.getEstado() == EstadoInscripcion.PENDIENTE_PAGO || i.getEstado() == EstadoInscripcion.PAGADA));
        if (yaInscrito) throw ApiException.conflicto("Ya tienes una inscripcion activa en este curso");

        Inscripcion i = new Inscripcion();
        i.setUsuario(u);
        i.setCurso(c);
        i.setEstado(EstadoInscripcion.PENDIENTE_PAGO);
        i.setMonto(c.getPrecio());
        i.setMoneda(c.getMoneda());
        return repo.save(i).getId();
    }

    private PagosClient.PreferenciaResponse pedirLinkPago(Long inscripcionId) {
        Inscripcion i = tx.execute(s -> {
            Inscripcion x = repo.buscarCompleta(inscripcionId).orElseThrow();
            x.getUsuario().getEmail(); // inicializa relaciones dentro de la TX
            return x;
        });
        try {
            Usuario u = i.getUsuario();
            Curso c = i.getCurso();
            return pagos.crearPreferencia(new PagosClient.CrearPreferenciaRequest(i.getId(), u.getId(), u.getNombre(),
                    u.getEmail(), u.getTelefono(), u.getDocumento(), c.getId(), c.getNombre(), i.getMonto(), i.getMoneda()));
        } catch (Exception e) {
            log.warn("pagos-service no respondio para inscripcion {}: {}. El link se generara despues.", inscripcionId, e.getMessage());
            return null;
        }
    }

    public List<InscripcionDto> misInscripciones(Long usuarioId) {
        return tx.execute(s -> repo.delUsuario(usuarioId).stream().map(InscripcionDto::de).toList());
    }

    public InscripcionDto detalle(Long usuarioId, Long id, boolean esAdmin) {
        return tx.execute(s -> InscripcionDto.de(propia(usuarioId, id, esAdmin)));
    }

    /** Reintenta obtener el link si pagos-service estuvo caido al momento de inscribirse. */
    public InscripcionDto linkPago(Long usuarioId, Long id) {
        Inscripcion i = tx.execute(s -> propia(usuarioId, id, false));
        if (i.getEstado() != EstadoInscripcion.PENDIENTE_PAGO) throw ApiException.invalido("La inscripcion no esta pendiente de pago");
        PagosClient.PreferenciaResponse pref = pedirLinkPago(id);
        if (pref == null) throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "El servicio de pagos no esta disponible, intenta en unos minutos");
        return tx.execute(s -> {
            Inscripcion x = repo.buscarCompleta(id).orElseThrow();
            x.setPagoId(pref.pagoId());
            x.setLinkPago(pref.linkPago());
            return InscripcionDto.de(x);
        });
    }

    public InscripcionDto cancelar(Long usuarioId, Long id) {
        return tx.execute(s -> {
            Inscripcion i = propia(usuarioId, id, false);
            if (i.getEstado() != EstadoInscripcion.PENDIENTE_PAGO) throw ApiException.invalido("Solo se puede cancelar una inscripcion pendiente de pago");
            i.setEstado(EstadoInscripcion.CANCELADA);
            return InscripcionDto.de(i);
        });
    }

    public List<InscripcionDto> admin(EstadoInscripcion estado) {
        return tx.execute(s -> (estado == null ? repo.todasAdmin() : repo.porEstadoAdmin(estado))
                .stream().map(InscripcionDto::admin).toList());
    }

    private Inscripcion propia(Long usuarioId, Long id, boolean esAdmin) {
        Inscripcion i = repo.buscarCompleta(id).orElseThrow(() -> ApiException.noEncontrado("Inscripcion no encontrada"));
        if (!esAdmin && !i.getUsuario().getId().equals(usuarioId)) throw ApiException.noEncontrado("Inscripcion no encontrada");
        return i;
    }
}
