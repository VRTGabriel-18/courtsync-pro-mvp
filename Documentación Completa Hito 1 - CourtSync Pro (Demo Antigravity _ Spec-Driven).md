# **ESPECIFICACIÓN TÉCNICA CONSOLIDADA – HITO 1: COURTSYNC PRO**

**Stack Objetivo:** Backend en Java 17/21 \+ Spring Boot 3.x (Spring Web, Spring Data JPA, Spring Security JWT, Bean Validation) \| Base de Datos PostgreSQL \| Frontend SPA Reac

| t |
| :---- |
| **Propósito del Documento:** Fuente única de verdad (Spec-Driven Development) que integra Contexto, Historias de Usuario con Reglas de Negocio y Gherkin, Matriz de Atributos de Calidad (RNF), Proceso TO-BE, Especificación de Interfaz (Figma), Contrato OpenAPI 3.0.3 y Auditoría de Trazabilidad. |

# **1\. CONTEXTO DEL SISTEMA, ACTORES Y ALCANCE (ACTIVIDAD 0\)**

| Elemento | Especificación Técnica |
| :---- | :---- |
| **Nombre del Sistema** | **CourtSync Pro** – Plataforma Web de Gestión y Reserva de Canchas Deportivas |
| **Problema de Negocio** | Las reservas de canchas se gestionan manualmente por WhatsApp y hojas de Excel en recepción, provocando dobles asignaciones del mismo turno en horas pico (18:00 a 22:00), pérdida de ingresos por cancelaciones tardías sin penalidad y demoras de hasta 20 minutos para confirmar disponibilidad. |
| **Actores y Roles del Sistema** | 1\. **JUGADOR (Rol JWT: `ROLE_JUGADOR`):** Consulta disponibilidad en tiempo real, crea reservas con bloqueo temporal, confirma pagos de anticipo, consulta su historial y cancela turnos.2. **ADMIN\_SEDE (Rol JWT: `ROLE_ADMIN_SEDE`):** Administra canchas, define tarifas por franja horaria, registra bloqueos operativos por mantenimiento/torneos y consulta ocupación.3. **Sistemas Externos:** Pasarela de Pagos (Webhook de confirmación) y Servidor SMTP (envío asíncrono de comprobantes). |
| **Alcance del MVP (3 Módulos Críticos)** | 1\. **Módulo de Disponibilidad y Reservas:** Consulta optimizada de franjas horarias y creación transaccional de reservas con control de concurrencia y bloqueo temporal de 10 minutos.2. **Módulo de Pagos, Anticipos y Cancelaciones:** Confirmación de pago y gestión de cancelaciones con cálculo automático de saldo a favor o penalidad según horas de anticipación.3. **Módulo Administrativo de Canchas y Bloqueos:** Programación de bloqueos operativos verificando ausencia de conflictos con reservas activas. |

# **2\. ESPECIFICACIÓN FUNCIONAL, REGLAS DE NEGOCIO Y GHERKIN (TALLER 1\)**

## **Catálogo Consolidado de Reglas de Negocio (`RN`)**

* **`RN-01` (Límite diario por jugador):** Un usuario con rol `JUGADOR` puede tener máximo **2 reservas activas** (en estado `PENDIENTE_PAGO` o `CONFIRMADA`) para una misma fecha calendario.  
* **`RN-02` (Anticipación mínima de reserva):** Toda reserva debe crearse con mínimo **2 horas de anticipación** respecto a la `fechaHoraInicio` del turno.  
* **`RN-03` (Bloqueo temporal de pago):** Toda reserva nueva nace en estado `PENDIENTE_PAGO` y bloquea el turno por exactamente **10 minutos** (`expiraEn` \= `fechaCreacion + 10 min`). Si no se confirma el pago en ese lapso, el turno vuelve a quedar libre.  
* **`RN-04` (Cancelación con reembolso total):** Si un jugador cancela una reserva con **6 o más horas** de anticipación respecto a `fechaHoraInicio`, el estado pasa a `CANCELADA`, se libera la cancha y se acredita el **100% del anticipo** a su saldo a favor (`billeteraSaldo`).  
* **`RN-05` (Cancelación tardía con penalidad):** Si un jugador cancela una reserva con **menos de 6 horas** de anticipación, el estado pasa a `CANCELADA_CON_PENALIDAD`, se libera la cancha y se retiene el **100% del anticipo** (saldo acreditado \= `0.0`).  
* **`RN-06` (Inmutabilidad de turnos iniciados o pasados):** No se permite cancelar una reserva cuya `fechaHoraInicio` ya haya pasado o cuyo estado sea `CANCELADA`, `CANCELADA_CON_PENALIDAD` o `FINALIZADA`.  
* **`RN-07` (Autorización de bloqueos):** Solo usuarios autenticados con rol `ADMIN_SEDE` pueden registrar bloqueos operativos en una cancha.  
* **`RN-08` (Integridad de bloqueos operativos):** No se puede registrar un bloqueo de mantenimiento/torneo en una cancha si existe al menos una reserva en estado `PENDIENTE_PAGO` o `CONFIRMADA` que se traslape con el rango `[fechaHoraInicio, fechaHoraFin]`.  
* **`RN-09` (Cálculo de tarifa total):** El valor total de la reserva (`valorTotal`) equivale a `cancha.tarifaHora * duracionHoras`, y el anticipo requerido (`valorAnticipo`) equivale al **50%** de `valorTotal`.

## **`HU-01`: Consulta de Disponibilidad de Canchas por Fecha y Tipo**

* **Enunciado:** **Como** jugador o visitante, **quiero** consultar la grilla de horarios libres y ocupados de las canchas para una fecha específica **para** identificar qué turnos puedo reservar.  
* **Escenarios Gherkin:**  
  * **Escenario 1: Consulta exitosa con resultados (`200 OK`)**  
    * **DADO** que existen canchas activas de tipo `PADEL` y la fecha consultada es `2026-11-20` (fecha futura o actual),  
    * **CUANDO** se envía `GET /api/v1/canchas/disponibilidad?fecha=2026-11-20&tipoCancha=PADEL`,  
    * **ENTONCES** el backend retorna HTTP `200 OK` con el listado de canchas y sus bloques horarios indicando estado `LIBRE`, `OCUPADO` o `MANTENIMIENTO` y la tarifa por hora.  
  * **Escenario 2: Fecha en el pasado o formato inválido (`400 Bad Request`)**  
    * **DADO** que el cliente envía una fecha anterior a hoy o con formato inválido (ej. `fecha=15-11-2026`),  
    * **CUANDO** invoca `GET /api/v1/canchas/disponibilidad`,  
    * **ENTONCES** el controlador rechaza la petición con HTTP `400 Bad Request` y un objeto `ErrorResponse` estandarizado.

## **`HU-02`: Creación de Reserva con Control de Concurrencia y Bloqueo Temporal**

* **Enunciado:** **Como** jugador autenticado, **quiero** reservar una cancha en una fecha, hora y duración (1 o 2 horas) disponibles **para** asegurar mi turno de juego.  
* **Reglas asociadas:** `RN-01`, `RN-02`, `RN-03`, `RN-09`.  
* **Escenarios Gherkin:**  
  * **Escenario 1: Reserva exitosa (*Happy Path* – `201 Created`)**  
    * **DADO** que el jugador está autenticado con `ROLE_JUGADOR`, tiene menos de 2 reservas activas para el `2026-11-20`, la Cancha `1` está libre de `19:00` a `20:00` y faltan más de 2 horas para el inicio,  
    * **CUANDO** envía `POST /api/v1/reservas` con `{ "canchaId": 1, "fechaHoraInicio": "2026-11-20T19:00:00Z", "duracionHoras": 1 }`,  
    * **ENTONCES** el servicio crea la reserva en estado `PENDIENTE_PAGO`, calcula `valorTotal` y `valorAnticipo` (50%), asigna `expiraEnMinutos = 10`, genera `codigoConfirmacion` único (ej. `RES-2026-0001`) y retorna HTTP `201 Created`.  
  * **Escenario 2: Choque de concurrencia / Horario ocupado (`409 Conflict`)**  
    * **DADO** que la Cancha `1` ya tiene una reserva activa (`CONFIRMADA` o `PENDIENTE_PAGO` vigente) o un bloqueo operativo que se traslapa con `2026-11-20T19:00:00Z`,  
    * **CUANDO** el jugador envía `POST /api/v1/reservas` para ese mismo horario,  
    * **ENTONCES** el servicio hace rollback de la transacción, lanza `HorarioNoDisponibleException` y retorna HTTP `409 Conflict` con código de error `CONFLICT_HORARIO_OCUPADO`.  
  * **Escenario 3: Violación de anticipación mínima o límite diario (`422 Unprocessable Entity`)**  
    * **DADO** que el jugador intenta reservar con menos de 2 horas de anticipación (`RN-02`) O ya posee 2 reservas activas ese mismo día (`RN-01`),  
    * **CUANDO** envía `POST /api/v1/reservas`,  
    * **ENTONCES** el servicio lanza `ReglaNegocioException` y retorna HTTP `422 Unprocessable Entity` detallando la regla infringida.

## **`HU-03`: Confirmación de Pago de Anticipo de Reserva**

* **Enunciado:** **Como** jugador autenticado, **quiero** registrar la confirmación del pago del anticipo dentro de los 10 minutos de bloqueo **para** dejar mi reserva en firme.  
* **Reglas asociadas:** `RN-03`.  
* **Escenarios Gherkin:**  
  * **Escenario 1: Confirmación dentro del tiempo límite (`200 OK`)**  
    * **DADO** que la reserva `504` pertenece al jugador autenticado, está en estado `PENDIENTE_PAGO` y su `fechaExpiracion` es posterior a la hora actual del servidor,  
    * **CUANDO** envía `POST /api/v1/reservas/504/confirmar-pago` con `{ "metodoPago": "PSE", "referenciaTransaccion": "TX-998877" }`,  
    * **ENTONCES** el estado cambia a `CONFIRMADA`, se registra la referencia de pago, se dispara el evento asíncrono de notificación por correo y retorna HTTP `200 OK`.  
  * **Escenario 2: Reserva expirada por superar los 10 minutos (`409 Conflict`)**  
    * **DADO** que han transcurrido más de 10 minutos desde la creación de la reserva `504`,  
    * **CUANDO** el jugador intenta confirmar el pago,  
    * **ENTONCES** el servicio marca la reserva como `EXPIRADA` y retorna HTTP `409 Conflict` con error `CONFLICT_RESERVA_EXPIRADA`.

## **`HU-04`: Cancelación de Reserva con Política de Anticipación**

* **Enunciado:** **Como** jugador autenticado, **quiero** cancelar una reserva activa **para** liberar el turno y recuperar el saldo de mi anticipo si cumplo la anticipación mínima.  
* **Reglas asociadas:** `RN-04`, `RN-05`, `RN-06`.  
* **Escenarios Gherkin:**  
  * **Escenario 1: Cancelación con \>= 6 horas de anticipación (`200 OK` – Reembolso 100%)**  
    * **DADO** que la reserva `504` pertenece al jugador autenticado, está `CONFIRMADA` y faltan 8 horas para `fechaHoraInicio`,  
    * **CUANDO** envía `PATCH /api/v1/reservas/504/cancelacion` con `{ "motivo": "Cambio de horario laboral" }`,  
    * **ENTONCES** el estado cambia a `CANCELADA`, `penalidadAplicada = false`, `saldoAcreditadoBilletera = valorAnticipo` y retorna HTTP `200 OK`.  
  * **Escenario 2: Cancelación con \< 6 horas de anticipación (`200 OK` – Penalidad 100%)**  
    * **DADO** que la reserva `504` está `CONFIRMADA` y faltan 2 horas para `fechaHoraInicio`,  
    * **CUANDO** envía `PATCH /api/v1/reservas/504/cancelacion` con `{ "motivo": "Lesión deportiva" }`,  
    * **ENTONCES** el estado cambia a `CANCELADA_CON_PENALIDAD`, `penalidadAplicada = true`, `saldoAcreditadoBilletera = 0.0` y retorna HTTP `200 OK`.  
  * **Escenario 3: Intento de cancelar turno ya iniciado o finalizado (`422 Unprocessable Entity`)**  
    * **DADO** que la `fechaHoraInicio` de la reserva ya transcurrió o la reserva ya estaba cancelada (`RN-06`),  
    * **CUANDO** envía `PATCH /api/v1/reservas/504/cancelacion`,  
    * **ENTONCES** el sistema retorna HTTP `422 Unprocessable Entity` con código `UNPROCESSABLE_ESTADO_RESERVA`.

## **`HU-05`: Bloqueo Operativo de Cancha por Mantenimiento o Torneo**

* **Enunciado:** **Como** administrador de sede (`ADMIN_SEDE`), **quiero** bloquear una cancha en un rango horario **para** impedir reservas públicas durante mantenimientos o eventos privados.  
* **Reglas asociadas:** `RN-07`, `RN-08`.  
* **Escenarios Gherkin:**  
  * **Escenario 1: Bloqueo exitoso (`201 Created`)**  
    * **DADO** que el usuario tiene rol `ADMIN_SEDE` y la Cancha `2` no tiene reservas `PENDIENTE_PAGO` ni `CONFIRMADA` entre `2026-11-22T08:00:00Z` y `2026-11-22T12:00:00Z`,  
    * **CUANDO** envía `POST /api/v1/canchas/2/bloqueos` con `{ "fechaHoraInicio": "2026-11-22T08:00:00Z", "fechaHoraFin": "2026-11-22T12:00:00Z", "motivo": "Mantenimiento de iluminación" }`,  
    * **ENTONCES** el sistema guarda el bloqueo y retorna HTTP `201 Created`.  
  * **Escenario 2: Traslape con reservas activas (`409 Conflict`)**  
    * **DADO** que existe una reserva `CONFIRMADA` a las `10:00` en la Cancha `2`,  
    * **CUANDO** el administrador intenta crear el bloqueo de `08:00` a `12:00`,  
    * **ENTONCES** el sistema rechaza la operación con HTTP `409 Conflict` (`CONFLICT_RESERVAS_ACTIVAS`) sin crear el bloqueo.  
  * **Escenario 3: Acceso denegado a usuarios sin rol administrador (`403 Forbidden`)**  
    * **DADO** que un usuario con rol `ROLE_JUGADOR` intenta invocar `POST /api/v1/canchas/2/bloqueos`,  
    * **CUANDO** realiza la petición HTTP,  
    * **ENTONCES** Spring Security responde HTTP `403 Forbidden`.

# **3\. MATRIZ COMPLETA DE ATRIBUTOS DE CALIDAD / RNF (TALLER 2\)**

| ID | Categoría | Fuente / Estímulo | Componente / Entorno | Respuesta del Sistema | Métrica Cuantificable | Decisión Técnica de Implementación (Spring Boot / React / BD) |
| :---- | :---- | :---- | :---- | :---- | :---- | :---- |
| **`RNF-01`** | **Rendimiento** | 200 jugadores consultan simultáneamente la disponibilidad de canchas para el fin de semana. | `GET /api/v1/canchas/disponibilidad` en hora pico. | Consulta únicamente franjas de la fecha solicitada mediante proyección ligera. | Latencia **\< 350 ms en P95**. | Índice compuesto `(cancha_id, fecha_hora_inicio, estado)` en PostgreSQL \+ consulta JPQL optimizada sin carga N+1. |
| **`RNF-02`** | **Concurrencia / Integridad** | 2 o más jugadores envían `POST /api/v1/reservas` para la misma cancha y hora en el mismo segundo. | `ReservaService.crearReserva()` y tabla `reservas`. | Serializa el acceso a la cancha dentro de la transacción atómica, acepta la primera y rechaza las demás. | **0% de sobreventa** (cero duplicados) y rechazo `409` en **\< 250 ms**. | `@Transactional` \+ bloqueo pesimista `@Lock(LockModeType.PESSIMISTIC_WRITE)` sobre el registro de `Cancha` al validar disponibilidad. |
| **`RNF-03`** | **Seguridad** | Un atacante ejecuta 5 intentos fallidos consecutivos de login o envía un token JWT alterado/expirado. | `AuthController` y filtro JWT en Spring Security. | Rechaza tokens inválidos con `401`, valida roles con `@PreAuthorize` (`403`) y bloquea fuerza bruta con `429`. | Bloqueo por **15 minutos** tras 5 fallos; validación JWT en **\< 50 ms**. | Spring Security stateless con JWT firmado (`HMAC-SHA256`), contraseñas con `BCryptPasswordEncoder` y anotaciones `@PreAuthorize`. |
| **`RNF-04`** | **Disponibilidad / Resiliencia** | El servidor externo de correos (SMTP) se cae o tiene latencia \> 3 s al confirmar una reserva. | `NotificacionService` invocado desde `ReservaService`. | Confirma y persiste la reserva en PostgreSQL sin bloquear el hilo HTTP y ejecuta el envío de correo de forma asíncrona. | Confirmación HTTP en **\< 600 ms** con **0 rollbacks** por fallo de correo. | Desacoplamiento mediante `@Async` y `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` en Spring Boot. |
| **`RNF-05`** | **Mantenibilidad** | Se requiere modificar las reglas de penalidad por cancelación (`RN-04`/`RN-05`) o agregar un nuevo medio de pago. | Capas `Controller`, `Service`, `Repository` y `DTO`. | El cambio se aísla exclusivamente en la capa `@Service` sin alterar firmas de `@RestController` ni entidades JPA. | **0 cambios** en controladores o contratos OpenAPI ante ajustes internos de cálculo. | Separación estricta de paquetes (`controller`, `service`, `repository`, `domain`, `dto`, `exception`) e inyección por constructor. |
| **`RNF-06`** | **Usabilidad / Idempotencia** | El jugador hace doble clic rápido en el botón "Confirmar Reserva" bajo red lenta (\> 300 ms). | Componente `ModalConfirmarReserva` en React. | Deshabilita inmediatamente el botón en el primer clic y muestra estado de carga hasta recibir respuesta HTTP. | Cambio visual a `disabled` en **\< 100 ms** y **máximo 1 petición POST** enviada. | Estado `isSubmitting` en React bloqueando `onSubmit` \+ manejo global de errores en backend con `@RestControllerAdvice`. |

# **4\. PROCESO DE NEGOCIO TO-BE COMPLETO (TALLER 3\)**

```
flowchart TD
    subgraph FRONTEND ["CARRIL 1: CLIENTE WEB (SPA React)"]
        U1[Jugador selecciona cancha, fecha, hora y duración] --> U2[React valida campos obligatorios y activa isSubmitting = true]
        U2 --> U3[Envía POST /api/v1/reservas con Header Authorization: Bearer JWT]
        E409_UI[Muestra Banner Rojo 409: 'Turno recién ocupado' + Refresca grilla]
        E422_UI[Muestra Alerta Ámbar 422: 'Viola regla RN-01 o RN-02']
        OK201_UI[Muestra Vista Confirmación: Código RES-2026 + Temporizador 10:00 min]
    end

    subgraph BACKEND ["CARRIL 2: BACKEND (Spring Boot - Controller & Service)"]
        U3 --> B1{Spring Security: ¿JWT válido y ROLE_JUGADOR?}
        B1 -- No --> B_401[Retorna HTTP 401 / 403]
        B1 -- Sí --> B2[ReservaController valida @Valid CrearReservaRequest]
        B2 -- Inválido --> B_400[GlobalExceptionHandler retorna HTTP 400 Bad Request]
        B2 -- Válido --> B3{ReservaService: ¿Cumple RN-01 max 2/día y RN-02 min 2h anticipación?}
        B3 -- No --> B_422[Lanza ReglaNegocioException -> HTTP 422]
        B3 -- Sí --> B4[Adquiere Lock Pesimista sobre Cancha en BD]
        B4 --> B5{¿Cancha libre en el rango solicitado?}
        B5 -- No --> B_409[Lanza HorarioNoDisponibleException -> HTTP 409]
        B5 -- Sí --> B6[Calcula valorTotal y anticipo 50% RN-09; guarda en PENDIENTE_PAGO RN-03]
        B6 --> B7[Publica ReservaCreadaEvent @Async y retorna HTTP 201 Created]
    end

    subgraph DATABASE ["CARRIL 3: PERSISTENCIA Y EXTERNOS (PostgreSQL / SMTP)"]
        B4 -.-> DB1[(PostgreSQL: SELECT ... FOR UPDATE en canchas)]
        B6 -.-> DB2[(PostgreSQL: INSERT INTO reservas)]
        B7 -.-> EXT1[NotificacionListener @Async: Envía correo con enlace de pago]
    end

    B_409 --> E409_UI
    B_422 --> E422_UI
    B7 --> OK201_UI
```

# **5\. ESPECIFICACIÓN DE INTERFAZ UI/UX Y LOS 4 ESTADOS (TALLER 4 – FIGMA)**

| Frame en Figma | Condición Técnica en React | Componentes Visuales, Inputs y Salidas | Trazabilidad con Backend / Gherkin |
| :---- | :---- | :---- | :---- |
| **Frame 1: `Loading` & `Empty State`** | `isLoading === true` o `canchas.length === 0` | • **Loading:** 6 tarjetas *Skeleton* animadas en la grilla de horarios y `DatePicker` deshabilitado.• **Empty:** Ilustración de cancha vacía, mensaje *"No hay horarios disponibles para la fecha o filtro seleccionado"* y botón *"Ver mañana"*. | Cumple `RNF-06` y `HU-01`. |
| **Frame 2: `Success / Data State`** *(Grilla \+ Modal Reserva)* | `canchas.length > 0` | • **Filtros:** `DatePicker` (bloquea fechas pasadas), `Select` de `tipoCancha` (`PADEL`, `FUTBOL_5`, `TENIS`).• **Grilla:** Tarjetas por cancha con franjas horarias y *Badges* (`LIBRE` verde, `OCUPADO` gris, `MANTENIMIENTO` naranja).• **Modal Confirmar:** Muestra `canchaId`, `fechaHoraInicio`, selector `duracionHoras` (`1` o `2`), `valorTotal`, `valorAnticipo` (50%) y botón *"Confirmar Reserva"* (cambia a *"Procesando..."* \+ `disabled`). | Campos exactos de `CrearReservaRequest` (`HU-02` Escenario 1). |
| **Frame 3: `Error State`** *(Alertas `409` y `422`)* | `error.status === 409` o `error.status === 422` | • **Banner `409`:** Alerta roja superior en el modal leyendo `error.message` (*"La Cancha 1 ya fue reservada para las 19:00 por otro jugador"*) y disparando recarga automática del `GET /disponibilidad`.• **Alerta `422`:** Mensaje ámbar bajo el selector de hora mostrando `error.message` (*"Solo se permiten reservas con mínimo 2 horas de anticipación"*). | Renderiza el `ErrorResponse` estándar de Spring Boot (`HU-02` Escenarios 2 y 3). |
| **Frame 4: `Confirmation State`** *(Resultado `201 Created`)* | `reservaCreada.status === 201` | • Tarjeta de éxito con `codigoConfirmacion` (`RES-2026-0001`), *Badge* amarillo `PENDIENTE_PAGO`, monto del anticipo (`valorAnticipo`) y **temporizador regresivo en vivo (`10:00`)** con botón *"Pagar Anticipo Ahora"*. | Renderiza `ReservaResponse` (`HU-02` \+ `RN-03`). |

# **6\. MATRIZ PUENTE UI-API Y CONTRATO FORMAL OPENAPI 3.0.3 (TALLER 5\)**

## **6.1. Matriz de Mapeo Endpoints REST**

| Vista / Acción | Método y Ruta | Request DTO / Params | Respuestas HTTP y Response DTO |
| :---- | :---- | :---- | :---- |
| Grilla Disponibilidad | `GET /api/v1/canchas/disponibilidad` | Query: `fecha` (LocalDate, req), `tipoCancha` (Enum, opt) | `200`: `List<DisponibilidadCanchaResponse>400`: `ErrorResponse` |
| Modal Confirmar Reserva | `POST /api/v1/reservas` | Body: `CrearReservaRequest` | `201`: `ReservaResponse400`, `401`, `409`, `422`: `ErrorResponse` |
| Pantalla Pago Anticipo | `POST /api/v1/reservas/{id}/confirmar-pago` | Path: `id` (Long)Body: `ConfirmarPagoRequest` | `200`: `ReservaResponse404`, `409`: `ErrorResponse` |
| Modal Cancelar Reserva | `PATCH /api/v1/reservas/{id}/cancelacion` | Path: `id` (Long)Body: `CancelarReservaRequest` | `200`: `CancelacionResponse403`, `404`, `422`: `ErrorResponse` |
| Panel Admin Bloqueo | `POST /api/v1/canchas/{id}/bloqueos` | Path: `id` (Long)Body: `CrearBloqueoRequest` | `201`: `BloqueoResponse403`, `404`, `409`: `ErrorResponse` |

## **6.2. Contrato `openapi.yaml` Completo**

```
openapi: 3.0.3
info:
  title: CourtSync Pro API - Gestión y Reserva de Canchas Deportivas
  description: Especificación oficial API-First (Hito 1 - Ingeniería de Software II TdeA).
  version: 1.0.0
servers:
  - url: http://localhost:8080/api/v1
    description: Servidor Local Spring Boot
components:
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT
  schemas:
    TipoCancha:
      type: string
      enum: [PADEL, FUTBOL_5, TENIS]
    EstadoReserva:
      type: string
      enum: [PENDIENTE_PAGO, CONFIRMADA, CANCELADA, CANCELADA_CON_PENALIDAD, EXPIRADA, FINALIZADA]
    FranjaHorariaDTO:
      type: object
      properties:
        horaInicio:
          type: string
          example: "19:00"
        horaFin:
          type: string
          example: "20:00"
        estado:
          type: string
          enum: [LIBRE, OCUPADO, MANTENIMIENTO]
          example: "LIBRE"
    DisponibilidadCanchaResponse:
      type: object
      properties:
        canchaId:
          type: integer
          format: int64
          example: 1
        nombre:
          type: string
          example: "Cancha Pádel 1 - Panorámica"
        tipoCancha:
          $ref: '#/components/schemas/TipoCancha'
        tarifaHora:
          type: number
          format: double
          example: 80000.0
        franjas:
          type: array
          items:
            $ref: '#/components/schemas/FranjaHorariaDTO'
    CrearReservaRequest:
      type: object
      required: [canchaId, fechaHoraInicio, duracionHoras]
      properties:
        canchaId:
          type: integer
          format: int64
          example: 1
        fechaHoraInicio:
          type: string
          format: date-time
          example: "2026-11-20T19:00:00Z"
        duracionHoras:
          type: integer
          minimum: 1
          maximum: 2
          example: 1
    ReservaResponse:
      type: object
      properties:
        reservaId:
          type: integer
          format: int64
          example: 504
        codigoConfirmacion:
          type: string
          example: "RES-2026-0504"
        canchaId:
          type: integer
          format: int64
          example: 1
        canchaNombre:
          type: string
          example: "Cancha Pádel 1 - Panorámica"
        fechaHoraInicio:
          type: string
          format: date-time
          example: "2026-11-20T19:00:00Z"
        duracionHoras:
          type: integer
          example: 1
        valorTotal:
          type: number
          format: double
          example: 80000.0
        valorAnticipo:
          type: number
          format: double
          example: 40000.0
        estado:
          $ref: '#/components/schemas/EstadoReserva'
        fechaExpiracionPago:
          type: string
          format: date-time
          example: "2026-11-15T10:25:00Z"
    ConfirmarPagoRequest:
      type: object
      required: [metodoPago, referenciaTransaccion]
      properties:
        metodoPago:
          type: string
          enum: [PSE, TARJETA, NEQUI, BILLETERA_INTERNA]
          example: "PSE"
        referenciaTransaccion:
          type: string
          minLength: 4
          maxLength: 60
          example: "TX-99887766"
    CancelarReservaRequest:
      type: object
      required: [motivo]
      properties:
        motivo:
          type: string
          minLength: 5
          maxLength: 200
          example: "Cambio de horario laboral"
    CancelacionResponse:
      type: object
      properties:
        reservaId:
          type: integer
          format: int64
          example: 504
        nuevoEstado:
          $ref: '#/components/schemas/EstadoReserva'
        penalidadAplicada:
          type: boolean
          example: false
        saldoAcreditadoBilletera:
          type: number
          format: double
          example: 40000.0
        mensaje:
          type: string
          example: "Cancelación con >= 6 horas de anticipación (RN-04). Se acreditó el 100% del anticipo."
    CrearBloqueoRequest:
      type: object
      required: [fechaHoraInicio, fechaHoraFin, motivo]
      properties:
        fechaHoraInicio:
          type: string
          format: date-time
          example: "2026-11-22T08:00:00Z"
        fechaHoraFin:
          type: string
          format: date-time
          example: "2026-11-22T12:00:00Z"
        motivo:
          type: string
          minLength: 5
          maxLength: 150
          example: "Mantenimiento preventivo de iluminación"
    BloqueoResponse:
      type: object
      properties:
        bloqueoId:
          type: integer
          format: int64
          example: 12
        canchaId:
          type: integer
          format: int64
          example: 2
        fechaHoraInicio:
          type: string
          format: date-time
        fechaHoraFin:
          type: string
          format: date-time
        motivo:
          type: string
    ErrorResponse:
      type: object
      properties:
        timestamp:
          type: string
          format: date-time
          example: "2026-11-15T10:15:30Z"
        status:
          type: integer
          example: 409
        error:
          type: string
          example: "CONFLICT_HORARIO_OCUPADO"
        message:
          type: string
          example: "La Cancha 1 ya fue reservada para el horario seleccionado."
        path:
          type: string
          example: "/api/v1/reservas"
paths:
  /canchas/disponibilidad:
    get:
      summary: Consultar disponibilidad de canchas por fecha y tipo (HU-01)
      parameters:
        - in: query
          name: fecha
          required: true
          schema:
            type: string
            format: date
            example: "2026-11-20"
        - in: query
          name: tipoCancha
          required: false
          schema:
            $ref: '#/components/schemas/TipoCancha'
      responses:
        '200':
          description: Lista de canchas con franjas horarias
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/DisponibilidadCanchaResponse'
        '400':
          description: Fecha inválida o en el pasado
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'
  /reservas:
    post:
      summary: Crear reserva con bloqueo temporal de 10 min (HU-02)
      security:
        - bearerAuth: []
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CrearReservaRequest'
      responses:
        '201':
          description: Reserva creada en estado PENDIENTE_PAGO
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ReservaResponse'
        '400':
          description: Error de validación de campos del DTO
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'
        '409':
          description: Conflicto - Turno ocupado o bloqueado
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'
        '422':
          description: Violación de regla de negocio RN-01 o RN-02
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'
  /reservas/{id}/confirmar-pago:
    post:
      summary: Confirmar pago del anticipo dentro de los 10 minutos (HU-03)
      security:
        - bearerAuth: []
      parameters:
        - in: path
          name: id
          required: true
          schema:
            type: integer
            format: int64
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/ConfirmarPagoRequest'
      responses:
        '200':
          description: Reserva confirmada exitosamente
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ReservaResponse'
        '404':
          description: Reserva no encontrada
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'
        '409':
          description: La reserva expiró por superar los 10 minutos (RN-03)
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'
  /reservas/{id}/cancelacion:
    patch:
      summary: Cancelar reserva aplicando política de anticipación (HU-04)
      security:
        - bearerAuth: []
      parameters:
        - in: path
          name: id
          required: true
          schema:
            type: integer
            format: int64
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CancelarReservaRequest'
      responses:
        '200':
          description: Cancelación procesada (RN-04 o RN-05)
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/CancelacionResponse'
        '422':
          description: Violación de RN-06 (turno ya iniciado o finalizado)
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'
  /canchas/{id}/bloqueos:
    post:
      summary: Registrar bloqueo operativo de cancha (HU-05 - Solo ADMIN_SEDE)
      security:
        - bearerAuth: []
      parameters:
        - in: path
          name: id
          required: true
          schema:
            type: integer
            format: int64
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CrearBloqueoRequest'
      responses:
        '201':
          description: Bloqueo operativo registrado
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/BloqueoResponse'
        '403':
          description: Acceso denegado - Requiere rol ADMIN_SEDE (RN-07)
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'
        '409':
          description: Conflicto con reservas activas existentes (RN-08)
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'
```

# **7\. AUDITORÍA DE TRAZABILIDAD Y USO CRÍTICO DE IA**

## **7.1. Matriz de Auditoría Cruzada (*Peer Review* – 4 Puntos Verificados)**

1. **Trazabilidad Gherkin \$\\leftrightarrow\$ Figma:** Todos los escenarios de fallo (`409` por concurrencia en `HU-02`, `422` por anticipación mínima en `HU-02`, y expiración de 10 minutos en `HU-03`) cuentan con representación visual explícita en los Frames 3 y 4 del prototipo.  
2. **Trazabilidad Figma \$\\leftrightarrow\$ OpenAPI (Consistencia de Campos):** Los campos del formulario de reserva (`canchaId`, `fechaHoraInicio`, `duracionHoras`) y los campos de salida de confirmación (`reservaId`, `codigoConfirmacion`, `valorTotal`, `valorAnticipo`, `estado`, `fechaExpiracionPago`) coinciden al 100% en nombre y tipo de dato entre la interfaz y los `schemas` YAML.  
3. **Trazabilidad Reglas de Negocio (`RN-01` a `RN-09`) \$\\leftrightarrow\$ Códigos HTTP:** Cada regla de negocio está mapeada a un código HTTP semántico (`400`, `403`, `409`, `422`) y a un esquema único `ErrorResponse` gestionado centralizadamente.  
4. **Trazabilidad RNF \$\\leftrightarrow\$ Arquitectura Spring Boot:** Los 6 atributos de calidad tienen umbral numérico verificable y mecanismo técnico definido (`@Lock(PESSIMISTIC_WRITE)`, `@Async`, índices compuestos en PostgreSQL y `ErrorResponse` global).

## **7.2. Prompt Sugerido para Generación de Código en Google Antigravity**

*"Actúa como Arquitecto de Software Senior especialista en Java 21 y Spring Boot 3\. Lee esta especificación técnica completa del Hito 1 (Reglas de Negocio RN-01 a RN-09, escenarios Gherkin, RNF y contrato OpenAPI 3.0.3). Genera la implementación limpia siguiendo arquitectura por capas (paquetes `dto`, `exception`, `service` y `controller`):*

*1\. Todos los DTOs de Request y Response como Java `record` con anotaciones de Jakarta Validation (`@NotNull`, `@Min`, `@Max`, `@Size`) exactas al `openapi.yaml`.*

*2\. Un `GlobalExceptionHandler` con `@RestControllerAdvice` que devuelva exactamente el esquema `ErrorResponse` para códigos `400`, `403`, `404`, `409` y `422`.*

*3\. Las interfaces e implementaciones de `ReservaService` y `CanchaBloqueoService` aplicando `@Transactional`, bloqueo pesimista (`RNF-02`), eventos asíncronos (`RNF-04`) y todas las validaciones de `RN-01` a `RN-09`.*

*4\. Los controladores `ReservaController` y `CanchaController` respetando al pie de la letra las rutas `/api/v1/...`, seguridad con `@PreAuthorize` y códigos HTTP del contrato OpenAPI."*