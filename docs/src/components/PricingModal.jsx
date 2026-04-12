import React, { useState } from 'react';
import { motion } from 'framer-motion';
import { X, Check, Zap, Building2, Rocket } from 'lucide-react';

const CONTACT_EMAIL = 'naik.rohan07@gmail.com'; // hardcoded recipient

const TIERS = [
  {
    id: 'starter',
    name: 'Starter',
    icon: Zap,
    color: '#4ade80',
    price: { events: '$0.002', dispatches: '$0.005' },
    limits: { events: '50K events/mo', dispatches: '20K dispatches/mo' },
    features: ['Event ingestion', 'Template generation', 'Email + SMS channels', 'Standard support'],
  },
  {
    id: 'growth',
    name: 'Growth',
    icon: Rocket,
    color: '#facc15',
    popular: true,
    price: { events: '$0.0015', dispatches: '$0.004' },
    limits: { events: '500K events/mo', dispatches: '200K dispatches/mo' },
    features: ['Everything in Starter', 'Rule processing', 'All built-in channels', 'Scheduling & deferral', 'Priority support'],
  },
  {
    id: 'enterprise',
    name: 'Enterprise',
    icon: Building2,
    color: '#a78bfa',
    price: { events: 'Custom', dispatches: 'Custom' },
    limits: { events: 'Unlimited', dispatches: 'Unlimited' },
    features: ['Everything in Growth', 'Custom connectors', 'Dedicated infra', 'SLA + uptime guarantee', 'Dedicated engineer'],
  },
];

const PricingModal = ({ onClose }) => {
  const [form, setForm] = useState({ name: '', email: '', org: '', message: '' });
  const [sending, setSending] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState('');

  const handleChange = (e) => setForm((p) => ({ ...p, [e.target.name]: e.target.value }));

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!form.name || !form.email || !form.org) { setError('Please fill in all required fields.'); return; }
    setSending(true);
    setError('');

    // Send via mailto as a fallback (opens email client with prefilled content)
    const subject = encodeURIComponent(`Notify.ai Hosted Access Inquiry – ${form.org}`);
    const body = encodeURIComponent(
      `Name: ${form.name}\nEmail: ${form.email}\nOrganisation: ${form.org}\n\nMessage:\n${form.message}`
    );
    window.location.href = `mailto:${CONTACT_EMAIL}?subject=${subject}&body=${body}`;

    setTimeout(() => {
      setSending(false);
      setSent(true);
    }, 600);
  };

  return (
    <motion.div
      key="pricing-overlay"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      style={{
        position: 'fixed', inset: 0, zIndex: 200,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        backgroundColor: 'rgba(0,0,0,0.75)',
        backdropFilter: 'blur(6px)',
        padding: '1.5rem',
        overflowY: 'auto',
      }}
      onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}
    >
      <motion.div
        initial={{ opacity: 0, scale: 0.95, y: 20 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.95, y: 20 }}
        transition={{ type: 'spring', stiffness: 280, damping: 28 }}
        style={{
          width: '100%', maxWidth: '900px',
          backgroundColor: '#0f0f12',
          borderRadius: '1.5rem',
          border: '1px solid rgba(255,255,255,0.1)',
          boxShadow: '0 40px 100px rgba(0,0,0,0.7)',
          overflow: 'hidden',
          position: 'relative',
        }}
      >
        {/* Close button */}
        <button
          id="pricing-close"
          onClick={onClose}
          style={{
            position: 'absolute', top: '1.25rem', right: '1.25rem',
            width: '2rem', height: '2rem', borderRadius: '0.5rem',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            backgroundColor: 'rgba(255,255,255,0.06)',
            border: '1px solid rgba(255,255,255,0.1)',
            color: '#94a3b8', cursor: 'pointer', transition: 'all 200ms', zIndex: 10,
          }}
          onMouseEnter={(e) => { e.currentTarget.style.backgroundColor = 'rgba(255,255,255,0.12)'; e.currentTarget.style.color = '#f8fafc'; }}
          onMouseLeave={(e) => { e.currentTarget.style.backgroundColor = 'rgba(255,255,255,0.06)'; e.currentTarget.style.color = '#94a3b8'; }}
        >
          <X size={16} />
        </button>

        <div style={{ padding: '2.5rem 2.5rem 1rem' }}>
          <h2 style={{ fontSize: '1.75rem', fontWeight: 800, marginBottom: '0.5rem' }}>
            Hosted <span style={{ color: '#facc15' }}>Access</span>
          </h2>
          <p style={{ color: '#64748b', marginBottom: '2.5rem', fontSize: '0.95rem' }}>
            Pay per event invocation and per notification dispatch. Scale as you grow.
          </p>

          {/* Pricing cards */}
          <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap', marginBottom: '3rem' }}>
            {TIERS.map((tier) => {
              const Icon = tier.icon;
              return (
                <div
                  key={tier.id}
                  style={{
                    flex: '1 1 220px', borderRadius: '1rem', padding: '1.5rem',
                    border: tier.popular
                      ? `1.5px solid ${tier.color}55`
                      : '1px solid rgba(255,255,255,0.08)',
                    backgroundColor: tier.popular ? `${tier.color}08` : 'rgba(255,255,255,0.02)',
                    position: 'relative',
                  }}
                >
                  {tier.popular && (
                    <span style={{
                      position: 'absolute', top: '-0.75rem', left: '50%', transform: 'translateX(-50%)',
                      backgroundColor: tier.color, color: '#000',
                      fontSize: '0.65rem', fontWeight: 800, letterSpacing: '0.08em',
                      padding: '0.2rem 0.7rem', borderRadius: '999px', whiteSpace: 'nowrap',
                    }}>MOST POPULAR</span>
                  )}

                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', marginBottom: '1rem' }}>
                    <div style={{
                      width: '2rem', height: '2rem', borderRadius: '0.5rem',
                      backgroundColor: `${tier.color}18`, display: 'flex', alignItems: 'center', justifyContent: 'center',
                      color: tier.color,
                    }}>
                      <Icon size={16} />
                    </div>
                    <span style={{ fontWeight: 700, fontSize: '1rem' }}>{tier.name}</span>
                  </div>

                  {/* Pricing details */}
                  <div style={{ marginBottom: '1.25rem', padding: '0.875rem', borderRadius: '0.6rem', backgroundColor: 'rgba(0,0,0,0.3)' }}>
                    <PriceLine label="Event invocation" value={tier.price.events} limit={tier.limits.events} color={tier.color} />
                    <PriceLine label="Notification dispatch" value={tier.price.dispatches} limit={tier.limits.dispatches} color={tier.color} last />
                  </div>

                  {/* Feature list */}
                  <ul style={{ listStyle: 'none', display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                    {tier.features.map((f) => (
                      <li key={f} style={{ display: 'flex', alignItems: 'flex-start', gap: '0.5rem', fontSize: '0.82rem', color: '#94a3b8' }}>
                        <Check size={13} color={tier.color} style={{ flexShrink: 0, marginTop: '2px' }} />
                        {f}
                      </li>
                    ))}
                  </ul>
                </div>
              );
            })}
          </div>

          {/* Contact form */}
          <div style={{ borderTop: '1px solid rgba(255,255,255,0.08)', paddingTop: '2rem' }}>
            <h3 style={{ fontSize: '1.1rem', fontWeight: 700, marginBottom: '0.4rem' }}>Get in touch</h3>
            <p style={{ color: '#64748b', fontSize: '0.875rem', marginBottom: '1.75rem' }}>
              Interested in hosted access? Leave your details and we'll reach out.
            </p>

            {sent ? (
              <motion.div
                initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}
                style={{
                  padding: '1.25rem', borderRadius: '0.75rem',
                  backgroundColor: 'rgba(74,222,128,0.08)',
                  border: '1px solid rgba(74,222,128,0.25)',
                  color: '#4ade80', textAlign: 'center', fontWeight: 600,
                }}
              >
                ✓ Your email client has opened with the prefilled message. We'll be in touch soon!
              </motion.div>
            ) : (
              <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '0.875rem' }}>
                <div style={{ display: 'flex', gap: '0.875rem', flexWrap: 'wrap' }}>
                  <FormField id="form-name" label="Name *" name="name" value={form.name} onChange={handleChange} placeholder="Jane Smith" />
                  <FormField id="form-email" label="Email *" name="email" type="email" value={form.email} onChange={handleChange} placeholder="jane@acme.com" />
                </div>
                <FormField id="form-org" label="Organisation *" name="org" value={form.org} onChange={handleChange} placeholder="Acme Corp" full />
                <FormField id="form-message" label="Message" name="message" value={form.message} onChange={handleChange} placeholder="Tell us about your use case..." textarea full />

                {error && <p style={{ color: '#f87171', fontSize: '0.85rem' }}>{error}</p>}

                <button
                  id="pricing-submit"
                  type="submit"
                  disabled={sending}
                  onMouseEnter={(e) => { if (!sending) e.currentTarget.style.backgroundColor = '#f59e0b'; }}
                  onMouseLeave={(e) => { if (!sending) e.currentTarget.style.backgroundColor = '#eab308'; }}
                  style={{
                    padding: '0.875rem 2rem', alignSelf: 'flex-start',
                    borderRadius: '0.75rem', backgroundColor: '#eab308',
                    color: '#000', fontWeight: 700, fontSize: '0.9rem',
                    cursor: sending ? 'not-allowed' : 'pointer',
                    opacity: sending ? 0.7 : 1, transition: 'all 200ms',
                    marginTop: '0.25rem',
                  }}
                >
                  {sending ? 'Opening email client…' : 'Send Inquiry →'}
                </button>
              </form>
            )}
          </div>
        </div>
        <div style={{ height: '2.5rem' }} />
      </motion.div>
    </motion.div>
  );
};

const PriceLine = ({ label, value, limit, color, last }) => (
  <div style={{ paddingBottom: last ? 0 : '0.6rem', marginBottom: last ? 0 : '0.6rem', borderBottom: last ? 'none' : '1px solid rgba(255,255,255,0.06)' }}>
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: '0.2rem' }}>
      <span style={{ fontSize: '0.75rem', color: '#64748b' }}>{label}</span>
      <span style={{ fontSize: '1rem', fontWeight: 800, color }}>{value}</span>
    </div>
    <span style={{ fontSize: '0.7rem', color: '#475569' }}>{limit}</span>
  </div>
);

const FormField = ({ id, label, name, type = 'text', value, onChange, placeholder, textarea, full }) => (
  <div style={{ flex: full ? '1 1 100%' : '1 1 200px', minWidth: 0, display: 'flex', flexDirection: 'column', gap: '0.35rem' }}>
    <label htmlFor={id} style={{ fontSize: '0.8rem', fontWeight: 600, color: '#94a3b8' }}>{label}</label>
    {textarea ? (
      <textarea
        id={id} name={name} value={value} onChange={onChange} placeholder={placeholder} rows={3}
        style={{
          padding: '0.65rem 0.875rem', borderRadius: '0.6rem', resize: 'vertical',
          backgroundColor: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.1)',
          color: '#f8fafc', fontSize: '0.875rem', outline: 'none', fontFamily: 'inherit', lineHeight: 1.6,
        }}
      />
    ) : (
      <input
        id={id} name={name} type={type} value={value} onChange={onChange} placeholder={placeholder}
        style={{
          padding: '0.65rem 0.875rem', borderRadius: '0.6rem',
          backgroundColor: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.1)',
          color: '#f8fafc', fontSize: '0.875rem', outline: 'none', fontFamily: 'inherit',
        }}
      />
    )}
  </div>
);

export default PricingModal;
