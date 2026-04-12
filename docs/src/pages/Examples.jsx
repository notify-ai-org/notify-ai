import React from 'react';
import CodeBlock from '../components/CodeBlock';
import { motion } from 'framer-motion';
import { ShoppingCart, Landmark } from 'lucide-react';

const Examples = () => {
  const ecommerceCode = `@Event(key = "ABANDONED_CART", eventType = "deferred", priority = 3)
public CartPayload abandonCart(CartPayload payload) {
    log.info("🛒 Cart abandoned: {} by customer {}", payload.getCartId(), payload.getCustomerId());
    return payload;
}

@Rule(name = "fraud-check", event = "ORDER_PLACED")
public boolean fraudCheck(OrderPayload payload) {
    return payload.getAmount() < 1000.0;
}`;

  const bankingCode = `@Event(key = "LARGE_TRANSFER", priority = 8)
public TransactionPayload processLargeTransfer(TransactionPayload payload) {
    log.info("Large transfer: {} -> {} — \${} {}", payload.getFromAccountId(), payload.getToAccountId(), payload.getAmount(), payload.getCurrency());
    return payload;
}

@SubjectSupplier(event = "SUSPICIOUS_LOGIN")
public List<Subject> getSecuritySubjects(LoginPayload payload) {
    Account acc = accounts.get(payload.getUserId());
    return List.of(
        new SmsSubject(acc.getPhone(), Map.of("user", acc.getHolderName())),
        new EmailSubject(acc.getEmail(), Map.of("user", acc.getHolderName()))
    );
}`;

  return (
    <div style={{ flex: 1, overflowY: 'auto', padding: '5rem 3rem 10rem 3rem' }}>
      <div style={{ maxWidth: '56rem', margin: '0 auto' }}>
        <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}>
          <h1 style={{ fontSize: '3rem', fontWeight: 700, marginBottom: '1.5rem' }}>Real-World <span style={{ color: '#fb923c' }}>Examples</span></h1>
          <p style={{ fontSize: '1.25rem', color: '#9ca3af', marginBottom: '3rem' }}>See how industry leaders use Notify AI to automate customer journeys.</p>
        </motion.div>

        <section style={{ marginBottom: '6rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', marginBottom: '2rem' }}>
            <div style={{ padding: '0.75rem', borderRadius: '0.75rem', backgroundColor: 'rgba(249, 115, 22, 0.1)', color: '#fb923c', border: '1px solid rgba(249, 115, 22, 0.2)' }}>
              <ShoppingCart size={24} />
            </div>
            <h2 style={{ fontSize: '1.875rem', fontWeight: 700 }}>Ecommerce</h2>
          </div>
          <p style={{ color: '#9ca3af', marginBottom: '1.5rem', lineHeight: 1.625 }}>
            Boost conversion with abandoned cart recovery and protect your revenue with intelligent fraud rules.
          </p>
          <CodeBlock code={ecommerceCode} />
        </section>

        <section style={{ marginBottom: '4rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', marginBottom: '2rem' }}>
            <div style={{ padding: '0.75rem', borderRadius: '0.75rem', backgroundColor: 'rgba(59, 130, 246, 0.1)', color: '#60a5fa', border: '1px solid rgba(59, 130, 246, 0.2)' }}>
              <Landmark size={24} />
            </div>
            <h2 style={{ fontSize: '1.875rem', fontWeight: 700 }}>Banking & Fintech</h2>
          </div>
          <p style={{ color: '#9ca3af', marginBottom: '1.5rem', lineHeight: 1.625 }}>
            Deliver multi-channel security alerts instantly for suspicious logins or large unauthorized transfers.
          </p>
          <CodeBlock code={bankingCode} />
        </section>
      </div>
    </div>
  );
};

export default Examples;
