CREATE TABLE IF NOT EXISTS pedido (
    id BIGSERIAL PRIMARY KEY,
    cliente_id BIGINT NOT NULL,
    cliente_nombre VARCHAR(100),
    repartidor_id BIGINT,
    repartidor_nombre VARCHAR(100),
    estado VARCHAR(50) NOT NULL,
    fecha_pedido TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reserva_id VARCHAR(255) NOT NULL,
    total DECIMAL(10,2) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_pedido_cliente_fecha ON pedido(cliente_id, fecha_pedido DESC);
CREATE INDEX IF NOT EXISTS idx_pedido_estado_repartidor ON pedido(estado, repartidor_id);

CREATE TABLE IF NOT EXISTS detalle_pedido (
    id BIGSERIAL PRIMARY KEY,
    pedido_id BIGINT NOT NULL,
    producto_id BIGINT NOT NULL,
    producto_nombre VARCHAR(100),
    cantidad INTEGER NOT NULL,
    precio_unitario DECIMAL(10,2) NOT NULL,
    subtotal DECIMAL(10,2) NOT NULL,
    FOREIGN KEY (pedido_id) REFERENCES pedido(id) ON DELETE CASCADE
);
