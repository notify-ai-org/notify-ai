import React from 'react';
import CodeBlock from '../components/CodeBlock';
import { motion } from 'framer-motion';

const SDKGuide = () => {
  const mavenCode = `<dependency>
    <groupId>com.notify.agent</groupId>
    <artifactId>vocabulary-agent-client</artifactId>
    <version>1.0.0</version>
</dependency>`;

  const eventCode = `@Event(
    key = "ORDER_PLACED",
    description = "Customer placed an order",
    eventType = "static",
    scheduleIntent = "immediate",
    priority = 5,
    payload = OrderPayload.class
)
public OrderPayload placeOrder(OrderPayload payload) {
    // Business logic
    return payload;
}`;

  const supplierCode = `@SubjectSupplier(event = "ORDER_PLACED")
public List<Subject> getOrderPlacedSubjects(OrderPayload payload) {
    Customer c = customers.get(payload.getCustomerId());
    return List.of(new EmailSubject(c.getEmail(), Map.of("name", c.getName())));
}`;

  return (
    <div style={{ flex: 1, overflowY: 'auto', padding: '5rem 3rem 10rem 3rem' }}>
      <div style={{ maxWidth: '56rem', margin: '0 auto' }}>
        <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}>
          <h1 style={{ fontSize: '3rem', fontWeight: 700, marginBottom: '1.5rem' }}>Integration <span style={{ color: '#facc15' }}>Guide</span></h1>
          <p style={{ fontSize: '1.25rem', color: '#9ca3af', marginBottom: '3rem' }}>Set up Notify AI in your Spring Boot application in minutes.</p>
        </motion.div>

        <section style={{ marginBottom: '4rem' }}>
          <h2 style={{ fontSize: '1.5rem', fontWeight: 700, marginBottom: '1.5rem', display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
            <span style={{ width: '2rem', height: '2rem', borderRadius: '0.5rem', backgroundColor: 'rgba(234, 179, 8, 0.2)', color: '#facc15', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '0.875rem' }}>1</span>
            Installation
          </h2>
          <p style={{ color: '#9ca3af', marginBottom: '1rem' }}>Add the client SDK to your Maven project:</p>
          <CodeBlock code={mavenCode} language="xml" />
        </section>

        <section style={{ marginBottom: '4rem' }}>
          <h2 style={{ fontSize: '1.5rem', fontWeight: 700, marginBottom: '1.5rem', display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
            <span style={{ width: '2rem', height: '2rem', borderRadius: '0.5rem', backgroundColor: 'rgba(234, 179, 8, 0.2)', color: '#facc15', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '0.875rem' }}>2</span>
            Annotate Events
          </h2>
          <p style={{ color: '#9ca3af', marginBottom: '1rem' }}>Use the <code>@Event</code> annotation to intercept any method. The SDK will automatically wrap the result and push it to the ACP server.</p>
          <CodeBlock code={eventCode} />
        </section>

        <section style={{ marginBottom: '4rem' }}>
          <h2 style={{ fontSize: '1.5rem', fontWeight: 700, marginBottom: '1.5rem', display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
            <span style={{ width: '2rem', height: '2rem', borderRadius: '0.5rem', backgroundColor: 'rgba(234, 179, 8, 0.2)', color: '#facc15', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '0.875rem' }}>3</span>
            Resolve Recipients
          </h2>
          <p style={{ color: '#9ca3af', marginBottom: '1rem' }}>The <code>@SubjectSupplier</code> defines who should receive the notification based on the event payload.</p>
          <CodeBlock code={supplierCode} />
        </section>

        <div style={{ padding: '2rem', borderRadius: '1rem', backgroundColor: 'rgba(234, 179, 8, 0.1)', border: '1px solid rgba(234, 179, 8, 0.2)', marginTop: '5rem' }}>
          <h3 style={{ fontSize: '1.25rem', fontWeight: 700, color: '#facc15', marginBottom: '1rem' }}>Pro Tip</h3>
          <p style={{ color: '#d1d5db' }}>You can also use <code>@VocabularySupplier</code> to enrich event data with AI insights or <code>@Rule</code> to define complex business conditions for notification dispatch.</p>
        </div>
      </div>
    </div>
  );
};

export default SDKGuide;
