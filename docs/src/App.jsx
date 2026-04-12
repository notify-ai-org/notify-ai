import React, { useState, useEffect } from 'react';
import Layout from './components/Layout';
import Home from './pages/Home';
import SDKGuide from './pages/SDKGuide';
import PricingModal from './components/PricingModal';
import { MotionConfig, motion, AnimatePresence } from 'framer-motion';
import { Github } from 'lucide-react';

const GITHUB_URL = 'https://github.com/rohan-naik07/notify-ai';
const EXAMPLES_URL = 'https://github.com/rohan-naik07/notify-ai/tree/main/examples';

function App() {
  const [activeTab, setActiveTab] = useState('home');
  const [showPricing, setShowPricing] = useState(false);

  const navItems = [
    { id: 'docs', label: 'Docs', action: () => setActiveTab('guide') },
    { id: 'examples', label: 'Examples', action: () => window.open(EXAMPLES_URL, '_blank') },
    { id: 'github', label: 'GitHub', action: () => window.open(GITHUB_URL, '_blank'), icon: Github },
    { id: 'pricing', label: 'Hosted Access', action: () => setShowPricing(true), highlight: true },
  ];

  return (
    <MotionConfig reducedMotion="user">
      <Layout>
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: '100vh', position: 'relative' }}>
          {/* Nav */}
          <header className="glass" style={{
            position: 'sticky', top: 0, zIndex: 50,
            borderBottom: '1px solid rgba(255,255,255,0.08)',
            padding: '0 2rem',
            display: 'flex', alignItems: 'center', justifyContent: 'space-between',
            height: '64px',
          }}>
            {/* Logo */}
            <button
              onClick={() => setActiveTab('home')}
              style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', cursor: 'pointer' }}
            >
              <span style={{ fontSize: '1.25rem', fontWeight: 800, letterSpacing: '-0.02em' }}>
                notify<span style={{ color: '#eab308' }}>.ai</span>
              </span>
            </button>

            {/* Nav links */}
            <nav style={{ display: 'flex', alignItems: 'center', gap: '0.25rem' }}>
              {navItems.map((item) => {
                const isActive = (item.id === 'docs' && activeTab === 'guide') || (item.id === 'home' && activeTab === 'home');
                return (
                  <button
                    key={item.id}
                    id={`nav-${item.id}`}
                    onClick={item.action}
                    style={{
                      display: 'flex', alignItems: 'center', gap: '0.4rem',
                      padding: '0.5rem 1rem', borderRadius: '0.5rem',
                      fontSize: '0.9rem', fontWeight: item.highlight ? 700 : 500,
                      cursor: 'pointer', transition: 'all 200ms',
                      color: item.highlight ? '#000' : isActive ? '#facc15' : '#9ca3af',
                      backgroundColor: item.highlight ? '#eab308' : 'transparent',
                    }}
                    onMouseEnter={(e) => {
                      if (!item.highlight) {
                        e.currentTarget.style.color = '#f3f4f6';
                        e.currentTarget.style.backgroundColor = 'rgba(255,255,255,0.06)';
                      } else {
                        e.currentTarget.style.backgroundColor = '#f59e0b';
                      }
                    }}
                    onMouseLeave={(e) => {
                      if (!item.highlight) {
                        e.currentTarget.style.color = isActive ? '#facc15' : '#9ca3af';
                        e.currentTarget.style.backgroundColor = 'transparent';
                      } else {
                        e.currentTarget.style.backgroundColor = '#eab308';
                      }
                    }}
                  >
                    {item.icon && <item.icon size={15} />}
                    {item.label}
                  </button>
                );
              })}
            </nav>
          </header>

          <main style={{ flex: 1, display: 'flex', flexDirection: 'column', position: 'relative' }}>
            <AnimatePresence mode="wait">
              {activeTab === 'home' ? (
                <motion.div key="home" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.2 }}>
                  <Home onOpenPricing={() => setShowPricing(true)} onGoToDocs={() => setActiveTab('guide')} />
                </motion.div>
              ) : (
                <motion.div key="guide" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.2 }}>
                  <SDKGuide />
                </motion.div>
              )}
            </AnimatePresence>
          </main>
        </div>

        {/* Pricing Modal */}
        <AnimatePresence>
          {showPricing && <PricingModal onClose={() => setShowPricing(false)} />}
        </AnimatePresence>
      </Layout>
    </MotionConfig>
  );
}

export default App;
