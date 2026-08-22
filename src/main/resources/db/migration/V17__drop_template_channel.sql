-- Templates are channel-agnostic. Sending a reminder is a manual wa.me/sms: deep link
-- from the shopkeeper's own phone, so a template's content works on either channel;
-- the WhatsApp/SMS split only hid good templates from the other tab. Drop the vestigial
-- column. (The send channel actually used lives on reminder_logs.channel — untouched.)
alter table message_templates drop column channel;
