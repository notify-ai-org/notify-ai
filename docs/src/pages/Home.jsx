import React from 'react';
import { motion, useInView } from 'framer-motion';

/* ── Reusable section that alternates image/text sides ── */
const FeatureSection = ({ title, bullets, imgSrc, imgAlt, reverse = false, index, tight = false }) => {
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
        gap: '2rem',
        alignItems: 'center',
        justifyContent: tight ? 'flex-end' : 'flex-start',
        marginBottom: '5rem',
        flexWrap: 'wrap',
      }}
    >
      {/* Text */}
      <div style={{ flex: '1 1 300px', minWidth: 0, maxWidth: tight ? '440px' : 'none' }}>
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
        <ul style={{ listStyle: 'none', padding: 0, margin: 0, display: 'flex', flexDirection: 'column', gap: '0.6rem' }}>
          {bullets.map((b, i) => (
            <li key={i} style={{ display: 'flex', alignItems: 'flex-start', gap: '0.6rem', color: '#94a3b8', lineHeight: 1.7, fontSize: '1rem' }}>
              <span style={{ flexShrink: 0, marginTop: '0.35rem', width: '5px', height: '5px', borderRadius: '50%', backgroundColor: '#facc15', display: 'inline-block' }} />
              {b}
            </li>
          ))}
        </ul>
      </div>

      {/* Image */}
      <motion.div
        initial={{ opacity: 0, scale: 0.95 }}
        whileInView={{ opacity: 1, scale: 1 }}
        viewport={{ once: true }}
        transition={{ duration: 0.7, delay: 0.15 }}
        style={{ flex: '1 1 280px', minWidth: 0, maxWidth: '400px' }}
      >
        <div style={{
          borderRadius: '1rem',
          overflow: 'hidden',
          border: '1px solid rgba(234,179,8,0.18)',
          boxShadow: '0 16px 48px rgba(0,0,0,0.5)',
          position: 'relative',
        }}>
          {/* golden tint overlay */}
          <div style={{
            position: 'absolute', inset: 0,
            background: 'linear-gradient(135deg, rgba(234,179,8,0.18) 0%, rgba(251,146,60,0.10) 100%)',
            zIndex: 1, pointerEvents: 'none',
            mixBlendMode: 'color',
          }} />
          {/* edge fade */}
          <div style={{
            position: 'absolute', inset: 0,
            background: reverse
              ? 'linear-gradient(to right, rgba(10,10,12,0.3) 0%, transparent 55%)'
              : 'linear-gradient(to left, rgba(10,10,12,0.3) 0%, transparent 55%)',
            zIndex: 2, pointerEvents: 'none',
          }} />
          <img
            src={imgSrc}
            alt={imgAlt}
            style={{
              width: '100%', display: 'block', objectFit: 'cover',
              filter: 'sepia(0.55) saturate(1.8) hue-rotate(5deg) brightness(0.92)',
            }}
          />
        </div>
      </motion.div>
    </motion.div>
  );
};
/* ── Main Home component ── */
const Home = ({ onOpenPricing, onGoToDocs }) => {
  const features = [
    {
      title: 'Event Processing',
      bullets: [
        'Decides whether to send a notification for each emitted event.',
        'Leverages episodic memory to factor in past event history.',
        'Uses long-term domain knowledge for context-aware decisions.',
      ],
      img: '/event_processing.png',
      alt: 'Event Processing',
      reverse: false,
    },
    {
      title: 'Template Generation',
      bullets: [
        'Produces business-oriented and user-oriented message templates.',
        'Adapts tone and format to the target notification channel.',
        'Respects custom instructions passed alongside the event.',
      ],
      img: '/template_generation.png',
      alt: 'Template Generation',
      reverse: true,
    },
    {
      title: 'Scheduling',
      bullets: [
        'Schedules deferred notifications with configurable delay logic.',
        'Supports repeat intervals, day-of-week windows, and time-of-day ranges.',
        'Immediate, delayed, and CRON-style dispatch strategies.',
      ],
      img: '/scheduling.png',
      alt: 'Scheduling',
      reverse: false,
    },
    {
      title: 'Rule Processing',
      bullets: [
        'Define business rules in plain natural language.',
        'Rules are translated into fast executable expressions by an AI agent.',
        'Reduces context overhead and improves notification dispatch performance.',
      ],
      img: '/rule_processing.png',
      alt: 'Rule Processing',
      reverse: true,
    },
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
              bullets={f.bullets}
              imgSrc={f.img}
              imgAlt={f.alt}
              reverse={f.reverse}
              index={i + 1}
              tight={!f.reverse}
            />
          ))}

          {/* ── Configurable Connectors (special grid layout) ── */}
          <ConnectorsSection />
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

const ConnectorsSection = () => {
  const ref = React.useRef(null);
  const isInView = useInView(ref, { once: true, margin: '-60px' });

  const channels = [
    { label: 'Email',            icon: '✉️' },
    { label: 'SMS',              icon: '💬' },
    { label: 'Push Notification',icon: '🔔' },
    { label: 'Webhook',          icon: '🔗' },
    { label: 'In-App',           icon: '📱' },
    { label: 'Custom Connector', icon: '🔌' },
  ];

  return (
    <motion.div
      ref={ref}
      animate={isInView ? { opacity: 1, y: 0 } : { opacity: 0, y: 40 }}
      initial={{ opacity: 0, y: 40 }}
      transition={{ duration: 0.6 }}
      style={{ display: 'flex', flexDirection: 'row', gap: '2rem', alignItems: 'center', justifyContent: 'flex-end', marginBottom: '5rem', flexWrap: 'wrap' }}
    >
      {/* Left: text */}
      <div style={{ flex: '1 1 300px', minWidth: 0, maxWidth: '440px' }}>
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
        <ul style={{ listStyle: 'none', padding: 0, margin: 0, display: 'flex', flexDirection: 'column', gap: '0.6rem' }}>
          {channels.map((ch) => (
            <li key={ch.label} style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', color: '#94a3b8', lineHeight: 1.7, fontSize: '1rem' }}>
              <span style={{ flexShrink: 0, width: '5px', height: '5px', borderRadius: '50%', backgroundColor: '#facc15', display: 'inline-block' }} />
              <span style={{ marginRight: '0.35rem' }}>{ch.icon}</span>
              {ch.label}
            </li>
          ))}
        </ul>
      </div>

      {/* Right: image */}
      <div style={{ flex: '1 1 280px', minWidth: 0, maxWidth: '400px' }}>
        <div style={{
          borderRadius: '1rem',
          overflow: 'hidden',
          border: '1px solid rgba(234,179,8,0.18)',
          boxShadow: '0 16px 48px rgba(0,0,0,0.5)',
          position: 'relative',
        }}>
          {/* golden tint overlay */}
          <div style={{
            position: 'absolute', inset: 0,
            background: 'linear-gradient(135deg, rgba(234,179,8,0.18) 0%, rgba(251,146,60,0.10) 100%)',
            zIndex: 1, pointerEvents: 'none',
            mixBlendMode: 'color',
          }} />
          {/* edge fade */}
          <div style={{
            position: 'absolute', inset: 0,
            background: 'linear-gradient(to left, rgba(10,10,12,0.3) 0%, transparent 55%)',
            zIndex: 2, pointerEvents: 'none',
          }} />
          <img
            src="/connectors.png"
            alt="Connectors"
            style={{
              width: '100%', display: 'block', objectFit: 'cover',
              filter: 'sepia(0.55) saturate(1.8) hue-rotate(5deg) brightness(0.92)',
            }}
          />
        </div>
      </div>
    </motion.div>
  );
};

export default Home;

