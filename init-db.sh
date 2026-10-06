#!/bin/bash
set -e

echo "Creating databases..."

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
CREATE DATABASE auth_db;
CREATE DATABASE catalogo_db;
CREATE DATABASE pedidos_db;
EOSQL

echo "Databases created successfully."
