-- UPI collection details for the shop. Used to build a upi://pay deep link + QR so
-- customers can pay a reminder in one tap. Owner/branch-manager editable. Null = not set.
alter table businesses add column upi_vpa text;
alter table businesses add column upi_payee_name text;
