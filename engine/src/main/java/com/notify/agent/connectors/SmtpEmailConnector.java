package com.notify.agent.connectors;

import org.springframework.stereotype.Component;

import com.notify.agent.AbstractNotificationConnector;
import com.notify.agent.annotations.ManagedConfiguration;
import com.notify.agent.models.NotificationJob;
import com.notify.agent.models.subject.EmailSubject;
import com.notify.agent.models.subject.Subject;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

import lombok.RequiredArgsConstructor;

import org.checkerframework.checker.units.qual.s;
import org.springframework.mail.MailSendException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class SmtpEmailConnector extends AbstractNotificationConnector {

    private static final String ATTR_SUBJECT = "subject";
    private static final String ATTR_IS_HTML = "isHtml";
    private static final String ATTR_CC = "cc";
    private static final String ATTR_BCC = "bcc";
    private static final String ATTR_REPLY_TO = "replyTo";

    private final JavaMailSender mailSender = new JavaMailSenderImpl();

    @ManagedConfiguration(key = "notification.smtp.from")
    private String from; // default from

    @ManagedConfiguration(key = "notification.smtp.fromName")
    private String fromName; // optional personal name

    @ManagedConfiguration(key = "notification.smtp.defaultHtml")
    private boolean defaultHtml = true;

    @ManagedConfiguration(key = "notification.smtp.subjectPrefix")
    private String subjectPrefix = ""; // optional "[App] "

    @Override
    public String channel() {
        return "EMAIL";
    }

    @Override
    public void send(NotificationJob job, Subject subject) {

        if (!(subject instanceof EmailSubject)) {
            return;
        }

        EmailSubject emailSubject = (EmailSubject) subject;

        Objects.requireNonNull(job, "job");

        Map<String, String> attrs = job.getAttributes() == null ? Map.of() : job.getAttributes();

        String address = normalizeSubject(subject.getAddress());
        boolean isHtml = parseBoolean(attrs.get(ATTR_IS_HTML), defaultHtml);

        String[] to = parseAddresses(emailSubject.getAddress());
        String[] cc = parseAddresses(emailSubject.getCc());
        String[] bcc = parseAddresses(emailSubject.getBcc());
        String replyTo = firstOrNull(parseAddresses(attrs.get(ATTR_REPLY_TO)));

        try {
            MimeMessage message = mailSender.createMimeMessage();

            // false = no multipart. If you add attachments later, flip to true.
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    false,
                    StandardCharsets.UTF_8.name());

            // From
            InternetAddress fromAddr = buildFromAddress();
            helper.setFrom(fromAddr);

            // To/Cc/Bcc
            helper.setTo(to);
            if (cc.length > 0)
                helper.setCc(cc);
            if (bcc.length > 0)
                helper.setBcc(bcc);

            // Reply-To
            if (replyTo != null && !replyTo.isBlank()) {
                helper.setReplyTo(replyTo);
            }

            String content = job.getTemplate();

            // Subject & body
            helper.setSubject(emailSubject.getAddress());
            helper.setText(content == null ? "" : content, isHtml);

            // Idempotency-friendly headers (doesn't prevent duplicate sends by itself,
            // but helps tracking and downstream de-dupe if you add it)
            message.setHeader("X-Notification-Id", job.getId());
            message.setHeader("X-Notification-Channel", "email");
            message.setHeader("Message-ID", "<notif-" + job.getId() + "@" + safeDomain(fromAddr) + ">");
            message.setSentDate(new Date());

            // Optional: set higher priority if needed (1 highest, 5 lowest)
            // message.setHeader("X-Priority", "3");
            // message.setHeader("Priority", "urgent");

            // Ensure recipients applied
            message.saveChanges();

            mailSender.send(message);

        } catch (MailSendException mse) {
            // MailSendException is common for SMTP connectivity and protocol issues
            throw mse;
        } catch (Exception e) {
            // Let Worker retry/DLQ handle it
            throw new RuntimeException("SMTP send failed for notificationId=" + job.getId(), e);
        }
    }

    private InternetAddress buildFromAddress() throws Exception {
        if (from == null || from.isBlank()) {
            throw new IllegalStateException("notification.smtp.from is not configured");
        }
        if (fromName != null && !fromName.isBlank()) {
            return new InternetAddress(from, fromName, StandardCharsets.UTF_8.name());
        }
        return new InternetAddress(from);
    }

    private String normalizeSubject(String subject) {
        String s = (subject == null || subject.isBlank()) ? "Notification" : subject.trim();
        if (subjectPrefix != null && !subjectPrefix.isBlank()) {
            return subjectPrefix + s;
        }
        return s;
    }

    private boolean parseBoolean(String raw, boolean defaultVal) {
        if (raw == null)
            return defaultVal;
        return raw.equalsIgnoreCase("true") ||
                raw.equalsIgnoreCase("1") ||
                raw.equalsIgnoreCase("yes");
    }

    private String[] parseAddresses(String raw) {
        if (raw == null || raw.isBlank())
            return new String[0];

        // Split by comma/semicolon, trim, filter empties
        return Stream.of(raw.split("[,;]"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toArray(String[]::new);
    }

    private String firstOrNull(String[] arr) {
        return (arr == null || arr.length == 0) ? null : arr[0];
    }

    private String safeDomain(InternetAddress from) {
        try {
            String addr = from.getAddress();
            int at = addr.lastIndexOf('@');
            if (at > 0 && at < addr.length() - 1)
                return addr.substring(at + 1);
        } catch (Exception ignored) {
        }
        return "local";
    }

    @Override
    public void close() {
    }

}
