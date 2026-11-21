export type TipoCancha = 'PADEL' | 'FUTBOL_5' | 'TENIS';

export interface FranjaHorariaDTO {
  horaInicio: string;
  horaFin: string;
  estado: 'LIBRE' | 'OCUPADO' | 'MANTENIMIENTO';
}

export interface DisponibilidadCanchaResponse {
  canchaId: number;
  nombre: string;
  tipoCancha: TipoCancha;
  tarifaHora: number;
  franjas: FranjaHorariaDTO[];
}

export interface CrearReservaRequest {
  canchaId: number;
  fechaHoraInicio: string;
  duracionHoras: 1 | 2;
}

export interface ReservaResponse {
  reservaId: number;
  codigoConfirmacion: string;
  canchaId: number;
  canchaNombre: string;
  fechaHoraInicio: string;
  duracionHoras: number;
  valorTotal: number;
  valorAnticipo: number;
  estado: string;
  fechaExpiracionPago: string;
}

export interface ErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
}

export class ApiError extends Error {
  public readonly status: number;
  public readonly errorCode: string;

  constructor(status: number, errorCode: string, message: string) {
    super(message);
    this.status = status;
    this.errorCode = errorCode;
  }
}

const API_BASE = '/api/v1';

export async function consultarDisponibilidad(
  fecha: string,
  tipoCancha?: TipoCancha | ''
): Promise<DisponibilidadCanchaResponse[]> {
  const params = new URLSearchParams({ fecha });
  if (tipoCancha) {
    params.set('tipoCancha', tipoCancha);
  }

  const response = await fetch(`${API_BASE}/canchas/disponibilidad?${params.toString()}`);
  if (!response.ok) {
    const err = (await response.json().catch(() => null)) as ErrorResponse | null;
    throw new ApiError(
      response.status,
      err?.error ?? 'ERROR_DISPONIBILIDAD',
      err?.message ?? 'No fue posible consultar la disponibilidad.'
    );
  }

  return (await response.json()) as DisponibilidadCanchaResponse[];
}

export async function crearReserva(
  payload: CrearReservaRequest,
  usuarioId = 1
): Promise<ReservaResponse> {
  const response = await fetch(`${API_BASE}/reservas`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Usuario-Id': String(usuarioId),
    },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    const err = (await response.json().catch(() => null)) as ErrorResponse | null;
    throw new ApiError(
      response.status,
      err?.error ?? 'ERROR_RESERVA',
      err?.message ?? 'No fue posible crear la reserva.'
    );
  }

  return (await response.json()) as ReservaResponse;
}
