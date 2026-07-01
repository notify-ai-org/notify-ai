import introRaw from './intro.md?raw';
import acpServerRaw from './acp_server.md?raw';
import engineRaw from './engine.md?raw';
import clientRaw from './client.md?raw';
import ecommerceRaw from './ecommerce_app.md?raw';
import bankingRaw from './banking_app.md?raw';

export interface DocItem {
  id: string;
  title: string;
  category: string;
  content: string;
}

export interface DocCategory {
  name: string;
  items: DocItem[];
}

export const docsRegistry: DocCategory[] = [
  {
    name: 'Getting Started',
    items: [
      {
        id: 'intro',
        title: 'Welcome to Notify.ai',
        category: 'Getting Started',
        content: introRaw,
      }
    ],
  },
  {
    name: 'Core Modules',
    items: [
      {
        id: 'acp-server',
        title: 'Agent Control Plane (ACP)',
        category: 'Core Modules',
        content: acpServerRaw,
      },
      {
        id: 'engine',
        title: 'Execution & Delivery Engine',
        category: 'Core Modules',
        content: engineRaw,
      },
    ],
  },
  {
    name: 'Developer SDK',
    items: [
      {
        id: 'client',
        title: 'Client AOP SDK',
        category: 'Developer SDK',
        content: clientRaw,
      },
    ],
  },
  {
    name: 'Integration Examples',
    items: [
      {
        id: 'ecommerce',
        title: 'E-Commerce App',
        category: 'Integration Examples',
        content: ecommerceRaw,
      },
      {
        id: 'banking',
        title: 'Banking App',
        category: 'Integration Examples',
        content: bankingRaw,
      },
    ],
  },
];

export const allDocs: DocItem[] = docsRegistry.reduce<DocItem[]>((acc, category) => {
  return [...acc, ...category.items];
}, []);
