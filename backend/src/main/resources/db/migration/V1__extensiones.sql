-- V1: extensiones requeridas por el dominio (sin tablas de dominio).
--  * btree_gist: permite EXCLUDE USING gist con columnas de igualdad (no superposición de turnos).
--  * citext: comparación de texto sin distinguir mayúsculas (p. ej. usuario.email en C-02).
-- Ambas son "trusted extensions" desde PostgreSQL 13: el dueño de la base puede crearlas.
-- Rollback (solo desarrollo local): DROP EXTENSION citext; DROP EXTENSION btree_gist;
CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE EXTENSION IF NOT EXISTS citext;
