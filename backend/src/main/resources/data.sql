INSERT INTO jugadores (id, nombre, email)
VALUES (1, 'Jugador Demo', 'jugador@courtsync.pro')
ON CONFLICT (id) DO NOTHING;

INSERT INTO canchas (id, nombre, tipo_cancha, tarifa_hora, activa)
VALUES
    (1, 'Cancha Pádel 1 - Panorámica', 'PADEL', 80000.0, true),
    (2, 'Cancha Fútbol 5 - Sintética Norte', 'FUTBOL_5', 140000.0, true),
    (3, 'Cancha Tenis 1 - Polvo de Ladrillo', 'TENIS', 95000.0, true)
ON CONFLICT (id) DO NOTHING;

SELECT setval(pg_get_serial_sequence('jugadores', 'id'), COALESCE((SELECT MAX(id) FROM jugadores), 1));
SELECT setval(pg_get_serial_sequence('canchas', 'id'), COALESCE((SELECT MAX(id) FROM canchas), 1));
