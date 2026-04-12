import React from 'react';
import { motion, useInView } from 'framer-motion';

/* ── Reusable section that alternates image/text sides ── */
const FeatureSection = ({ title, description, imgSrc, imgAlt, reverse = false, index }) => {
  const ref = React.useRef(null);
  const isInView = useInView(ref, { once: true, margin: '-60px' });
  return (
    <motion.div
      ref={ref}
      animate={isInView ? { opacity: 1, y: 0 } : { opacity: 0, y: 40 }}
      initial={{ opacity: 0, y: 40 }}
      transition={{ duration: 0.6, delay: 0.05 }}
      style={{
        display: 'flex',
        flexDirection: reverse ? 'row-reverse' : 'row',
        gap: '4rem',
        alignItems: 'center',
        marginBottom: '7rem',
        flexWrap: 'wrap',
      }}
    >
      {/* Text */}
      <div style={{ flex: '1 1 320px', minWidth: 0 }}>
        <div style={{ display: 'inline-flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem' }}>
          <span style={{
            width: '1.75rem', height: '1.75rem', borderRadius: '0.4rem',
            backgroundColor: 'rgba(234,179,8,0.15)', color: '#facc15',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontSize: '0.75rem', fontWeight: 700,
          }}>
            {String(index).padStart(2, '0')}
          </span>
        </div>
        <h2 style={{ fontSize: 'clamp(1.5rem, 3vw, 2rem)', fontWeight: 700, marginBottom: '1rem', lineHeight: 1.25 }}>
          {title}
        </h2>
        <p style={{ color: '#94a3b8', lineHeight: 1.75, fontSize: '1.05rem' }}>
          {description}
        </p>
      </div>

      {/* Image */}
      <motion.div
        initial={{ opacity: 0, scale: 0.95 }}
        whileInView={{ opacity: 1, scale: 1 }}
        viewport={{ once: true }}
        transition={{ duration: 0.7, delay: 0.15 }}
        style={{ flex: '1 1 320px', minWidth: 0 }}
      >
        <div style={{
          borderRadius: '1.25rem',
          overflow: 'hidden',
          border: '1px solid rgba(255,255,255,0.08)',
          boxShadow: '0 25px 60px rgba(0,0,0,0.5)',
          position: 'relative',
        }}>
          {/* fade overlay */}
          <div style={{
            position: 'absolute', inset: 0,
            background: reverse
              ? 'linear-gradient(to right, rgba(10,10,12,0.25) 0%, transparent 50%)'
              : 'linear-gradient(to left, rgba(10,10,12,0.25) 0%, transparent 50%)',
            zIndex: 1, pointerEvents: 'none',
          }} />
          <img
            src={imgSrc}
            alt={imgAlt}
            style={{ width: '100%', display: 'block', objectFit: 'cover' }}
          />
        </div>
      </motion.div>
    </motion.div>
  );
};

/* ── Channel badge ── */
const ChannelBadge = ({ label, color, icon }) => (
  <div style={{
    display: 'flex', alignItems: 'center', gap: '0.6rem',
    padding: '0.65rem 1.1rem',
    borderRadius: '0.75rem',
    border: `1px solid ${color}33`,
    backgroundColor: `${color}11`,
    color: color,
    fontWeight: 600,
    fontSize: '0.9rem',
    whiteSpace: 'nowrap',
  }}>
    <span style={{ fontSize: '1.1rem' }}>{icon}</span>
    {label}
  </div>
);

/* ── Main Home component ── */
const Home = ({ onOpenPricing, onGoToDocs }) => {
  const features = [
    {
      title: 'Event Processing',
      description:
        'This agent decides whether to send a notification corresponding to an emitted event, based on past events, and domain experience based on episodic and long term memory.',
      img: '/event_processing.png',
      alt: 'Event Processing',
      reverse: false,
    },
    {
      title: 'Template Generation',
      description:
        'This agent generates business oriented and user oriented templates with respect to the notification channel and other instructions.',
      img: '/template_generation.png',
      alt: 'Template Generation',
      reverse: true,
    },
    {
      title: 'Scheduling',
      description:
        'This agent schedules a deferred notification with respect to logic of interval duration and repetition.',
      img: '/scheduling.png',
      alt: 'Scheduling',
      reverse: false,
    },
    {
      title: 'Rule Processing',
      description:
        'Define procedural business rules related to notification generation and dispatch in natural language, which are translated into fast executable expressions by an agent, resulting in less context overhead and improved performance.',
      img: '/rule_processing.png',
      alt: 'Rule Processing',
      reverse: true,
    },
  ];

  const channels = [
    { label: 'Email', color: '#60a5fa', icon: '✉️' },
    { label: 'SMS', color: '#4ade80', icon: '💬' },
    { label: 'Push Notification', color: '#f97316', icon: '🔔' },
    { label: 'Webhook', color: '#a78bfa', icon: '🔗' },
    { label: 'Custom Connector', color: '#facc15', icon: '🔌' },
    { label: 'In-App', color: '#f472b6', icon: '📱' },
  ];

  return (
    <div style={{ flex: 1, position: 'relative' }}>
      {/* ── Background blobs ── */}
      <div style={{ position: 'fixed', inset: 0, overflow: 'hidden', pointerEvents: 'none', zIndex: 0 }}>
        <motion.div
          animate={{ x: [0, 60, 0], y: [0, -40, 0] }}
          transition={{ duration: 20, repeat: Infinity, ease: 'linear' }}
          className="bg-blob"
          style={{ top: '-5%', left: '20%' }}
        />
        <motion.div
          animate={{ x: [0, -50, 0], y: [0, 40, 0] }}
          transition={{ duration: 25, repeat: Infinity, ease: 'linear' }}
          className="bg-blob bg-blob-secondary"
          style={{ bottom: '10%', right: '15%' }}
        />
      </div>

      <div style={{ position: 'relative', zIndex: 1 }}>
        {/* ── Hero ── */}
        <section style={{
          minHeight: '92vh',
          display: 'flex',
          alignItems: 'center',
          padding: '6rem 2rem',
          maxWidth: '1200px',
          margin: '0 auto',
          gap: '4rem',
          flexWrap: 'wrap',
        }}>
          {/* Left: text */}
          <div style={{ flex: '1 1 380px', minWidth: 0 }}>
            <motion.div
              initial={{ opacity: 0, y: 5 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.4 }}
              style={{
                display: 'inline-flex', alignItems: 'center', gap: '0.5rem',
                padding: '0.35rem 0.9rem', borderRadius: '999px',
                border: '1px solid rgba(234,179,8,0.25)',
                backgroundColor: 'rgba(234,179,8,0.08)',
                color: '#facc15', fontSize: '0.8rem', fontWeight: 600,
                marginBottom: '1.75rem', letterSpacing: '0.04em',
              }}
            >
              ✦ AI-Powered Notification Engine
            </motion.div>

            <motion.h1
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.1, duration: 0.5 }}
              style={{ fontSize: 'clamp(2.5rem, 5vw, 4rem)', fontWeight: 800, lineHeight: 1.08, marginBottom: '1.75rem', letterSpacing: '-0.02em' }}
            >
              Notifications<br />
              <span className="gradient-text">Enhanced by AI</span>
            </motion.h1>

            <motion.p
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.2 }}
              style={{ fontSize: '1.1rem', color: '#94a3b8', lineHeight: 1.75, marginBottom: '1.25rem', maxWidth: '520px' }}
            >
              Notify.ai is a notification generation and dispatch engine powered by orchestrated AI agents.
            </motion.p>

            <motion.p
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.25 }}
              style={{ fontSize: '1rem', color: '#64748b', lineHeight: 1.75, marginBottom: '2.5rem', maxWidth: '500px' }}
            >
              An event-based architecture based on event generation by a client SDK embedded in your backend service. A generated event is consumed and processed asynchronously by a team of AI agents.
            </motion.p>

            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.3 }}
              style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap' }}
            >
              <button
                id="hero-get-started"
                onClick={onGoToDocs}
                onMouseEnter={(e) => { e.currentTarget.style.transform = 'translateY(-2px)'; e.currentTarget.style.boxShadow = '0 16px 40px rgba(234,179,8,0.35)'; }}
                onMouseLeave={(e) => { e.currentTarget.style.transform = 'translateY(0)'; e.currentTarget.style.boxShadow = '0 8px 24px rgba(234,179,8,0.2)'; }}
                style={{
                  padding: '0.875rem 2rem', borderRadius: '0.75rem',
                  backgroundColor: '#eab308', color: '#000', fontWeight: 700,
                  fontSize: '0.95rem', transition: 'all 300ms', cursor: 'pointer',
                  boxShadow: '0 8px 24px rgba(234,179,8,0.2)',
                }}
              >
                Read the Docs
              </button>
              <button
                id="hero-hosted-access"
                onClick={onOpenPricing}
                onMouseEnter={(e) => { e.currentTarget.style.backgroundColor = 'rgba(255,255,255,0.08)'; e.currentTarget.style.transform = 'translateY(-2px)'; }}
                onMouseLeave={(e) => { e.currentTarget.style.backgroundColor = 'transparent'; e.currentTarget.style.transform = 'translateY(0)'; }}
                style={{
                  padding: '0.875rem 2rem', borderRadius: '0.75rem',
                  border: '1px solid rgba(255,255,255,0.12)',
                  color: '#e2e8f0', fontWeight: 600, fontSize: '0.95rem',
                  transition: 'all 300ms', cursor: 'pointer',
                }}
              >
                Hosted Access →
              </button>
            </motion.div>
          </div>

          {/* Right: architecture image */}
          <motion.div
            initial={{ opacity: 0, x: 30 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ delay: 0.35, duration: 0.6 }}
            style={{ flex: '1 1 340px', minWidth: 0 }}
          >
            <div style={{
              borderRadius: '1.5rem', overflow: 'hidden',
              border: '1px solid rgba(255,255,255,0.08)',
              boxShadow: '0 30px 80px rgba(0,0,0,0.6)',
              position: 'relative',
            }}>
              <div style={{
                position: 'absolute', inset: 0,
                background: 'linear-gradient(to left, rgba(10,10,12,0.3) 0%, transparent 60%)',
                zIndex: 1, pointerEvents: 'none',
              }} />
              <img src="/architecture.jpeg" alt="System Architecture" style={{ width: '100%', display: 'block' }} />
            </div>
          </motion.div>
        </section>

        {/* ── Feature Sections ── */}
        <section style={{ maxWidth: '1200px', margin: '0 auto', padding: '2rem 2rem 4rem' }}>
          {features.map((f, i) => (
            <FeatureSection
              key={f.title}
              title={f.title}
              description={f.description}
              imgSrc={f.img}
              imgAlt={f.alt}
              reverse={f.reverse}
              index={i + 1}
            />
          ))}

          {/* ── Configurable Connectors (special grid layout) ── */}
          <ConnectorsSection channels={channels} />
        </section>

        {/* ── DOCS section ── */}
        <section style={{ maxWidth: '1200px', margin: '0 auto', padding: '0 2rem 8rem' }}>
          <motion.div
            initial={{ opacity: 0, y: 30 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.5 }}
          >
            <h2 style={{ fontSize: 'clamp(1.75rem, 3vw, 2.25rem)', fontWeight: 700, marginBottom: '2rem', color: '#f8fafc' }}>
              Documentation
            </h2>
            <div style={{ display: 'flex', gap: '1.25rem', flexWrap: 'wrap' }}>
              {/* Client SDK card */}
              <DocCard
                title="Client SDK"
                description="Integrate Notify.ai into your Spring Boot application using the @Event, @SubjectSupplier, and @Rule annotations."
                badge="Available"
                badgeColor="#4ade80"
                onClick={onGoToDocs}
              />
              {/* Engine API card */}
              <DocCard
                title="Engine API"
                description="Direct REST API access to the notification engine, event ingestion, tenant management, and configuration endpoints."
                badge="Coming Soon"
                badgeColor="#facc15"
                onClick={null}
              />
            </div>
          </motion.div>
        </section>
      </div>
    </div>
  );
};

const DocCard = ({ title, description, badge, badgeColor, onClick }) => {
  const [hovered, setHovered] = React.useState(false);
  return (
    <div
      onClick={onClick}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      style={{
        flex: '1 1 280px', padding: '2rem', borderRadius: '1.25rem',
        border: `1px solid ${hovered && onClick ? 'rgba(234,179,8,0.3)' : 'rgba(255,255,255,0.08)'}`,
        backgroundColor: hovered && onClick ? 'rgba(234,179,8,0.04)' : 'rgba(255,255,255,0.03)',
        cursor: onClick ? 'pointer' : 'default',
        transition: 'all 300ms',
        transform: hovered && onClick ? 'translateY(-4px)' : 'translateY(0)',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1rem' }}>
        <h3 style={{ fontSize: '1.15rem', fontWeight: 700 }}>{title}</h3>
        <span style={{
          padding: '0.2rem 0.65rem', borderRadius: '999px', fontSize: '0.7rem', fontWeight: 700,
          backgroundColor: `${badgeColor}18`, color: badgeColor, border: `1px solid ${badgeColor}33`,
        }}>{badge}</span>
      </div>
      <p style={{ color: '#64748b', lineHeight: 1.7, fontSize: '0.95rem' }}>{description}</p>
      {onClick && (
        <p style={{ color: '#facc15', marginTop: '1.25rem', fontSize: '0.875rem', fontWeight: 600 }}>
          View guide →
        </p>
      )}
    </div>
  );
};

const ConnectorsSection = ({ channels }) => {
  const ref = React.useRef(null);
  const isInView = useInView(ref, { once: true, margin: '-60px' });
  return (
    <motion.div
      ref={ref}
      animate={isInView ? { opacity: 1, y: 0 } : { opacity: 0, y: 40 }}
      initial={{ opacity: 0, y: 40 }}
      transition={{ duration: 0.6 }}
      style={{ display: 'flex', flexDirection: 'row', gap: '4rem', alignItems: 'center', marginBottom: '7rem', flexWrap: 'wrap' }}
    >
      {/* Left: text */}
      <div style={{ flex: '1 1 320px', minWidth: 0 }}>
        <div style={{ display: 'inline-flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem' }}>
          <span style={{
            width: '1.75rem', height: '1.75rem', borderRadius: '0.4rem',
            backgroundColor: 'rgba(234,179,8,0.15)', color: '#facc15',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontSize: '0.75rem', fontWeight: 700,
          }}>05</span>
        </div>
        <h2 style={{ fontSize: 'clamp(1.5rem, 3vw, 2rem)', fontWeight: 700, marginBottom: '1rem', lineHeight: 1.25 }}>
          Configurable Connectors
        </h2>
        <p style={{ color: '#94a3b8', lineHeight: 1.75, fontSize: '1.05rem', marginBottom: '2rem' }}>
          Supports a variety of channels, and this support is extensible by the ability to write custom connectors which are used by the engine to dispatch notifications.
        </p>
      </div>

      {/* Right: channel grid + image */}
      <div style={{ flex: '1 1 320px', minWidth: 0 }}>
        <div style={{
          borderRadius: '1.25rem', overflow: 'hidden',
          border: '1px solid rgba(255,255,255,0.08)',
          boxShadow: '0 25px 60px rgba(0,0,0,0.5)',
          marginBottom: '1.5rem', position: 'relative',
        }}>
          <div style={{
            position: 'absolute', inset: 0,
            background: 'linear-gradient(to left, rgba(10,10,12,0.25) 0%, transparent 50%)',
            zIndex: 1, pointerEvents: 'none',
          }} />
          <img src="/connectors.png" alt="Connectors" style={{ width: '100%', display: 'block', objectFit: 'cover' }} />
        </div>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.6rem' }}>
          {channels.map((ch) => (
            <ChannelBadge key={ch.label} {...ch} />
          ))}
        </div>
      </div>
    </motion.div>
  );
};

export default Home;

