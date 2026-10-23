-- CanteenApp standalone MySQL setup
-- Compatible with src/com/canteen/CanteenServlet.java
-- Run this entire script in MySQL Workbench as a user allowed to create databases/tables.

CREATE DATABASE IF NOT EXISTS canteen_app
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE canteen_app;

CREATE TABLE IF NOT EXISTS users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(80) NOT NULL UNIQUE,
  full_name VARCHAR(120) NOT NULL,
  password VARCHAR(255) NOT NULL,
  role VARCHAR(16) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT chk_user_role CHECK (role IN ('STUDENT', 'ADMIN'))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS menu_items (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  description VARCHAR(500) NOT NULL DEFAULT '',
  category VARCHAR(60) NOT NULL DEFAULT 'Meals',
  price DECIMAL(10,2) NOT NULL,
  available BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS cart_items (
  user_id BIGINT NOT NULL,
  menu_item_id BIGINT NOT NULL,
  quantity INT NOT NULL DEFAULT 1,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, menu_item_id),
  CONSTRAINT fk_cart_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_cart_menu FOREIGN KEY (menu_item_id) REFERENCES menu_items(id) ON DELETE CASCADE,
  CONSTRAINT chk_cart_quantity CHECK (quantity BETWEEN 1 AND 20)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS orders (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  ticket_code VARCHAR(24) NOT NULL UNIQUE,
  total DECIMAL(10,2) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES users(id),
  INDEX idx_orders_user_created (user_id, created_at),
  INDEX idx_orders_status (status)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS order_items (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  menu_item_id BIGINT NULL,
  item_name VARCHAR(100) NOT NULL,
  unit_price DECIMAL(10,2) NOT NULL,
  quantity INT NOT NULL,
  CONSTRAINT fk_item_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
  CONSTRAINT fk_item_menu FOREIGN KEY (menu_item_id) REFERENCES menu_items(id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- Seed sample menu only when the table is empty; safe to rerun.
INSERT INTO menu_items (name, description, category, price, available)
SELECT seed.name, seed.description, seed.category, seed.price, TRUE
FROM (
  SELECT 'Masala Dosa' AS name, 'Crispy dosa with potato masala and chutney' AS description, 'Breakfast' AS category, 65.00 AS price
  UNION ALL SELECT 'Veg Sandwich', 'Grilled sandwich with fresh vegetables', 'Snacks', 55.00
  UNION ALL SELECT 'Paneer Rice Bowl', 'Paneer, rice and house masala', 'Meals', 110.00
  UNION ALL SELECT 'Samosa (2 pcs)', 'Golden pastry with spiced potato filling', 'Snacks', 30.00
  UNION ALL SELECT 'Cold Coffee', 'Chilled coffee with milk', 'Drinks', 50.00
  UNION ALL SELECT 'Chocolate Brownie', 'Soft chocolate brownie', 'Desserts', 45.00
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM menu_items LIMIT 1);

-- Local classroom demo accounts (plain-text passwords; never use this setup in production).
-- Safe to rerun: resets these two demo accounts to the credentials shown here.
INSERT INTO users (username, full_name, password, role)
VALUES
  ('admin', 'Canteen Administrator', 'admin', 'ADMIN'),
  ('student', 'Demo Student', 'student', 'STUDENT')
ON DUPLICATE KEY UPDATE
  full_name = VALUES(full_name),
  password = VALUES(password),
  role = VALUES(role);

-- Verify:
-- SELECT id, username, full_name, role FROM users;
-- SELECT id, name, category, price, available FROM menu_items;
