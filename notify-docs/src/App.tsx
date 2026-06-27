import { useState, useEffect, useRef, type FormEvent } from 'react';
import { marked } from 'marked';
import {
  Search,
  Sun,
  Moon,
  Menu,
  ChevronRight,
  Home,
  LogIn
} from 'lucide-react';
import { motion } from 'framer-motion';
import { docsRegistry, allDocs } from './docs/docsRegistry';

interface TOCItem {
  id: string;
  text: string;
  level: 'H2' | 'H3';
}

interface LandingPageProps {
  selectDoc: (id: string) => void;
  searchQuery: string;
  setSearchQuery: (q: string) => void;
  filteredRegistry: any[];
  theme: 'dark' | 'light';
}

const ADMIN_DEMO_EMAIL = 'rohan.notify.admin1203@gmail.com';


function DemoRequestModal({ onClose }: { onClose: () => void }) {
  const [clientEmail, setClientEmail] = useState('');
  const [note, setNote] = useState('');

  const submitRequest = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    const subject = 'Notify.ai demo request';
    const body = [
      'A client requested a Notify.ai demo.',
      '',
      `Client email: ${clientEmail}`,
      '',
      'Note:',
      note || 'No note provided.'
    ].join('\n');

    window.location.href = `mailto:${ADMIN_DEMO_EMAIL}?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(body)}`;
    onClose();
  };

  return (
    <div className="pricing-modal-overlay" onClick={onClose}>
      <div className="demo-modal-card animate-fade-in" onClick={event => event.stopPropagation()}>
        <div className="pricing-modal-header">
          <div>
            <h2>Request demo</h2>
            <p className="demo-modal-kicker">Tell us where to reach you.</p>
          </div>
          <button className="pricing-modal-close" onClick={onClose} aria-label="Close request demo modal">
            &times;
          </button>
        </div>

        <form className="demo-form" onSubmit={submitRequest}>
          <label className="demo-form-field">
            <span>Client email</span>
            <input
              required
              type="email"
              value={clientEmail}
              onChange={event => setClientEmail(event.target.value)}
              placeholder="client@company.com"
            />
          </label>

          <label className="demo-form-field">
            <span>Note</span>
            <textarea
              rows={5}
              value={note}
              onChange={event => setNote(event.target.value)}
              placeholder="What should we cover in the demo?"
            />
          </label>

          <div className="demo-modal-actions">
            <button type="button" className="docs-secondary-btn" onClick={onClose}>
              Cancel
            </button>
            <button type="submit" className="docs-primary-btn">
              Send request
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

function LandingPage({
  selectDoc,
  searchQuery,
  setSearchQuery,
  filteredRegistry,
  theme
}: LandingPageProps) {
  const isDark = theme === 'dark';
  const [pricingOpen, setPricingOpen] = useState(false);
  if (searchQuery.trim() !== '') {
    const results = filteredRegistry.flatMap(category => category.items);
    return (
      <div className="landing-container animate-fade-in">
        <section className="search-results-section">
          <h2 className="landing-section-title">Search Results for "{searchQuery}"</h2>
          <div className="search-results-list">
            {results.map(item => {
              const cleanContent = item.content.replace(/[#`*_\-]/g, ' ').replace(/\s+/g, ' ');
              const snippet = cleanContent.length > 200 ? cleanContent.substring(0, 200) + '...' : cleanContent;
              return (
                <div key={item.id} className="search-result-card" onClick={() => { selectDoc(item.id); setSearchQuery(''); }}>
                  <div className="search-result-card-header">
                    <span className="result-category">{item.category}</span>
                    <span className="landing-card-link">Read Guide &rarr;</span>
                  </div>
                  <h3>{item.title}</h3>
                  <p className="result-snippet">{snippet}</p>
                </div>
              );
            })}
            {results.length === 0 && (
              <div className="no-results-message">
                No articles match your query. Try searching for different keywords (e.g., "AOP", "MySQL", "Kafka").
              </div>
            )}
          </div>
          <button className="clear-search-btn" onClick={() => setSearchQuery('')}>Clear Search</button>
        </section>
      </div>
    );
  }

  return (
    <div className="landing-container animate-fade-in">
      {/* Hero Header */}

      <section className="landing-hero">


        <section style={{ minHeight: '92vh', display: 'flex', alignItems: 'center', justifyContent: 'center', maxWidth: '1200px', margin: '0 auto', position: 'relative' }}>
          {/* Hero text wrapper with integrated background image */}
          <div className="hero-text-wrapper" style={{
            position: 'relative',
            zIndex: 10,
            width: '100%',
            maxWidth: '840px',
            padding: 15,
            borderRadius: '1.5rem',
            border: '1px solid hsl(var(--border-color))',
            backgroundColor: isDark ? 'hsla(var(--bg-card) / 0.15)' : 'hsla(var(--bg-card) / 0.7)',
            backdropFilter: 'blur(12px)',
            boxShadow: isDark ? '0 20px 50px rgba(0, 0, 0, 0.4)' : '0 20px 50px rgba(0, 0, 0, 0.1)',
            overflow: 'hidden',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            textAlign: 'center'
          }}>
            {/* Gradient overlay — dark tint in dark mode, subtle light tint in light mode */}
            <div style={{
              position: 'absolute',
              inset: 0,
              background: isDark
                ? 'linear-gradient(135deg, rgba(0,0,0,0.82) 0%, rgba(0,0,0,0.55) 60%, rgba(0,0,0,0.75) 100%)'
                : 'linear-gradient(135deg, rgba(255,255,255,0.6) 0%, rgba(255,255,255,0.35) 60%, rgba(255,255,255,0.55) 100%)',
              zIndex: -1,
              pointerEvents: 'none'
            }} />

            <motion.div initial={{ opacity: 0, y: 5 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.4 }}
              style={{ display: 'inline-flex', alignItems: 'center', gap: '0.5rem', padding: '0.35rem 0.9rem', borderRadius: '999px', border: '1px solid rgba(234,179,8,0.35)', backgroundColor: 'rgba(234,179,8,0.12)', color: isDark ? '#facc15' : '#92680a', fontSize: '0.8rem', fontWeight: 600, marginBottom: '1.75rem', letterSpacing: '0.04em' }}>
              ✦ AI-Powered Notification Engine
            </motion.div>
            <motion.h1 initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1, duration: 0.5 }}
              style={{ fontSize: 'clamp(2.5rem, 5vw, 4rem)', fontWeight: 800, lineHeight: 1.08, marginBottom: '1.75rem', letterSpacing: '-0.02em' }}>
              Notifications<br /><span className="gradient-text">Enhanced by AI</span>
            </motion.h1>
            <motion.p initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.2 }}
              style={{ fontSize: '1.1rem', color: isDark ? '#94a3b8' : '#475569', lineHeight: 1.75, marginBottom: '1.25rem', maxWidth: '560px', margin: '0 auto 1.25rem' }}>
              Notify.ai is a notification generation and dispatch engine powered by orchestrated AI agents.
            </motion.p>
            <motion.p initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.25 }}
              style={{ fontSize: '1rem', color: isDark ? '#64748b' : '#64748b', lineHeight: 1.75, marginBottom: '2.5rem', maxWidth: '540px', margin: '0 auto 2.5rem' }}>
              An event-based architecture based on event generation by a client SDK embedded in your backend service. A generated event is consumed and processed asynchronously by a team of AI agents.
            </motion.p>
            <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.3 }} style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap', justifyContent: 'center' }}>
              <button id="hero-get-started"
                onClick={() => selectDoc('intro')}
                onMouseEnter={e => { (e.currentTarget as HTMLButtonElement).style.transform = 'translateY(-2px)'; (e.currentTarget as HTMLButtonElement).style.boxShadow = '0 16px 40px rgba(234,179,8,0.45)'; }}
                onMouseLeave={e => { (e.currentTarget as HTMLButtonElement).style.transform = 'translateY(0)'; (e.currentTarget as HTMLButtonElement).style.boxShadow = '0 8px 24px rgba(234,179,8,0.25)'; }}
                style={{ padding: '0.875rem 2rem', borderRadius: '0.75rem', backgroundColor: '#eab308', color: '#000', fontWeight: 700, fontSize: '0.95rem', transition: 'all 300ms', cursor: 'pointer', boxShadow: '0 8px 24px rgba(234,179,8,0.25)', border: 'none' }}>
                Read the Docs
              </button>
              <button id="hero-hosted-access"
                onClick={() => setPricingOpen(true)}
                onMouseEnter={e => { (e.currentTarget as HTMLButtonElement).style.backgroundColor = isDark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.06)'; (e.currentTarget as HTMLButtonElement).style.transform = 'translateY(-2px)'; }}
                onMouseLeave={e => { (e.currentTarget as HTMLButtonElement).style.backgroundColor = 'transparent'; (e.currentTarget as HTMLButtonElement).style.transform = 'translateY(0)'; }}
                style={{ padding: '0.875rem 2rem', borderRadius: '0.75rem', border: isDark ? '1px solid rgba(255,255,255,0.12)' : '1px solid rgba(0,0,0,0.15)', color: isDark ? '#e2e8f0' : '#1e293b', fontWeight: 600, fontSize: '0.95rem', transition: 'all 300ms', cursor: 'pointer' }}>
                Hosted Access →
              </button>
            </motion.div>
          </div>
        </section>
        <div className="hero-search-wrapper">
          <Search className="hero-search-icon" size={20} />
          <input
            type="text"
            className="hero-search-input"
            placeholder="Search the docs (e.g., AOP, MySQL, Dead Letter Queue)..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
        </div>
      </section>

      {/* Architecture Image — sits naturally in flow below search bar */}
      <motion.div
        initial={{ opacity: 0, y: 24 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.4, duration: 0.8, ease: 'easeOut' }}
        style={{ position: 'relative', width: '100%', borderRadius: '1.25rem', overflow: 'hidden', border: '1px solid hsl(var(--border-color))', boxShadow: '0 30px 60px rgba(0,0,0,0.5)' }}
      >
        <img
          src="/architecture.jpeg"
          alt="Notify.ai System Architecture Diagram"
          style={{ width: '100%', display: 'block', opacity: 0.8 }}
        />
      </motion.div>

      {/* Grid of Quick Starts */}
      <section className="landing-section">
        <h2 className="landing-section-title">Getting Started Guides</h2>
        <div className="landing-grid">
          <div className="landing-card" onClick={() => selectDoc('intro')}>
            <h3>Quick Start Guide</h3>
            <p>Stand up the complete Notify.ai engine, MySQL, Redis, and administration portals locally in under 5 minutes.</p>
            <span className="landing-card-link">Start Building &rarr;</span>
          </div>

          <div className="landing-card" onClick={() => selectDoc('client')}>
            <h3>Java Client SDK</h3>
            <p>Configure aspect-oriented event capturing inside your spring boot projects to intercept method executions.</p>
            <span className="landing-card-link">View Integration &rarr;</span>
          </div>

          <div className="landing-card" onClick={() => selectDoc('acp-server')}>
            <h3>Agent Control Plane</h3>
            <p>Explore the LLM orchestrator that processes incoming events, extracts facts, and structures behavioral profiles.</p>
            <span className="landing-card-link">Explore Agents &rarr;</span>
          </div>
        </div>
      </section>

      {/* Key Architectural Highlights */}
      <section className="landing-section">
        <h2 className="landing-section-title">Platform Capabilities</h2>
        <div className="architecture-grid">
          <div className="architecture-card">
            <h4>Aspect-Oriented Interception</h4>
            <p>Zero-boilerplate event capturing using semantic annotations like <code>@Event</code> and <code>@Vocabulary</code> inside spring boot clients.</p>
          </div>
          <div className="architecture-card">
            <h4>Memory Assembler & Vector Search</h4>
            <p>Combines incoming domain events into raw context vectors, executes PGVector semantic search, and extracts user profiles dynamically.</p>
          </div>
          <div className="architecture-card">
            <h4>Multi-Channel Delivery Pipes</h4>
            <p>Reactive RX delivery engines distributing notifications across email, SMS, push, and webhooks with built-in dead letter queues.</p>
          </div>
        </div>
      </section>
      {pricingOpen && (
        <div className="pricing-modal-overlay" onClick={() => setPricingOpen(false)}>
          <div className="pricing-modal-card animate-fade-in" onClick={e => e.stopPropagation()}>
            <div className="pricing-modal-header">
              <h2>Hosted Access Plans</h2>
              <button className="pricing-modal-close" onClick={() => setPricingOpen(false)}>&times;</button>
            </div>
            <div className="pricing-modal-body">
              <p className="pricing-modal-desc">
                Deploy and scale Notify.ai instantly on our secure, managed cloud infrastructure.
              </p>
              <div className="pricing-plans-grid">
                <div className="pricing-plan-card">
                  <h3>Developer Plan</h3>
                  <div className="pricing-price"> Free </div>
                  <ul>
                    <li>Limited scalability and security</li>
                    <li>Shared agent execution queue</li>
                    <li>Standard vector memory database and in memory cache</li>
                    <li>OpenAI Llm provider</li>
                    <li>REST API based event ingestion and scheduled notification trigger API</li>
                  </ul>
                </div>
                <div className="pricing-plan-card popular">
                  <div className="popular-badge">Most Popular</div>
                  <h3>Enterprise Plan</h3>
                  <div className="pricing-price">Custom</div>
                  <ul>
                    <li>Dedicated Kafka Topics for event ingestion</li>
                    <li>Dedicated agent instances & keys</li>
                    <li>Dedicated PGVector database and Redis based cache</li>
                    <li>OpenAI or locally hosted Ollama based LLM Provider, faster throughput</li>
                    <li>24/7 SLA developer support</li>
                  </ul>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default function App() {
  const [selectedDocId, setSelectedDocId] = useState<string>('home');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [theme, setTheme] = useState<'dark' | 'light'>('dark');
  const [sidebarOpen, setSidebarOpen] = useState<boolean>(false);
  const [demoModalOpen, setDemoModalOpen] = useState<boolean>(false);
  const [toc, setToc] = useState<TOCItem[]>([]);
  const [activeTocId, setActiveTocId] = useState<string>('');

  const contentRef = useRef<HTMLDivElement>(null);

  // Initialize theme from localStorage or system settings
  useEffect(() => {
    const savedTheme = localStorage.getItem('theme') as 'dark' | 'light' | null;
    const systemPrefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;

    const initialTheme = savedTheme || (systemPrefersDark ? 'dark' : 'light');
    setTheme(initialTheme);

    if (initialTheme === 'light') {
      document.documentElement.classList.add('light');
    } else {
      document.documentElement.classList.remove('light');
    }
  }, []);

  const toggleTheme = () => {
    const nextTheme = theme === 'dark' ? 'light' : 'dark';
    setTheme(nextTheme);
    localStorage.setItem('theme', nextTheme);
    if (nextTheme === 'light') {
      document.documentElement.classList.add('light');
    } else {
      document.documentElement.classList.remove('light');
    }
  };

  // Get active document
  const activeDoc = allDocs.find(d => d.id === selectedDocId) || {
    id: 'home',
    title: 'Overview',
    category: 'Home',
    content: ''
  };

  // Helper to slugify heading text
  const slugify = (text: string) => {
    return text
      .toLowerCase()
      .trim()
      .replace(/[^\w\s-]/g, '')
      .replace(/\s+/g, '-')
      .replace(/-+/g, '-');
  };

  // Process Markdown Content: Table of Contents & Interactive Code blocks
  useEffect(() => {
    if (!contentRef.current) return;

    // 1. Process headings to add IDs for anchor links
    const headings = contentRef.current.querySelectorAll('h2, h3');
    const tocItems: TOCItem[] = [];

    headings.forEach((heading) => {
      const text = heading.textContent || '';
      const slug = slugify(text);
      heading.id = slug;

      tocItems.push({
        id: slug,
        text,
        level: heading.tagName as 'H2' | 'H3'
      });
    });

    setToc(tocItems);
    if (tocItems.length > 0) {
      setActiveTocId(tocItems[0].id);
    } else {
      setActiveTocId('');
    }

    // 2. Process code blocks to add premium headers and copy buttons
    const preBlocks = contentRef.current.querySelectorAll('pre');
    preBlocks.forEach((pre) => {
      // Avoid double wrapping if already processed
      if (pre.parentElement?.classList.contains('code-block-wrapper')) return;

      const codeEl = pre.querySelector('code');
      const codeText = codeEl?.textContent || '';

      // Extract language
      let lang = 'code';
      if (codeEl) {
        const classes = Array.from(codeEl.classList);
        const langClass = classes.find(c => c.startsWith('language-'));
        if (langClass) {
          lang = langClass.replace('language-', '');
        }
      }

      // Create code block wrapper
      const wrapper = document.createElement('div');
      wrapper.className = 'code-block-wrapper';

      // Create code block header
      const header = document.createElement('div');
      header.className = 'code-block-header';

      const langBadge = document.createElement('span');
      langBadge.textContent = lang;

      const copyBtn = document.createElement('button');
      copyBtn.className = 'code-block-copy-btn';
      copyBtn.innerHTML = `Copy`;

      // Event listener for copy button
      copyBtn.addEventListener('click', async () => {
        try {
          await navigator.clipboard.writeText(codeText);
          copyBtn.innerHTML = `Copied!`;
          copyBtn.style.color = 'hsl(var(--accent-color))';
          setTimeout(() => {
            copyBtn.innerHTML = `Copy`;
            copyBtn.style.color = '';
          }, 2000);
        } catch (err) {
          console.error('Failed to copy text: ', err);
        }
      });

      header.appendChild(langBadge);
      header.appendChild(copyBtn);

      // Wrap pre block
      pre.parentNode?.insertBefore(wrapper, pre);
      wrapper.appendChild(header);
      wrapper.appendChild(pre);
    });

    // 3. Scroll to top on page switch
    window.scrollTo(0, 0);

  }, [selectedDocId]);

  // Scrollspy to update active heading in TOC
  useEffect(() => {
    const handleScroll = () => {
      if (!contentRef.current) return;
      const headings = Array.from(contentRef.current.querySelectorAll('h2, h3'));
      if (headings.length === 0) return;

      const scrollPosition = window.scrollY + 100; // Offset for header

      // Find current heading
      let currentHeadingId = headings[0].id;
      for (const heading of headings) {
        const element = heading as HTMLElement;
        if (element.offsetTop <= scrollPosition) {
          currentHeadingId = element.id;
        } else {
          break;
        }
      }
      setActiveTocId(currentHeadingId);
    };

    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, [toc]);

  // Filter registry based on search query
  const filteredRegistry = docsRegistry.map(category => {
    const matchedItems = category.items.filter(item => {
      const query = searchQuery.toLowerCase();
      return (
        item.title.toLowerCase().includes(query) ||
        item.content.toLowerCase().includes(query)
      );
    });
    return {
      ...category,
      items: matchedItems
    };
  }).filter(category => category.items.length > 0);

  // Parse markdown content to safe html
  const renderMarkdown = (md: string) => {
    try {
      return marked.parse(md) as string;
    } catch (err) {
      console.error('Error rendering markdown: ', err);
      return `<p>Error rendering documentation</p>`;
    }
  };

  const selectDoc = (id: string) => {
    setSelectedDocId(id);
    setSidebarOpen(false); // Close sidebar on mobile
  };

  const handleTocClick = (id: string) => {
    const element = document.getElementById(id);
    if (element) {
      const yOffset = -85; // header offset
      const y = element.getBoundingClientRect().top + window.pageYOffset + yOffset;
      window.scrollTo({ top: y, behavior: 'smooth' });
      setActiveTocId(id);
    }
  };

  return (
    <div className="app-container">
      {/* Mobile Backdrop */}
      {sidebarOpen && (
        <div className="backdrop" onClick={() => setSidebarOpen(false)} />
      )}

      {/* Left Sidebar */}
      <aside className={`sidebar ${sidebarOpen ? 'open' : ''}`}>
        <div className="logo-section" style={{ cursor: 'pointer' }} onClick={() => selectDoc('home')}>
          <div className="logo-text"><span className="logo-text-notify">Notify</span><span className="logo-text-ai">.ai</span></div>
        </div>

        <div className="search-container">
          <Search className="search-icon" size={16} />
          <input
            type="text"
            className="search-input"
            placeholder="Search documentation..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
        </div>

        <nav className="nav-groups">
          <div className="nav-group">
            <div
              className={`nav-item ${selectedDocId === 'home' ? 'active' : ''}`}
              onClick={() => selectDoc('home')}
              style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontWeight: 600 }}
            >
              <Home size={16} />
              Overview Home
            </div>
          </div>

          {filteredRegistry.length > 0 ? (
            filteredRegistry.map(category => (
              <div key={category.name} className="nav-group">
                <div className="nav-group-title">{category.name}</div>
                {category.items.map(item => (
                  <div
                    key={item.id}
                    className={`nav-item ${selectedDocId === item.id ? 'active' : ''}`}
                    onClick={() => selectDoc(item.id)}
                  >
                    {item.title}
                  </div>
                ))}
              </div>
            ))
          ) : (
            <div style={{ padding: '1rem', color: 'hsl(var(--text-muted))', fontSize: '0.875rem', textAlign: 'center' }}>
              No documents matched your search.
            </div>
          )}
        </nav>
      </aside>

      {/* Main Content Wrapper */}
      <div className="main-wrapper">
        {/* Top Header */}
        <header className="top-header">
          <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
            <button className="sidebar-toggle-btn" onClick={() => setSidebarOpen(true)}>
              <Menu size={20} />
            </button>
            <div className="breadcrumbs">
              Docs <ChevronRight size={12} style={{ display: 'inline', margin: '0 2px', verticalAlign: 'middle' }} /> {activeDoc.category} <ChevronRight size={12} style={{ display: 'inline', margin: '0 2px', verticalAlign: 'middle' }} /> <span className="current">{activeDoc.title}</span>
            </div>
          </div>

          <div className="header-actions">
            <button className="docs-primary-btn" onClick={() => setDemoModalOpen(true)}>
              Request demo
            </button>
            <button
              className="theme-toggle-btn"
              onClick={toggleTheme}
              title={theme === 'dark' ? 'Switch to Light Mode' : 'Switch to Dark Mode'}
            >
              {theme === 'dark' ? <Sun size={18} /> : <Moon size={18} />}
            </button>
            <a
              href="https://github.com/notify-ai-org/notify-ai"
              target="_blank"
              rel="noreferrer"
              className="github-btn"
              title="GitHub Repository"
            >
              <svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" strokeWidth="2" fill="none" strokeLinecap="round" strokeLinejoin="round">
                <path d="M9 19c-5 1.5-5-2.5-7-3m14 6v-3.87a3.37 3.37 0 0 0-.94-2.61c3.14-.35 6.44-1.54 6.44-7A5.44 5.44 0 0 0 20 4.77 5.07 5.07 0 0 0 19.91 1S18.73.65 16 2.48a13.38 13.38 0 0 0-7 0C6.27.65 5.09 1 5.09 1A5.07 5.07 0 0 0 5 4.77a5.44 5.44 0 0 0-1.5 3.78c0 5.42 3.3 6.61 6.44 7A3.37 3.37 0 0 0 9 18.13V22"></path>
              </svg>
            </a>
          </div>
        </header>

        {/* Content Body */}
        <div className="content-area">
          {selectedDocId === 'home' ? (
            <LandingPage
              selectDoc={selectDoc}
              searchQuery={searchQuery}
              setSearchQuery={setSearchQuery}
              filteredRegistry={filteredRegistry}
              theme={theme}
            />
          ) : (
            <article className="markdown-container">
              <div
                ref={contentRef}
                className="markdown-body"
                dangerouslySetInnerHTML={{ __html: renderMarkdown(activeDoc.content) }}
              />
            </article>
          )}

          {/* Right Sidebar for TOC */}
          {selectedDocId !== 'home' && toc.length > 0 && (
            <aside className="toc-container">
              <div className="toc-title">On this page</div>
              <ul className="toc-list">
                {toc.map(item => (
                  <li key={item.id} className="toc-item">
                    <a
                      className={`toc-link ${activeTocId === item.id ? 'active' : ''} ${item.level === 'H3' ? 'indent-h3' : ''}`}
                      onClick={() => handleTocClick(item.id)}
                    >
                      {item.text}
                    </a>
                  </li>
                ))}
              </ul>
            </aside>
          )}
        </div>
      </div>

      {demoModalOpen && (
        <DemoRequestModal onClose={() => setDemoModalOpen(false)} />
      )}
    </div>
  );
}
