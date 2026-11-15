package com.academia.notificaciones.bot;

import com.academia.notificaciones.comun.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Chat del bot desde la web: usa EXACTAMENTE el mismo BotService que responde por WhatsApp,
 * identificando al estudiante por el telefono que viene en su JWT. Sirve para probar el bot
 * sin depender de Meta y para que el estudiante consulte desde la plataforma.
 */
@RestController
@RequestMapping("/api/bot")
public class BotWebController {

    private final BotService bot;
    private final MensajeEntranteRepository repo;

    public BotWebController(BotService bot, MensajeEntranteRepository repo) {
        this.bot = bot;
        this.repo = repo;
    }

    public record MensajeRequest(@NotBlank @Size(max = 500) String texto) {}

    public record MensajeChat(String autor, String texto, Instant fecha) {}

    @PostMapping("/mensaje")
    public MensajeChat enviar(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody MensajeRequest r) {
        String telefono = telefono(jwt);
        String respuesta = bot.responder(telefono, r.texto());
        MensajeEntrante m = new MensajeEntrante();
        m.setWaMessageId("web-" + UUID.randomUUID());
        m.setTelefono(telefono);
        m.setTexto(r.texto());
        m.setRespuesta(respuesta);
        repo.save(m);
        return new MensajeChat("BOT", respuesta, Instant.now());
    }

    @GetMapping("/historial")
    public List<MensajeChat> historial(@AuthenticationPrincipal Jwt jwt) {
        List<MensajeEntrante> ultimos = new ArrayList<>(repo.findTop30ByTelefonoOrderByRecibidoEnDesc(telefono(jwt)));
        Collections.reverse(ultimos);
        List<MensajeChat> chat = new ArrayList<>();
        for (MensajeEntrante m : ultimos) {
            chat.add(new MensajeChat("YO", m.getTexto(), m.getRecibidoEn()));
            if (m.getRespuesta() != null) chat.add(new MensajeChat("BOT", m.getRespuesta(), m.getRecibidoEn()));
        }
        return chat;
    }

    private static String telefono(Jwt jwt) {
        String t = jwt.getClaimAsString("telefono");
        if (t == null || t.isBlank()) throw ApiException.invalido("Tu sesion no tiene telefono. Cierra sesion y vuelve a entrar");
        return t;
    }
}
