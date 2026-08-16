-- Cloudinary-hosted images: profile pictures (identity) and bill photos (ledger entries).
-- We store the secure_url for display and the public_id so the image can be deleted later.
alter table users add column avatar_url text;
alter table users add column avatar_public_id text;

alter table ledger_entries add column attachment_url text;
alter table ledger_entries add column attachment_public_id text;

alter table supplier_ledger_entries add column attachment_url text;
alter table supplier_ledger_entries add column attachment_public_id text;
