package com.courtsync.pro.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
public class NotificacionListener {

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleReservaCreada(ReservaCreadaEvent event) {
        log.info(
                "[SMTP-ASYNC] Enviando correo de reserva pendiente de pago -> codigo={}, usuarioId={}, cancha='{}', inicio={}, anticipo={}, expira={}",
                event.codigoConfirmacion(),
                event.usuarioId(),
                event.canchaNombre(),
                event.fechaHoraInicio(),
                event.valorAnticipo(),
                event.fechaExpiracionPago()
        );
    }
}
