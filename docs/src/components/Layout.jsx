import React from 'react';

const Layout = ({ children }) => {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', minHeight: '100vh', backgroundColor: 'var(--bg-dark)', color: 'var(--text-main)', transition: 'background-color 300ms, color 300ms' }}>
      {children}
    </div>
  );
};

export default Layout;
