import React, { useState, useEffect, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { 
  Terminal, 
  Database, 
  Cpu, 
  Smartphone, 
  Zap, 
  CheckCircle2, 
  AlertCircle,
  Activity,
  User,
  History
} from 'lucide-react';

const EVENT_TYPES = [
  { 
    id: 'view_product', 
    label: 'Product Viewed', 
    emoji: '👀',
    facts: { last_viewed: 'MacBook Pro M3', interest: 'High-end Laptops' },
    reasoning: [
      'Received event: PRODUCT_VIEWED',
      'Context: User has viewed MacBook Pro M3 twice in 24h',
      'Fact Extraction: interest=computing, price_bracket=premium',
      'Memory Update: Incrementing interest score for "Apple Hardware"',
      'Wait-and-see: No notification needed yet. Observing for intent.'
    ]
  },
  { 
    id: 'add_to_cart', 
    label: 'Add to Cart', 
    emoji: '🛒',
    facts: { cart_value: '$2,499', status: 'Pending Purchase' },
    reasoning: [
      'Received event: ADD_TO_CART',
      'Extracting Facts: value=2499.00, currency=USD',
      'Memory Check: User historically converts within 2 hours of carting',
      'Decision: Suppressing immediate push. Setting 15m abandon timer.',
      'Notification Prepared: "Incentive-based nudge" if no conversion.'
    ],
    notification: { title: 'Flash Deal! ⚡️', body: 'Complete your MacBook order in 10 mins for free shipping!' }
  },
  { 
    id: 'support_ticket', 
    label: 'Support Request', 
    emoji: '🛠️',
    facts: { tone: 'Frustrated', topic: 'Delivery Delay' },
    reasoning: [
      'Received event: SUPPORT_TICKET_CREATED',
      'Sentiment Analysis: frustrated (confidence 0.89)',
      'Topic Classification: logistics_delay',
      'Memory Update: User had a previous delay in Nov 2023',
      'Priority Override: Boosting urgency to "Immediate"',
      'Action: Dispatching proactive apology email with tracking fix.'
    ],
    notification: { title: 'We\'re on it! 🛠️', body: 'Support agent assigned. We\'re prioritizing your delivery inquiry.' }
  }
];

const Playground = () => {
  const [logs, setLogs] = useState([{ text: 'SYSTEM: Ready to process events...', type: 'system' }]);
  const [facts, setFacts] = useState({});
  const [memory, setMemory] = useState({ transactions: 5, lifetime_value: '$12k', loyalty_tier: 'Gold' });
  const [currentNotification, setCurrentNotification] = useState(null);
  const [isProcessing, setIsProcessing] = useState(false);
  const logEndRef = useRef(null);

  useEffect(() => {
    logEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [logs]);

  const emitEvent = async (event) => {
    if (isProcessing) return;
    setIsProcessing(true);
    setCurrentNotification(null);
    
    // Clear old reasoning from "active" state
    setLogs(prev => [...prev, { text: `> Emitting event: ${event.label}`, type: 'event' }]);

    for (const step of event.reasoning) {
      await new Promise(r => setTimeout(r, 600));
      setLogs(prev => [...prev, { text: step, type: 'reasoning' }]);
    }

    setFacts(prev => ({ ...prev, ...event.facts }));
    
    if (event.id === 'view_product') {
        setMemory(prev => ({ ...prev, top_interest: 'Hardware' }));
    }
    
    if (event.notification) {
      await new Promise(r => setTimeout(r, 1000));
      setCurrentNotification(event.notification);
    }

    setIsProcessing(false);
  };

  return (
    <section className="glass" style={{ marginTop: '8rem', padding: '3rem', borderRadius: '40px', border: '1px solid rgba(255, 255, 255, 0.1)', position: 'relative', overflow: 'hidden' }}>
      <div style={{ position: 'absolute', top: 0, right: 0, padding: '3rem', opacity: 0.05, pointerEvents: 'none' }}>
        <Zap size={120} color="#facc15" />
      </div>

      <div style={{ maxWidth: '56rem', margin: '0 auto', textAlign: 'center', marginBottom: '4rem' }}>
        <h2 style={{ fontSize: '3rem', fontWeight: 800, marginBottom: '1.5rem', color: 'var(--text-main)' }}>
          <span style={{ color: 'var(--text-muted)', fontWeight: 'normal' }}>Interactive</span> Playground
        </h2>
        <p style={{ fontSize: '1.25rem', color: 'var(--text-muted)' }}>See how Notify AI extracts facts, builds memory, and orchestrates notifications in real-time.</p>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(12, minmax(0, 1fr))', gap: '2rem', alignItems: 'flex-start' }}>
        
        {/* Left Column: Events & Terminal */}
        <div style={{ gridColumn: 'span 8 / span 8', display: 'flex', flexDirection: 'column', gap: '2rem' }}>
          
          <div style={{ padding: '1.5rem', borderRadius: '1.5rem', backgroundColor: 'rgba(255, 255, 255, 0.05)', border: '1px solid rgba(255, 255, 255, 0.1)' }}>
            <h3 style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontWeight: 700, marginBottom: '1.5rem', color: '#facc15' }}>
              <Activity size={20} />
              1. Emit Event
            </h3>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '1rem' }}>
              {EVENT_TYPES.map(event => (
                <button
                  key={event.id}
                  onClick={() => emitEvent(event)}
                  disabled={isProcessing}
                  onMouseEnter={(e) => {
                    if (!isProcessing) {
                      e.currentTarget.style.borderColor = 'rgba(234, 179, 8, 0.5)';
                      e.currentTarget.style.backgroundColor = 'rgba(234, 179, 8, 0.05)';
                      e.currentTarget.style.transform = 'scale(1.05)';
                    }
                  }}
                  onMouseLeave={(e) => {
                    if (!isProcessing) {
                      e.currentTarget.style.borderColor = 'rgba(255, 255, 255, 0.1)';
                      e.currentTarget.style.backgroundColor = 'rgba(255, 255, 255, 0.05)';
                      e.currentTarget.style.transform = 'scale(1)';
                    }
                  }}
                  onMouseDown={(e) => { if (!isProcessing) e.currentTarget.style.transform = 'scale(0.95)'; }}
                  onMouseUp={(e) => { if (!isProcessing) e.currentTarget.style.transform = 'scale(1.05)'; }}
                  style={{
                    display: 'flex', alignItems: 'center', gap: '0.75rem', padding: '1rem 1.5rem', borderRadius: '1rem', border: '1px solid',
                    transition: 'all 300ms', cursor: isProcessing ? 'not-allowed' : 'pointer',
                    opacity: isProcessing ? 0.5 : 1,
                    borderColor: isProcessing ? 'rgba(255, 255, 255, 0.05)' : 'rgba(255, 255, 255, 0.1)',
                    backgroundColor: 'rgba(255, 255, 255, 0.05)'
                  }}
                >
                  <span style={{ fontSize: '1.5rem' }}>{event.emoji}</span>
                  <span style={{ fontWeight: 600 }}>{event.label}</span>
                </button>
              ))}
            </div>
          </div>

          <div className="terminal-base" style={{ display: 'flex', flexDirection: 'column' }}>
            <div className="terminal-header">
              <div className="terminal-dot" style={{ backgroundColor: 'rgba(239, 68, 68, 0.5)' }} />
              <div className="terminal-dot" style={{ backgroundColor: 'rgba(234, 179, 8, 0.5)' }} />
              <div className="terminal-dot" style={{ backgroundColor: 'rgba(34, 197, 94, 0.5)' }} />
              <span style={{ marginLeft: '0.5rem', fontSize: '0.75rem', color: '#6b7280', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <Terminal size={12} /> agent_reasoning_engine.log
              </span>
            </div>
            <div className="terminal-content hide-scrollbar">
              {logs.map((log, i) => (
                <div 
                  key={i} 
                  className="reasoning-step"
                  style={{ color: log.type === 'event' ? '#facc15' : (log.type === 'system' ? 'var(--text-muted)' : undefined), fontWeight: log.type === 'event' ? 'bold' : 'normal', opacity: log.type === 'system' ? 0.5 : 1, fontStyle: log.type === 'system' ? 'italic' : 'normal' }}
                >
                  <span style={{ opacity: 0.3 }}>{i + 1}</span>
                  <span style={{ color: 'var(--text-main)' }}>{log.text}</span>
                </div>
              ))}
              {isProcessing && (
                <div className="reasoning-step active">
                  <span style={{ opacity: 0.3 }}>{logs.length + 1}</span>
                  <span style={{ animation: 'pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite' }}>_ Thinking...</span>
                </div>
              )}
              <div ref={logEndRef} />
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '2rem' }}>
            <div style={{ padding: '1.5rem', borderRadius: '1.5rem', backgroundColor: 'rgba(255, 255, 255, 0.05)', border: '1px solid rgba(255, 255, 255, 0.1)' }}>
              <h3 style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontWeight: 700, marginBottom: '1rem', color: '#fb923c', fontSize: '0.875rem', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                <Database size={16} />
                Extracted Facts
              </h3>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.5rem' }}>
                {Object.entries(facts).length === 0 ? (
                  <span style={{ color: '#4b5563', fontStyle: 'italic', fontSize: '0.875rem' }}>Waiting for event...</span>
                ) : (
                  Object.entries(facts).map(([k, v]) => (
                    <div key={k} className="fact-tag">
                      {k}: {v}
                    </div>
                  ))
                )}
              </div>
            </div>
            <div style={{ padding: '1.5rem', borderRadius: '1.5rem', backgroundColor: 'rgba(255, 255, 255, 0.05)', border: '1px solid rgba(255, 255, 255, 0.1)' }}>
              <h3 style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontWeight: 700, marginBottom: '1rem', color: '#fb923c', fontSize: '0.875rem', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                <User size={16} />
                User Memory
              </h3>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                {Object.entries(memory).map(([k, v]) => (
                  <div key={k} style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.875rem' }}>
                    <span style={{ color: 'var(--text-muted)' }}>{k.replace('_', ' ')}</span>
                    <span style={{ color: 'var(--text-main)', fontWeight: 500 }}>{v}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>

        </div>

        {/* Right Column: Device Mockup */}
        <div style={{ gridColumn: 'span 4 / span 4', display: 'flex', justifyContent: 'center' }}>
          <div className="device-mockup">
            <div className="scanline" />
            <div className="device-screen">
              <div style={{ width: '5rem', height: '0.25rem', borderRadius: '9999px', backgroundColor: 'rgba(255, 255, 255, 0.1)', alignSelf: 'center', position: 'absolute', top: '1rem' }} />
              
              <AnimatePresence>
                {currentNotification && (
                  <motion.div
                    initial={{ y: -50, opacity: 0, scale: 0.9 }}
                    animate={{ y: 0, opacity: 1, scale: 1 }}
                    exit={{ y: -50, opacity: 0 }}
                    className="notification-banner"
                  >
                    <div style={{ width: '2.5rem', height: '2.5rem', borderRadius: '0.75rem', backgroundColor: '#eab308', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#000', flexShrink: 0 }}>
                      <Zap size={20} />
                    </div>
                    <div>
                      <h4 style={{ fontSize: '0.75rem', fontWeight: 700, marginBottom: '0.25rem' }}>NOTIFY AI</h4>
                      <p style={{ fontSize: '0.875rem', lineHeight: 1.25, color: '#1f2937' }}>{currentNotification.title}</p>
                      <p style={{ fontSize: '0.75rem', color: '#4b5563', marginTop: '0.25rem' }}>{currentNotification.body}</p>
                    </div>
                  </motion.div>
                )}
              </AnimatePresence>

              {!currentNotification && !isProcessing && (
                <div style={{ marginTop: 'auto', marginBottom: '2.5rem', textAlign: 'center', padding: '0 1.5rem' }}>
                  <p style={{ color: 'rgba(255, 255, 255, 0.3)', fontSize: '0.875rem' }}>Waiting for incoming signal...</p>
                </div>
              )}
            </div>
          </div>
        </div>

      </div>
    </section>
  );
};

export default Playground;
