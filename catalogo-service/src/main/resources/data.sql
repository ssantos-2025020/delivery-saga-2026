-- Insertar comercios de ejemplo
INSERT INTO comercio (nombre, categoria, abierto) VALUES
('Burger Palace', 'Comida Rápida', true),
('Pizza Hut', 'Italiana', true),
('Sushi Express', 'Japonesa', true),
('Tacos Loco', 'Mexicana', true),
('Sandwich Club', 'Sándwiches', true);

-- Insertar productos de ejemplo
INSERT INTO producto (nombre, precio, stock, comercio_id, version) VALUES
('Hamburguesa Clásica', 15.00, 50, 1, 0),
('Papas Fritas', 8.00, 100, 1, 0),
('Refresco', 5.00, 200, 1, 0),
('Pizza Pepperoni', 25.00, 30, 2, 0),
('Pizza Margarita', 20.00, 40, 2, 0),
('Roll de Salmón', 18.00, 25, 3, 0),
('Nigiri Salmón', 12.00, 50, 3, 0),
('Tacos al Pastor', 10.00, 60, 4, 0),
('Burrito XL', 18.00, 35, 4, 0),
('Club Sandwich', 12.00, 45, 5, 0),
('Pollo Sandwich', 10.00, 55, 5, 0);
