CREATE OR REPLACE FUNCTION commoditycx_check_balanced_journal() RETURNS trigger AS $$
DECLARE
  journal_key UUID;
  journal_total NUMERIC(24,8);
BEGIN
  IF TG_OP = 'DELETE' THEN journal_key := OLD.journal_id; ELSE journal_key := NEW.journal_id; END IF;
  SELECT COALESCE(SUM(amount),0) INTO journal_total FROM ledger_entries WHERE journal_id = journal_key;
  IF journal_total <> 0 THEN
    RAISE EXCEPTION 'Ledger journal % is not balanced; net amount is %', journal_key, journal_total;
  END IF;
  RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER ledger_journal_must_balance
AFTER INSERT OR UPDATE OR DELETE ON ledger_entries
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION commoditycx_check_balanced_journal();
