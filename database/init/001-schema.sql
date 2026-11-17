CREATE TABLE IF NOT EXISTS instruments (
  id BIGSERIAL PRIMARY KEY,
  symbol VARCHAR(40) NOT NULL UNIQUE,
  commodity VARCHAR(80) NOT NULL,
  grade VARCHAR(80),
  location_code VARCHAR(20),
  quote_currency CHAR(3) NOT NULL,
  quantity_unit VARCHAR(24) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS trading_orders (
  id UUID PRIMARY KEY,
  instrument_id BIGINT NOT NULL REFERENCES instruments(id),
  participant_id VARCHAR(120) NOT NULL,
  side VARCHAR(4) NOT NULL CHECK (side IN ('BUY','SELL')),
  order_type VARCHAR(12) NOT NULL CHECK (order_type IN ('LIMIT','MARKET')),
  price NUMERIC(24,8),
  quantity NUMERIC(24,8) NOT NULL CHECK (quantity > 0),
  remaining_quantity NUMERIC(24,8) NOT NULL CHECK (remaining_quantity >= 0),
  status VARCHAR(20) NOT NULL DEFAULT 'NEW',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_orders_book ON trading_orders(instrument_id, side, status, price, created_at);

CREATE TABLE IF NOT EXISTS rfqs (
  id UUID PRIMARY KEY,
  instrument_id BIGINT NOT NULL REFERENCES instruments(id),
  requester_id VARCHAR(120) NOT NULL,
  quantity NUMERIC(24,8) NOT NULL CHECK (quantity > 0),
  side VARCHAR(4) NOT NULL CHECK (side IN ('BUY','SELL')),
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  expires_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS trades (
  id UUID PRIMARY KEY,
  instrument_id BIGINT NOT NULL REFERENCES instruments(id),
  buy_order_id UUID,
  sell_order_id UUID,
  price NUMERIC(24,8) NOT NULL,
  quantity NUMERIC(24,8) NOT NULL CHECK (quantity > 0),
  executed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS audit_events (
  id BIGSERIAL PRIMARY KEY,
  actor_id VARCHAR(120) NOT NULL,
  action VARCHAR(100) NOT NULL,
  entity_type VARCHAR(60) NOT NULL,
  entity_id VARCHAR(120) NOT NULL,
  event_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO instruments(symbol, commodity, grade, location_code, quote_currency, quantity_unit)
VALUES ('CRUDE-BRENT','Crude Oil','Brent','GLOBAL','USD','BBL'),
       ('GOLD-1KG','Gold','999.9','GLOBAL','USD','KG')
ON CONFLICT (symbol) DO NOTHING;
