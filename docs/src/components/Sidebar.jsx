import React from 'react';
import { Home, BookText, Code } from 'lucide-react';
import { motion } from 'framer-motion';

const Sidebar = ({ activeTab, setActiveTab }) => {
  const tabs = [
    { id: 'home', label: 'Home', icon: Home },
    { id: 'guide', label: 'SDK Guide', icon: BookText },
    { id: 'examples', label: 'Examples', icon: Code },
  ];

  return (
    <div className="w-64 h-screen border-r border-white/10 glass flex flex-col p-4 relative z-50 shrink-0">
      <div className="flex items-center gap-3 px-4 py-6 mb-4">
        <span className="text-xl font-bold tracking-wide">Notify<span className="text-yellow-500">.ai</span></span>
      </div>

      <nav className="flex flex-col gap-2 relative">
        {tabs.map((tab) => {
          const Icon = tab.icon;
          const isActive = activeTab === tab.id;
          return (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              className={`flex items-center gap-3 px-4 py-3 rounded-xl transition-all relative overflow-hidden text-left ${isActive ? 'text-yellow-400 font-semibold' : 'text-gray-400 hover:text-gray-100 hover:bg-white/5'
                }`}
            >
              {isActive && (
                <motion.div
                  layoutId="sidebar-active"
                  className="absolute inset-0 bg-yellow-500/10 border border-yellow-500/20 rounded-xl"
                  initial={false}
                  transition={{ type: "spring", stiffness: 300, damping: 30 }}
                />
              )}
              <Icon size={18} className="relative z-10" />
              <span className="relative z-10">{tab.label}</span>
            </button>
          );
        })}
      </nav>
    </div>
  );
};

export default Sidebar;
