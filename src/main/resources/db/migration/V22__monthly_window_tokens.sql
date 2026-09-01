-- Monthly-settlement reminders become period-aware: the mobile monthly-reminder sheet now
-- sends startDate/endDate to POST /templates/{id}/render, which fills the new window_*
-- tokens (picked dates, credit/payment sums inside the window, balance at window close).
-- Rewrite only UNEDITED seeded bodies (exact-string match): a shop that customised its
-- monthly message keeps its copy and can add the tokens itself in the web template editor.

update message_templates
set body = '{{customer_name}} ji, {{window_start}} se {{window_end}} tak {{branch_name}} ka hisaab: kharide {{window_credit}}, chukaye {{window_payment}}, total baaki {{amount_due}}. Kripya samay par settle kar dein. Dhanyavaad!'
where category = 'monthly_settlement'
  and body = '{{customer_name}} ji, {{branch_name}} se is mahine ka kul hisaab {{amount_due}} baaki hai. Kripya samay par settle kar dein. Dhanyavaad!';