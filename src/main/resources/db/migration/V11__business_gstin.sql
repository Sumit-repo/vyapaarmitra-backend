-- Business-level GSTIN so the SaaS-charge Razorpay invoice can be made GST-valid
-- (Razorpay stamps the buyer GSTIN on the tax invoice only when it's set on the
-- customer/subscription). Nullable — a shop isn't required to be GST-registered.
alter table businesses add column gstin text;
