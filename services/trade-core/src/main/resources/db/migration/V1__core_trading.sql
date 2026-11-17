CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE TABLE participants (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), code VARCHAR(40) NOT NULL UNIQUE,
 legal_name VARCHAR(200) NOT NULL, country_code CHAR(2) NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','ACTIVE','SUSPENDED','REJECTED')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE roles (id BIGSERIAL PRIMARY KEY, name VARCHAR(40) NOT NULL UNIQUE);
INSERT INTO roles(name) VALUES ('ADMIN'),('COMPLIANCE'),('TRADER'),('RISK_OFFICER') ON CONFLICT DO NOTHING;
CREATE TABLE users (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), participant_id UUID REFERENCES participants(id),
 email VARCHAR(254) NOT NULL UNIQUE, full_name VARCHAR(160) NOT NULL,
 password_hash VARCHAR(100) NOT NULL, enabled BOOLEAN NOT NULL DEFAULT false,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), last_login_at TIMESTAMPTZ
);
CREATE TABLE user_roles (
 user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 role_id BIGINT NOT NULL REFERENCES roles(id), PRIMARY KEY(user_id,role_id)
);
CREATE TABLE instruments (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), symbol VARCHAR(40) NOT NULL UNIQUE,
 name VARCHAR(160) NOT NULL, commodity_type VARCHAR(60) NOT NULL,
 grade VARCHAR(80), location VARCHAR(120), unit VARCHAR(20) NOT NULL,
 currency CHAR(3) NOT NULL, tick_size NUMERIC(24,8) NOT NULL CHECK(tick_size > 0),
 min_quantity NUMERIC(24,8) NOT NULL DEFAULT 1 CHECK(min_quantity > 0),
 active BOOLEAN NOT NULL DEFAULT true, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE rfqs (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), reference VARCHAR(40) NOT NULL UNIQUE,
 instrument_id UUID NOT NULL REFERENCES instruments(id), buyer_participant_id UUID NOT NULL REFERENCES participants(id),
 seller_participant_id UUID REFERENCES participants(id), side VARCHAR(4) NOT NULL CHECK(side IN ('BUY','SELL')),
 quantity NUMERIC(24,8) NOT NULL CHECK(quantity > 0), target_price NUMERIC(24,8),
 currency CHAR(3) NOT NULL, delivery_location VARCHAR(120), delivery_date DATE,
 status VARCHAR(24) NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','QUOTED','NEGOTIATING','ACCEPTED','REJECTED','EXPIRED','CANCELLED')),
 expires_at TIMESTAMPTZ NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_by UUID NOT NULL REFERENCES users(id)
);
CREATE TABLE quotes (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), rfq_id UUID NOT NULL REFERENCES rfqs(id),
 participant_id UUID NOT NULL REFERENCES participants(id), price NUMERIC(24,8) NOT NULL CHECK(price > 0),
 quantity NUMERIC(24,8) NOT NULL CHECK(quantity > 0), side VARCHAR(4) NOT NULL CHECK(side IN ('BUY','SELL')),
 message VARCHAR(1000), status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE','COUNTERED','ACCEPTED','REJECTED','WITHDRAWN')),
 valid_until TIMESTAMPTZ NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX quotes_rfq_idx ON quotes(rfq_id,created_at);
CREATE TABLE orders (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), reference VARCHAR(40) NOT NULL UNIQUE,
 instrument_id UUID NOT NULL REFERENCES instruments(id), participant_id UUID NOT NULL REFERENCES participants(id),
 user_id UUID NOT NULL REFERENCES users(id), side VARCHAR(4) NOT NULL CHECK(side IN ('BUY','SELL')),
 order_type VARCHAR(12) NOT NULL CHECK(order_type IN ('LIMIT','MARKET')),
 price NUMERIC(24,8), quantity NUMERIC(24,8) NOT NULL CHECK(quantity > 0),
 remaining_quantity NUMERIC(24,8) NOT NULL CHECK(remaining_quantity >= 0),
 status VARCHAR(24) NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','PARTIALLY_FILLED','FILLED','CANCELLED','REJECTED')),
 time_in_force VARCHAR(8) NOT NULL DEFAULT 'GTC' CHECK(time_in_force IN ('GTC','IOC')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX orders_book_idx ON orders(instrument_id,side,status,price,created_at);
CREATE TABLE trades (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), reference VARCHAR(40) NOT NULL UNIQUE,
 instrument_id UUID NOT NULL REFERENCES instruments(id), buy_order_id UUID REFERENCES orders(id),
 sell_order_id UUID REFERENCES orders(id), buyer_participant_id UUID NOT NULL REFERENCES participants(id),
 seller_participant_id UUID NOT NULL REFERENCES participants(id), price NUMERIC(24,8) NOT NULL,
 quantity NUMERIC(24,8) NOT NULL, currency CHAR(3) NOT NULL,
 source VARCHAR(12) NOT NULL CHECK(source IN ('ORDER_BOOK','RFQ')),
 status VARCHAR(24) NOT NULL DEFAULT 'PENDING_CONFIRMATION',
 buyer_confirmed_at TIMESTAMPTZ, seller_confirmed_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE risk_limits (
 participant_id UUID PRIMARY KEY REFERENCES participants(id), max_order_quantity NUMERIC(24,8) NOT NULL DEFAULT 1000000,
 max_order_notional NUMERIC(24,8) NOT NULL DEFAULT 100000000, updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE audit_events (
 id BIGSERIAL PRIMARY KEY, actor_user_id UUID REFERENCES users(id), participant_id UUID REFERENCES participants(id),
 event_type VARCHAR(80) NOT NULL, entity_type VARCHAR(40) NOT NULL, entity_id VARCHAR(80) NOT NULL,
 details JSONB NOT NULL DEFAULT '{}'::jsonb, occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX audit_entity_idx ON audit_events(entity_type,entity_id,occurred_at);
CREATE INDEX audit_actor_idx ON audit_events(actor_user_id,occurred_at);
