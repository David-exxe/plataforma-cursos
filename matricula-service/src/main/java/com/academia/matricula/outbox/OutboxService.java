package com.academia.matricula.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Registra eventos en la tabla outbox. DEBE llamarse dentro de la transaccion del cambio de negocio
 * (Propagation.MANDATORY lo obliga): o se guardan ambos, o ninguno.
 *
 * Sobre del evento (igual en todos los servicios):
 * { "eventoId": uuid, "tipo": "inscripcion.creada", "version": 1,
 *   "origen": "matricula-service", "ocurridoEn": iso-8601, "datos": { ... } }
 */
@Service
public class OutboxService {

    private final OutboxRepository repo;
    private final ObjectMapper mapper;

    public OutboxService(OutboxRepository repo, ObjectMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(String tipo, Map<String, Object> datos) {
        UUID id = UUID.randomUUID();
        Map<String, Object> sobre = new LinkedHashMap<>();
        sobre.put("eventoId", id.toString());
        sobre.put("tipo", tipo);
        sobre.put("version", 1);
        sobre.put("origen", "matricula-service");
        sobre.put("ocurridoEn", Instant.now().toString());
        sobre.put("datos", datos);
        OutboxEvento e = new OutboxEvento();
        e.setEventoId(id);
        e.setTipo(tipo);
        try {
            e.setPayload(mapper.writeValueAsString(sobre));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
        repo.save(e);
    }
}
