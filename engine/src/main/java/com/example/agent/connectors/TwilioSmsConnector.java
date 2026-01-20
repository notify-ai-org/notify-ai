package com.example.agent.connectors;

import com.example.agent.AbstractNotificationConnector;
import com.example.agent.models.ConnectorMetrics;
import com.example.agent.models.NotificationJob;
import com.example.agent.models.subject.Subject;
import com.twilio.Twilio;
import com.twilio.exception.ApiException;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.rest.api.v2010.account.MessageCreator;
import com.twilio.type.PhoneNumber;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

@Component
@RequiredArgsConstructor
public class TwilioSmsConnector extends AbstractNotificationConnector {

    private String accountSid;
    private String authToken;
    private String fromNumber;   // Twilio phone number
    private boolean enabled = true;

    private static final String ATTR_MEDIA_URL = "mediaUrl"; // optional MMS
    private static final String ATTR_STATUS_CALLBACK = "statusCallback";


    @Override
    public void init(AtomicReference<ConnectorMetrics> metrics) {
        super.init(metrics);
        if (accountSid == null || authToken == null) {
            throw new IllegalStateException("Twilio credentials not configured");
        }
        Twilio.init(accountSid,authToken);
    }

    @Override
    public String channel() {
        return "sms";
    }

    @Override
    public void send(NotificationJob job,Subject subject) {

        Objects.requireNonNull(job, "job");
        if (job.getTarget() == null || job.getTarget().isBlank()) {
            throw new IllegalArgumentException("SMS target (phone number) is missing");
        }

        String content = job.getTemplate();

        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("SMS content is empty");
        }

        Map<String, String> attrs =
                job.getAttributes() == null ? Map.of() : job.getAttributes();

        try {
            MessageCreator creator = Message.creator(
                    new PhoneNumber(normalize(subject.getAddress())),
                    new PhoneNumber(fromNumber),
                    content
            );

            // Optional MMS
            if (attrs.containsKey(ATTR_MEDIA_URL)) {
                creator.setMediaUrl(
                        java.util.List.of(java.net.URI.create(attrs.get(ATTR_MEDIA_URL)))
                );
            }

            // Optional delivery callback
            if (attrs.containsKey(ATTR_STATUS_CALLBACK)) {
                creator.setStatusCallback(attrs.get(ATTR_STATUS_CALLBACK));
            }

            // Idempotency / tracing
            creator.setProvideFeedback(true);
            creator.setApplicationSid(job.getId()); // used as correlation hint

            Message message = creator.create();

            // Treat queued/accepted as success
            if (message.getErrorCode() != null) {
                throw new RuntimeException(
                        "Twilio SMS failed: " + message.getErrorMessage()
                );
            }

        } catch (ApiException apiEx) {
            // Twilio-specific failure (rate limit, auth, invalid number)
            throw apiEx;
        } catch (Exception ex) {
            throw new RuntimeException(
                    "Twilio SMS send failed for notificationId=" + job.getId(),
                    ex
            );
        }
    }

    private String normalize(String number) {
        // Expect E.164 (+91..., +1...)
        String n = number.trim();
        if (!n.startsWith("+")) {
            throw new IllegalArgumentException(
                    "Phone number must be in E.164 format: " + number
            );
        }
        return n;
    }


    @Override
    public void close() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'close'");
    }
}
