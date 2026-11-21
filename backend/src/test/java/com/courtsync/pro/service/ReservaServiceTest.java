package com.courtsync.pro.service;

import com.courtsync.pro.domain.entity.Cancha;
import com.courtsync.pro.domain.entity.EstadoReserva;
import com.courtsync.pro.domain.entity.Reserva;
import com.courtsync.pro.domain.entity.TipoCancha;
import com.courtsync.pro.dto.CrearReservaRequest;
import com.courtsync.pro.dto.ReservaResponse;
import com.courtsync.pro.exception.HorarioNoDisponibleException;
import com.courtsync.pro.exception.ReglaNegocioException;
import com.courtsync.pro.repository.CanchaRepository;
import com.courtsync.pro.repository.ReservaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservaServiceTest {

    @Mock
    private CanchaRepository canchaRepository;

    @Mock
    private ReservaRepository reservaRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ReservaServiceImpl reservaService;

    @Test
    @DisplayName("Debe crear reserva en estado PENDIENTE_PAGO calculando 50% de anticipo (RN-03, RN-09)")
    void crearReserva_Exitosa_CalculaAnticipoYEstadoPendientePago() {
        // Arrange
        Long usuarioId = 1L;
        OffsetDateTime inicioFuturo = OffsetDateTime.now(ZoneOffset.UTC).plusHours(5);
        CrearReservaRequest request = new CrearReservaRequest(1L, inicioFuturo, 2);

        Cancha cancha = Cancha.builder()
                .id(1L)
                .nombre("Cancha Pádel 1 - Panorámica")
                .tipoCancha(TipoCancha.PADEL)
                .tarifaHora(80000.0)
                .activa(true)
                .build();

        when(reservaRepository.countReservasActivasPorUsuarioEnFecha(eq(usuarioId), any(), any(), any()))
                .thenReturn(0L);
        when(canchaRepository.findByIdWithLock(1L)).thenReturn(Optional.of(cancha));
        when(reservaRepository.findReservasActivasEnRangoPorCancha(eq(1L), any(), any(), any()))
                .thenReturn(List.of());
        when(reservaRepository.save(any(Reserva.class))).thenAnswer(invocation -> {
            Reserva r = invocation.getArgument(0);
            r.setId(504L);
            return r;
        });

        // Act
        ReservaResponse response = reservaService.crearReserva(request, usuarioId);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.reservaId()).isEqualTo(504L);
        assertThat(response.estado()).isEqualTo(EstadoReserva.PENDIENTE_PAGO);
        assertThat(response.valorTotal()).isEqualTo(160000.0);
        assertThat(response.valorAnticipo()).isEqualTo(80000.0);
        assertThat(response.fechaExpiracionPago()).isAfter(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(9));
        assertThat(response.codigoConfirmacion()).endsWith("-0504");

        ArgumentCaptor<ReservaCreadaEvent> eventCaptor = ArgumentCaptor.forClass(ReservaCreadaEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().valorAnticipo()).isEqualTo(80000.0);
    }

    @Test
    @DisplayName("Debe rechazar reserva con menos de 2 horas de anticipación lanzando ReglaNegocioException (RN-02)")
    void crearReserva_AnticipacionMenorADosHoras_LanzaReglaNegocioException() {
        // Arrange
        Long usuarioId = 1L;
        OffsetDateTime inicioCercano = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(60);
        CrearReservaRequest request = new CrearReservaRequest(1L, inicioCercano, 1);

        // Act & Assert
        assertThatThrownBy(() -> reservaService.crearReserva(request, usuarioId))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("2 horas");

        verify(reservaRepository, never()).save(any());
        verify(canchaRepository, never()).findByIdWithLock(any());
    }

    @Test
    @DisplayName("Debe rechazar reserva cuando existe choque de horario lanzando HorarioNoDisponibleException sin guardar (RNF-02)")
    void crearReserva_ChoqueDeHorario_LanzaHorarioNoDisponibleExceptionYNoGuarda() {
        // Arrange
        Long usuarioId = 1L;
        OffsetDateTime inicioFuturo = OffsetDateTime.now(ZoneOffset.UTC).plusHours(4);
        CrearReservaRequest request = new CrearReservaRequest(1L, inicioFuturo, 1);

        Cancha cancha = Cancha.builder()
                .id(1L)
                .nombre("Cancha Pádel 1 - Panorámica")
                .tipoCancha(TipoCancha.PADEL)
                .tarifaHora(80000.0)
                .activa(true)
                .build();

        Reserva reservaExistente = Reserva.builder()
                .id(99L)
                .cancha(cancha)
                .estado(EstadoReserva.CONFIRMADA)
                .fechaHoraInicio(inicioFuturo)
                .fechaHoraFin(inicioFuturo.plusHours(1))
                .build();

        when(reservaRepository.countReservasActivasPorUsuarioEnFecha(eq(usuarioId), any(), any(), any()))
                .thenReturn(0L);
        when(canchaRepository.findByIdWithLock(1L)).thenReturn(Optional.of(cancha));
        when(reservaRepository.findReservasActivasEnRangoPorCancha(eq(1L), any(), any(), any()))
                .thenReturn(List.of(reservaExistente));

        // Act & Assert
        assertThatThrownBy(() -> reservaService.crearReserva(request, usuarioId))
                .isInstanceOf(HorarioNoDisponibleException.class)
                .hasMessageContaining("ya fue reservada");

        verify(reservaRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("Debe rechazar reserva cuando el jugador ya tiene 2 reservas activas en la fecha (RN-01)")
    void crearReserva_LimiteDiarioAlcanzado_LanzaReglaNegocioException() {
        // Arrange
        Long usuarioId = 1L;
        OffsetDateTime inicioFuturo = OffsetDateTime.now(ZoneOffset.UTC).plusHours(6);
        CrearReservaRequest request = new CrearReservaRequest(1L, inicioFuturo, 1);

        when(reservaRepository.countReservasActivasPorUsuarioEnFecha(eq(usuarioId), any(), any(), any()))
                .thenReturn(2L);

        // Act & Assert
        assertThatThrownBy(() -> reservaService.crearReserva(request, usuarioId))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("límite máximo de 2 reservas");

        verify(canchaRepository, never()).findByIdWithLock(any());
        verify(reservaRepository, never()).save(any());
    }
}
