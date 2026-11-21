package com.courtsync.pro.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(
        name = "reservas",
        indexes = {
                @Index(name = "idx_reservas_cancha_inicio_estado", columnList = "cancha_id, fecha_hora_inicio, estado"),
                @Index(name = "idx_reservas_usuario_inicio", columnList = "usuario_id, fecha_hora_inicio")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_confirmacion", nullable = false, unique = true, length = 30)
    private String codigoConfirmacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cancha_id", nullable = false)
    private Cancha cancha;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "fecha_hora_inicio", nullable = false)
    private OffsetDateTime fechaHoraInicio;

    @Column(name = "fecha_hora_fin", nullable = false)
    private OffsetDateTime fechaHoraFin;

    @Column(name = "duracion_horas", nullable = false)
    private Integer duracionHoras;

    @Column(name = "valor_total", nullable = false)
    private Double valorTotal;

    @Column(name = "valor_anticipo", nullable = false)
    private Double valorAnticipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoReserva estado;

    @Column(name = "fecha_creacion", nullable = false)
    private OffsetDateTime fechaCreacion;

    @Column(name = "fecha_expiracion_pago", nullable = false)
    private OffsetDateTime fechaExpiracionPago;
}
