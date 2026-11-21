package com.courtsync.pro.controller;

import com.courtsync.pro.exception.GlobalExceptionHandler;
import com.courtsync.pro.exception.HorarioNoDisponibleException;
import com.courtsync.pro.service.ReservaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReservaController.class)
@Import(GlobalExceptionHandler.class)
class ReservaControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservaService reservaService;

    @Test
    @DisplayName("Debe retornar HTTP 409 Conflict con ErrorResponse cuando el horario ya está ocupado")
    void crearReserva_HorarioOcupado_Retorna409ErrorResponse() throws Exception {
        when(reservaService.crearReserva(any(), eq(1L)))
                .thenThrow(new HorarioNoDisponibleException("La Cancha 1 ya fue reservada para el horario seleccionado."));

        String requestJson = """
                {
                  "canchaId": 1,
                  "fechaHoraInicio": "2026-11-25T19:00:00Z",
                  "duracionHoras": 1
                }
                """;

        mockMvc.perform(post("/api/v1/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("CONFLICT_HORARIO_OCUPADO"))
                .andExpect(jsonPath("$.message").value("La Cancha 1 ya fue reservada para el horario seleccionado."))
                .andExpect(jsonPath("$.path").value("/api/v1/reservas"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("Debe retornar HTTP 400 Bad Request con ErrorResponse cuando falla la validación @Valid")
    void crearReserva_DtoInvalido_Retorna400ErrorResponse() throws Exception {
        String requestInvalidoJson = """
                {
                  "canchaId": 1,
                  "fechaHoraInicio": "2026-11-25T19:00:00Z",
                  "duracionHoras": 5
                }
                """;

        mockMvc.perform(post("/api/v1/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestInvalidoJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST_VALIDACION"))
                .andExpect(jsonPath("$.message").value("La duración máxima es de 2 horas"))
                .andExpect(jsonPath("$.path").value("/api/v1/reservas"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }
}
