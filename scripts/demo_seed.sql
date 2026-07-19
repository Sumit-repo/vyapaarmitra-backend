-- ============================================================================
-- VyapaarMitra — demo/test data seed  (run in the Supabase SQL editor)
-- ----------------------------------------------------------------------------
-- Adds ~12 realistic customers with credit/payment history + a few reminder
-- logs, attached to the OWNER's existing business + first branch.
--
-- Prerequisites:
--   * The backend has booted at least once with
--     BOOTSTRAP_OWNER_EMAIL set to the email below, so the owner account,
--     business, and main branch already exist. This script does NOT create the
--     owner (its password must be bcrypt-hashed by the app, not SQL).
--
-- Safe to run repeatedly: it tags seeded customers with the "demo" tag and
-- skips if any already exist. To re-seed from scratch, run the CLEANUP block
-- at the bottom first.
--
-- Denormalized fields (current_balance, oldest_due_date, trust_score,
-- trust_bucket) are set to match the inserted ledger rows, so the numbers stay
-- consistent until the app recomputes them on the next real entry.
-- ============================================================================

DO $$
DECLARE
  v_owner_email text := lower('1020sumit@gmail.com');  -- <-- your BOOTSTRAP_OWNER_EMAIL
  v_business    uuid;
  v_branch      uuid;
  v_owner       uuid;
  v_today       date := (now() AT TIME ZONE 'Asia/Kolkata')::date;
  v_cust        uuid;
  v_credit_due  date;
  r             record;
BEGIN
  -- Resolve the owner + their business + first branch.
  SELECT id, business_id INTO v_owner, v_business
  FROM users
  WHERE email = v_owner_email AND role = 'OWNER'
  LIMIT 1;

  IF v_owner IS NULL THEN
    RAISE EXCEPTION
      'Owner % not found. Boot the backend once with BOOTSTRAP_OWNER_EMAIL=% so the owner/business/branch are seeded, then re-run this script.',
      v_owner_email, v_owner_email;
  END IF;

  SELECT id INTO v_branch
  FROM branches
  WHERE business_id = v_business
  ORDER BY created_at
  LIMIT 1;

  IF v_branch IS NULL THEN
    RAISE EXCEPTION 'No branch found for business %.', v_business;
  END IF;

  -- Idempotency: bail if demo customers already exist.
  IF EXISTS (
    SELECT 1 FROM customers
    WHERE business_id = v_business AND tags @> '["demo"]'::jsonb
  ) THEN
    RAISE NOTICE 'Demo data already present for business %. Skipping.', v_business;
    RETURN;
  END IF;

  -- name, phone, balance, overdue_days, trust_score, trust_bucket
  FOR r IN
    SELECT * FROM (VALUES
      ('Ramesh Sharma',  '9876543210', 12500,  0, 84, 'GOOD'),
      ('Priya Gupta',    '9123456780',  4800,  5, 62, 'WATCH'),
      ('Suresh Patel',   '9988776655', 22000, 35, 34, 'RISKY'),
      ('Anita Singh',    '9012345678',  1500,  0, 58, 'WATCH'),
      ('Manoj Verma',    '9765432100',     0,  0, 90, 'GOOD'),
      ('Kavita Yadav',   '9654321098',  8750, 12, 60, 'WATCH'),
      ('Deepak Joshi',   '9543210987', 31000, 45, 28, 'RISKY'),
      ('Sunita Malik',   '9432109876',  5600,  0, 80, 'GOOD'),
      ('Rakesh Agarwal', '9321098765',  2200,  7, 55, 'WATCH'),
      ('Meena Tiwari',   '9210987654', 14300, 22, 57, 'WATCH'),
      ('Vijay Nair',     '9109876543',  3200,  0, 86, 'GOOD'),
      ('Farhan Ali',     '9098765432', 18000, 60, 22, 'RISKY')
    ) AS t(name, phone, balance, overdue_days, trust_score, trust_bucket)
  LOOP
    v_cust := gen_random_uuid();

    -- Due date for the opening credit: past if overdue, future if current.
    v_credit_due := CASE
      WHEN r.overdue_days > 0 THEN v_today - r.overdue_days
      ELSE v_today + 20
    END;

    INSERT INTO customers (
      id, business_id, branch_id, name, phone, tags,
      trust_score, trust_bucket, current_balance, oldest_due_date, active,
      created_at, updated_at
    ) VALUES (
      v_cust, v_business, v_branch, r.name, r.phone, '["demo"]'::jsonb,
      r.trust_score, r.trust_bucket, r.balance,
      CASE WHEN r.balance > 0 THEN v_credit_due ELSE NULL END,
      true, now() - interval '45 days', now()
    );

    IF r.balance > 0 THEN
      -- Opening credit of (balance + 3000), partly paid down by 3000 => net = balance.
      INSERT INTO ledger_entries (
        id, business_id, branch_id, customer_id, entry_type, amount,
        method, note, due_date, entry_at, created_by, created_at
      ) VALUES (
        gen_random_uuid(), v_business, v_branch, v_cust, 'CREDIT', r.balance + 3000,
        NULL, 'Goods on credit', v_credit_due, now() - interval '40 days', v_owner, now()
      );
      INSERT INTO ledger_entries (
        id, business_id, branch_id, customer_id, entry_type, amount,
        method, note, due_date, entry_at, created_by, created_at
      ) VALUES (
        gen_random_uuid(), v_business, v_branch, v_cust, 'PAYMENT', 3000,
        'cash', 'Part payment', NULL, now() - interval '9 days', v_owner, now()
      );
    ELSE
      -- Fully settled account: a credit cleared by an equal payment.
      INSERT INTO ledger_entries (
        id, business_id, branch_id, customer_id, entry_type, amount,
        method, note, due_date, entry_at, created_by, created_at
      ) VALUES (
        gen_random_uuid(), v_business, v_branch, v_cust, 'CREDIT', 5000,
        NULL, 'Goods on credit', v_today - 30, now() - interval '30 days', v_owner, now()
      );
      INSERT INTO ledger_entries (
        id, business_id, branch_id, customer_id, entry_type, amount,
        method, note, due_date, entry_at, created_by, created_at
      ) VALUES (
        gen_random_uuid(), v_business, v_branch, v_cust, 'PAYMENT', 5000,
        'upi', 'Full settlement', NULL, now() - interval '4 days', v_owner, now()
      );
    END IF;
  END LOOP;

  -- A few reminder logs for the overdue accounts (varied channels + outcomes).
  INSERT INTO reminder_logs (
    id, business_id, branch_id, customer_id, template_id, channel, outcome,
    promised_date, note, created_by, created_at
  )
  SELECT
    gen_random_uuid(), v_business, v_branch, c.id, NULL,
    (ARRAY['WHATSAPP','SMS','CALL'])[1 + floor(random() * 3)::int],
    (ARRAY['REMINDER_SENT','PROMISE_MADE','PAID'])[1 + floor(random() * 3)::int],
    CASE WHEN random() < 0.4 THEN v_today + 3 ELSE NULL END,
    'Demo reminder',
    v_owner,
    now() - (floor(random() * 12)::int || ' days')::interval
  FROM customers c
  WHERE c.business_id = v_business
    AND c.tags @> '["demo"]'::jsonb
    AND c.oldest_due_date IS NOT NULL
    AND c.oldest_due_date < v_today;

  RAISE NOTICE 'Demo data seeded for business % (branch %).', v_business, v_branch;
END $$;

-- ============================================================================
-- Suppliers (requires the V3 supplier migration deployed). Balance > 0 = payable.
-- ============================================================================
DO $$
DECLARE
  v_owner_email text := lower('1020sumit@gmail.com');
  v_business    uuid;
  v_branch      uuid;
  v_owner       uuid;
  v_today       date := (now() AT TIME ZONE 'Asia/Kolkata')::date;
  v_sup         uuid;
  v_credit_due  date;
  r             record;
BEGIN
  -- Skip cleanly if the supplier tables aren't there yet (migration not deployed).
  IF to_regclass('public.suppliers') IS NULL THEN
    RAISE NOTICE 'suppliers table not found — deploy the V3 migration first. Skipping supplier seed.';
    RETURN;
  END IF;

  SELECT id, business_id INTO v_owner, v_business
  FROM users WHERE email = v_owner_email AND role = 'OWNER' LIMIT 1;
  IF v_owner IS NULL THEN
    RAISE EXCEPTION 'Owner % not found; bootstrap first.', v_owner_email;
  END IF;
  SELECT id INTO v_branch FROM branches WHERE business_id = v_business ORDER BY created_at LIMIT 1;

  IF EXISTS (SELECT 1 FROM suppliers WHERE business_id = v_business AND tags @> '["demo"]'::jsonb) THEN
    RAISE NOTICE 'Demo suppliers already present. Skipping.';
    RETURN;
  END IF;

  -- name, phone, balance (payable), overdue_days
  FOR r IN
    SELECT * FROM (VALUES
      ('Sharma Wholesale',  '9811122233', 42500, 20),
      ('Gupta Paper Mart',  '9822233344',  8600,  0),
      ('Metro Stationers',  '9833344455',     0,  0),
      ('Ravi Distributors', '9844455566', 17250, 40),
      ('Anand Traders',     '9855566677', -1200,  0)
    ) AS t(name, phone, balance, overdue_days)
  LOOP
    v_sup := gen_random_uuid();
    v_credit_due := CASE WHEN r.overdue_days > 0 THEN v_today - r.overdue_days ELSE v_today + 20 END;

    INSERT INTO suppliers (id, business_id, branch_id, name, phone, tags, current_balance, oldest_due_date, active, created_at, updated_at)
    VALUES (v_sup, v_business, v_branch, r.name, r.phone, '["demo"]'::jsonb, r.balance,
            CASE WHEN r.balance > 0 THEN v_credit_due ELSE NULL END, true, now() - interval '45 days', now());

    IF r.balance > 0 THEN
      INSERT INTO supplier_ledger_entries (id, business_id, branch_id, supplier_id, entry_type, amount, note, due_date, entry_at, created_by, created_at)
      VALUES (gen_random_uuid(), v_business, v_branch, v_sup, 'CREDIT', r.balance + 5000, 'Stock purchased on credit', v_credit_due, now() - interval '40 days', v_owner, now());
      INSERT INTO supplier_ledger_entries (id, business_id, branch_id, supplier_id, entry_type, amount, method, note, entry_at, created_by, created_at)
      VALUES (gen_random_uuid(), v_business, v_branch, v_sup, 'PAYMENT', 5000, 'upi', 'Part payment', now() - interval '8 days', v_owner, now());
    ELSIF r.balance < 0 THEN
      -- advance: paid more than taken
      INSERT INTO supplier_ledger_entries (id, business_id, branch_id, supplier_id, entry_type, amount, note, due_date, entry_at, created_by, created_at)
      VALUES (gen_random_uuid(), v_business, v_branch, v_sup, 'CREDIT', 5000, 'Stock purchased', v_today - 30, now() - interval '30 days', v_owner, now());
      INSERT INTO supplier_ledger_entries (id, business_id, branch_id, supplier_id, entry_type, amount, method, note, entry_at, created_by, created_at)
      VALUES (gen_random_uuid(), v_business, v_branch, v_sup, 'PAYMENT', 6200, 'upi', 'Overpaid — advance', now() - interval '2 days', v_owner, now());
    ELSE
      INSERT INTO supplier_ledger_entries (id, business_id, branch_id, supplier_id, entry_type, amount, note, due_date, entry_at, created_by, created_at)
      VALUES (gen_random_uuid(), v_business, v_branch, v_sup, 'CREDIT', 5000, 'Stock purchased', v_today - 30, now() - interval '30 days', v_owner, now());
      INSERT INTO supplier_ledger_entries (id, business_id, branch_id, supplier_id, entry_type, amount, method, note, entry_at, created_by, created_at)
      VALUES (gen_random_uuid(), v_business, v_branch, v_sup, 'PAYMENT', 5000, 'cash', 'Full settlement', now() - interval '3 days', v_owner, now());
    END IF;
  END LOOP;

  RAISE NOTICE 'Demo suppliers seeded for business %.', v_business;
END $$;

-- ============================================================================
-- CLEANUP — uncomment and run to remove ALL demo data before re-seeding.
-- ============================================================================
-- DELETE FROM supplier_ledger_entries sle USING suppliers s
--   WHERE sle.supplier_id = s.id AND s.tags @> '["demo"]'::jsonb;
-- DELETE FROM suppliers WHERE tags @> '["demo"]'::jsonb;
-- DELETE FROM reminder_logs rl USING customers c
--   WHERE rl.customer_id = c.id AND c.tags @> '["demo"]'::jsonb;
-- DELETE FROM ledger_entries le USING customers c
--   WHERE le.customer_id = c.id AND c.tags @> '["demo"]'::jsonb;
-- DELETE FROM customer_reminder_settings crs USING customers c
--   WHERE crs.customer_id = c.id AND c.tags @> '["demo"]'::jsonb;
-- DELETE FROM customers WHERE tags @> '["demo"]'::jsonb;
