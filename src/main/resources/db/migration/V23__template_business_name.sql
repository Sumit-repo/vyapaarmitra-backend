-- Default reminder templates sign with the SHOP's name, not the branch's.
-- {{branch_name}} renders "Main Branch", which reads like a bank notice — customers
-- should see the store they know. TemplateService now fills {{business_name}} from the
-- business record, and new businesses get business_name seeds (BusinessProvisioningService).
--
-- Rewrite only UNEDITED seeded bodies (exact-string match, same policy as V22): a shop
-- that customised its copy keeps it and can swap the token in the web template editor.
-- {{branch_name}} remains a supported token either way.

update message_templates
set body = 'Namaste {{customer_name}} ji, {{business_name}} se. Aapka {{amount_due}} baaki hai. Jab suvidha ho, kripya settle kar dein. Dhanyavaad!'
where category = 'soft_reminder'
  and body = 'Namaste {{customer_name}} ji, {{branch_name}} se. Aapka {{amount_due}} baaki hai. Jab suvidha ho, kripya settle kar dein. Dhanyavaad!';

update message_templates
set body = '{{customer_name}} ji, {{business_name}} se baat kar rahe hain. Aapka {{amount_due}} pichle {{overdue_days}} din se baaki hai, jiski due date {{due_date}} thi. Kripya jald se jald payment clear kar dein.'
where category = 'firm_reminder'
  and body = '{{customer_name}} ji, {{branch_name}} se baat kar rahe hain. Aapka {{amount_due}} pichle {{overdue_days}} din se baaki hai, jiski due date {{due_date}} thi. Kripya jald se jald payment clear kar dein.';

update message_templates
set body = '{{customer_name}} ji, {{window_start}} se {{window_end}} tak {{business_name}} ka hisaab: kharide {{window_credit}}, chukaye {{window_payment}}, total baaki {{amount_due}}. Kripya samay par settle kar dein. Dhanyavaad!'
where category = 'monthly_settlement'
  and body = '{{customer_name}} ji, {{window_start}} se {{window_end}} tak {{branch_name}} ka hisaab: kharide {{window_credit}}, chukaye {{window_payment}}, total baaki {{amount_due}}. Kripya samay par settle kar dein. Dhanyavaad!';