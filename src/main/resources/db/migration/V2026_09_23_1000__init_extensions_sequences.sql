-- Trigram index support for name search on organizations.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Human-readable business codes: BRD-00042, DST-000123, RTL-000456.
CREATE SEQUENCE IF NOT EXISTS seq_brand_code;
CREATE SEQUENCE IF NOT EXISTS seq_distributor_code;
CREATE SEQUENCE IF NOT EXISTS seq_retailer_code;
