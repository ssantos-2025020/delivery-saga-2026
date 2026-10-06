CREATE TABLE IF NOT EXISTS usuario (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(100) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    rol VARCHAR(50) NOT NULL,
    nombre VARCHAR(100) NOT NULL,
    fecha_registro TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    activo BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS comercio (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    categoria VARCHAR(100) NOT NULL,
    abierto BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS producto (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    precio DECIMAL(10,2) NOT NULL,
    stock INTEGER NOT NULL CHECK (stock >= 0),
    comercio_id BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    FOREIGN KEY (comercio_id) REFERENCES comercio(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_producto_comercio ON producto(comercio_id);

CREATE TABLE IF NOT EXISTS pedido (
    id BIGSERIAL PRIMARY KEY,
    cliente_id BIGINT NOT NULL,
    repartidor_id BIGINT,
    estado VARCHAR(50) NOT NULL,
    fecha_pedido TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    total DECIMAL(10,2) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    FOREIGN KEY (cliente_id) REFERENCES usuario(id),
    FOREIGN KEY (repartidor_id) REFERENCES usuario(id)
);

CREATE INDEX IF NOT EXISTS idx_pedido_cliente_fecha ON pedido(cliente_id, fecha_pedido DESC);
CREATE INDEX IF NOT EXISTS idx_pedido_estado_repartidor ON pedido(estado, repartidor_id);

CREATE TABLE IF NOT EXISTS detalle_pedido (
    id BIGSERIAL PRIMARY KEY,
    pedido_id BIGINT NOT NULL,
    producto_id BIGINT NOT NULL,
    cantidad INTEGER NOT NULL,
    precio_unitario DECIMAL(10,2) NOT NULL,
    subtotal DECIMAL(10,2) NOT NULL,
    FOREIGN KEY (pedido_id) REFERENCES pedido(id) ON DELETE CASCADE,
    FOREIGN KEY (producto_id) REFERENCES producto(id)
);

CREATE TABLE IF NOT EXISTS idempotency_key (
    id BIGSERIAL PRIMARY KEY,
    cliente_id BIGINT NOT NULL,
    key VARCHAR(255) NOT NULL,
    pedido_id BIGINT NOT NULL,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (cliente_id) REFERENCES usuario(id),
    FOREIGN KEY (pedido_id) REFERENCES pedido(id),
    UNIQUE (cliente_id, key)
);
