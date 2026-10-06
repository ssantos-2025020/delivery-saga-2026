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

CREATE TABLE IF NOT EXISTS stock_reserva (
    id BIGSERIAL PRIMARY KEY,
    reserva_id VARCHAR(255) UNIQUE NOT NULL,
    producto_id BIGINT NOT NULL,
    cantidad INTEGER NOT NULL,
    estado VARCHAR(20) NOT NULL,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_expiracion TIMESTAMP,
    FOREIGN KEY (producto_id) REFERENCES producto(id)
);

CREATE INDEX IF NOT EXISTS idx_stock_reserva_reserva_id ON stock_reserva(reserva_id);
CREATE INDEX IF NOT EXISTS idx_stock_reserva_estado ON stock_reserva(estado);
