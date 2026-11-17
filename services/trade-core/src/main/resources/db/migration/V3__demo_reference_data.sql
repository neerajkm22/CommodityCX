-- Development reference-data fixtures. Replace/extend through controlled reference-data administration.
INSERT INTO instruments(symbol,name,commodity_type,grade,location,unit,currency,tick_size,min_quantity,active)
VALUES
 ('BRENT-USD','Brent Crude Oil','ENERGY','Dated Brent','North Sea','BBL','USD',0.01,1,true),
 ('XAU-1KG','Gold 1 Kilogram','PRECIOUS_METALS','995 Fine','Approved Vault','KG','USD',0.01,1,true),
 ('CU-CATHODE','Copper Cathode Grade A','BASE_METALS','Grade A','LME Warehouse','MT','USD',0.50,1,true)
ON CONFLICT(symbol) DO NOTHING;
