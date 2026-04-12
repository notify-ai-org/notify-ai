import React from 'react';
import { Copy, Check } from 'lucide-react';
import { useState } from 'react';

const CodeBlock = ({ code, language = 'java' }) => {
  const [copied, setCopied] = useState(false);
  const [isHovered, setIsHovered] = useState(false);

  const handleCopy = () => {
    navigator.clipboard.writeText(code);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div 
      style={{ position: 'relative', margin: '2rem 0' }}
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
    >
      <div style={{ position: 'absolute', right: '1rem', top: '1rem', zIndex: 10 }}>
        <button 
          onClick={handleCopy}
          onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'rgba(255, 255, 255, 0.1)'}
          onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'rgba(255, 255, 255, 0.05)'}
          style={{ padding: '0.5rem', borderRadius: '0.5rem', backgroundColor: 'rgba(255, 255, 255, 0.05)', border: '1px solid rgba(255, 255, 255, 0.1)', transition: 'all 300ms', cursor: 'pointer' }}
        >
          {copied ? <Check size={16} color="#4ade80" /> : <Copy size={16} color="#9ca3af" />}
        </button>
      </div>
      <div style={{
        position: 'absolute', top: '-1px', left: '-1px', right: '-1px', bottom: '-1px',
        background: 'linear-gradient(to right, rgba(234, 179, 8, 0.2), rgba(251, 146, 60, 0.2))',
        borderRadius: '1rem', filter: 'blur(4px)', transition: 'opacity 500ms',
        opacity: isHovered ? 1 : 0
      }} />
      <pre style={{ position: 'relative', overflowX: 'auto', padding: '1.5rem', borderRadius: '0.75rem', backgroundColor: '#0d0d0f', border: '1px solid rgba(255, 255, 255, 0.1)', fontFamily: 'monospace', fontSize: '0.875rem', lineHeight: 1.625 }}>
        <code style={{ display: 'block', color: '#d1d5db' }}>
          {code}
        </code>
      </pre>
    </div>
  );
};

export default CodeBlock;
