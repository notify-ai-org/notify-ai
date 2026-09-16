-- Apply after 002_channel_secrets.sql. Runtime settings belong to each tenant channel.
ALTER TABLE tenant_channel_config
    ADD COLUMN connector_class varchar(255),
    ADD COLUMN instances integer NOT NULL DEFAULT 1 CHECK (instances BETWEEN 1 AND 100),
    ADD COLUMN delay_ms bigint NOT NULL DEFAULT 0 CHECK (delay_ms >= 0),
    ADD COLUMN max_attempts integer NOT NULL DEFAULT 1 CHECK (max_attempts BETWEEN 1 AND 100),
    ADD COLUMN back_off_multiplier integer NOT NULL DEFAULT 2 CHECK (back_off_multiplier BETWEEN 1 AND 100);

UPDATE tenant_channel_config SET connector_class = CASE provider
    WHEN 'WEBHOOK' THEN 'com.notify.agent.connectors.WebhookConnector'
    WHEN 'META_WHATSAPP' THEN 'com.notify.agent.connectors.MetaWhatsAppConnector'
    WHEN 'FCM' THEN 'com.notify.agent.connectors.FcmPushConnector'
    WHEN 'TWILIO' THEN 'com.notify.agent.connectors.TwilioSmsConnector'
    WHEN 'SMTP' THEN 'com.notify.agent.connectors.SmtpEmailConnector'
END;
