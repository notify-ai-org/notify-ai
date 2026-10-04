import { useState } from 'react';
import { ExternalLink, RotateCw } from 'lucide-react';

// The storefront of examples/ecommerce-app, deployed behind nginx at /ecommerce
// (see .github/workflows/deploy-ecommerce.yml). Override per environment with
// VITE_ECOMMERCE_PLAYGROUND_URL, e.g. http://localhost:8090/portals/shop/.
const PLAYGROUND_URL =
  import.meta.env.VITE_ECOMMERCE_PLAYGROUND_URL || 'https://app.notify-ai.dev/ecommerce/portals/shop/';

/** The live e-commerce example app, embedded so readers can trigger Notify events from the docs. */
export function EcommercePlayground() {
  // Changing the key remounts the iframe, which reloads the storefront from its start page.
  const [reloads, setReloads] = useState(0);

  return (
    <section className="playground" aria-labelledby="playground-title">
      <div className="playground-header">
        <div>
          <h2 id="playground-title" className="playground-title">Live playground</h2>
          <p className="playground-hint">
            Sign in as <code>alice@example.com</code> with password <code>password123</code>, then browse,
            add to cart and check out. Each action shows the Notify event it fired.
          </p>
        </div>
        <div className="playground-actions">
          <button type="button" className="docs-secondary-btn" onClick={() => setReloads(count => count + 1)}>
            <RotateCw size={16} />
            Restart
          </button>
          <a className="docs-secondary-btn" href={PLAYGROUND_URL} target="_blank" rel="noreferrer">
            <ExternalLink size={16} />
            Open in new tab
          </a>
        </div>
      </div>
      <iframe
        key={reloads}
        className="playground-frame"
        src={PLAYGROUND_URL}
        title="Notify Shop, the e-commerce example storefront"
        loading="lazy"
        referrerPolicy="strict-origin-when-cross-origin"
      />
      <p className="playground-note">
        If sign-in does not stick, your browser is blocking cookies for embedded sites. Open the storefront in a
        new tab instead.
      </p>
    </section>
  );
}
