import introRaw from './intro.md?raw';
import acpServerRaw from './acp_server.md?raw';
import engineRaw from './engine.md?raw';
import artifactEngineRaw from './artifact_engine.md?raw';
import clientRaw from './client.md?raw';
import clientPythonRaw from './client_python.md?raw';
import clientGoRaw from './client_go.md?raw';
import clientTsRaw from './client_ts.md?raw';
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
      {
        id: 'artifact-engine',
        title: 'Artifact Storage and Retrieval Engine',
        category: 'Core Modules',
        content: artifactEngineRaw,
      },
    ],
  },
  {
    name: 'Developer SDK',
    items: [
      {
        id: 'client',
        title: 'Java SDK (Spring Boot)',
        category: 'Developer SDK',
        content: clientRaw,
      },
      {
        id: 'client-python',
        title: 'Python SDK',
        category: 'Developer SDK',
        content: clientPythonRaw,
      },
      {
        id: 'client-go',
        title: 'Go SDK',
        category: 'Developer SDK',
        content: clientGoRaw,
      },
      {
        id: 'client-ts',
        title: 'TypeScript SDK',
        category: 'Developer SDK',
        content: clientTsRaw,
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
