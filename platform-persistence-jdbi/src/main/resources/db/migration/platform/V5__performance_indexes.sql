CREATE INDEX idx_plt_messages_updated_epoch ON plt_messages(updated_at_epoch_ms DESC, id DESC);
CREATE INDEX idx_plt_contacts_updated_epoch ON plt_contacts(updated_at_epoch_ms DESC, id DESC);
CREATE INDEX idx_plt_contact_emails_contact  ON plt_contact_emails(contact_id);
CREATE INDEX idx_plt_contact_phones_contact  ON plt_contact_phones(contact_id);
CREATE INDEX idx_plt_contact_socials_contact ON plt_contact_socials(contact_id);
