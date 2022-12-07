#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
	CREATE USER mquser;
	ALTER USER mquser WITH PASSWORD 'mqpass';
	CREATE DATABASE mqdb;
EOSQL

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname mqdb <<-EOSQL
  GRANT ALL PRIVILEGES ON SCHEMA public TO mquser;
EOSQL