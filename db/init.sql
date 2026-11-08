-- VULNBANK LAB: every identity and balance is synthetic. NEVER run as a public service.
CREATE TABLE IF NOT EXISTS users (
  id BIGSERIAL PRIMARY KEY,
  username VARCHAR(60) UNIQUE NOT NULL,
  password VARCHAR(255) NOT NULL, -- DELIBERATELY insecure storage for training
  full_name VARCHAR(120) NOT NULL,
  email VARCHAR(120) NOT NULL,
  phone VARCHAR(30) NOT NULL,
  role VARCHAR(20) NOT NULL DEFAULT 'customer'
);
CREATE TABLE IF NOT EXISTS accounts (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  iban VARCHAR(34) UNIQUE NOT NULL,
  account_type VARCHAR(20) NOT NULL,
  balance NUMERIC(14,2) NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS transactions (
  id BIGSERIAL PRIMARY KEY,
  account_id BIGINT NOT NULL REFERENCES accounts(id),
  description VARCHAR(140) NOT NULL,
  amount NUMERIC(14,2) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS support_tickets (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT REFERENCES users(id),
  subject VARCHAR(160) NOT NULL,
  message TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS api_keys (
  id BIGSERIAL PRIMARY KEY,
  api_key VARCHAR(180) UNIQUE NOT NULL,
  label VARCHAR(100) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE
);
INSERT INTO users (id,username,password,full_name,email,phone,role) VALUES
 (1,'alice','password123','Alice Jordan','alice@vulnbank.lab','0790000001','customer'),
 (2,'bob','password123','Bob Smith','bob@vulnbank.lab','0790000002','customer'),
 (3,'charlie','password123','Charlie Reed','charlie@vulnbank.lab','0790000003','customer'),
 (4,'diana','password123','Diana Jones','diana@vulnbank.lab','0790000004','customer'),
 (5,'eve','password123','Eve Adams','eve@vulnbank.lab','0790000005','customer'),
 (6,'frank','password123','Frank Blake','frank@vulnbank.lab','0790000006','customer'),
 (7,'grace','password123','Grace Ford','grace@vulnbank.lab','0790000007','customer'),
 (8,'heidi','password123','Heidi Green','heidi@vulnbank.lab','0790000008','customer'),
 (9,'ivan','password123','Ivan King','ivan@vulnbank.lab','0790000009','customer'),
 (10,'judy','password123','Judy Chen','judy@vulnbank.lab','0790000010','customer'),
 (11,'mallory','password123','Mallory Lane','mallory@vulnbank.lab','0790000011','customer'),
 (12,'olivia','password123','Olivia Stone','olivia@vulnbank.lab','0790000012','customer')
ON CONFLICT (id) DO NOTHING;
SELECT setval(pg_get_serial_sequence('users','id'),(SELECT MAX(id) FROM users));
INSERT INTO accounts (id,user_id,iban,account_type,balance)
SELECT id,id,'JO94CBJO001000000000' || lpad(id::text, 8, '0'),
       CASE WHEN id % 2=0 THEN 'Savings' ELSE 'Current' END,
       (2500 + id * 378.65)::numeric(14,2)
FROM users ON CONFLICT (id) DO NOTHING;
SELECT setval(pg_get_serial_sequence('accounts','id'),(SELECT MAX(id) FROM accounts));
INSERT INTO transactions (account_id,description,amount) VALUES
 (1,'Demo grocery purchase',-75.25),(1,'Demo payroll',1200.00),(2,'Demo utility bill',-49.50),
 (3,'Demo bank transfer',250.00),(4,'Demo merchant refund',31.00),(5,'Demo travel expense',-130.20);
INSERT INTO api_keys (api_key,label,active) VALUES
 ('VULNBANK_LAB_KEY_123','Intentionally hardcoded Android training key',TRUE)
ON CONFLICT (api_key) DO NOTHING;
INSERT INTO support_tickets (user_id,subject,message) VALUES
 (1,'Welcome to the lab','All customer names and financial records are fictitious.');

-- Intentionally vulnerable simulated funds-transfer exercise (all money is fictional).
CREATE TABLE IF NOT EXISTS bank_transfers (
  id BIGSERIAL PRIMARY KEY,
  from_account_id BIGINT NOT NULL REFERENCES accounts(id),
  to_account_id BIGINT NOT NULL REFERENCES accounts(id),
  amount NUMERIC(14,2) NOT NULL CHECK (amount > 0),
  note VARCHAR(140) NOT NULL DEFAULT '',
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
