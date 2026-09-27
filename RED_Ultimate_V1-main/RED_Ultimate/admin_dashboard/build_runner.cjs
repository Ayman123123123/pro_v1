const crypto = require('crypto');
if (!globalThis.crypto) {
  globalThis.crypto = crypto.webcrypto;
}
if (!crypto.getRandomValues && crypto.webcrypto?.getRandomValues) {
  crypto.getRandomValues = crypto.webcrypto.getRandomValues.bind(crypto.webcrypto);
}

import('vite').then(({ build }) => {
  build().then(() => {
    console.log('Vite build completed successfully!');
  }).catch((err) => {
    console.error('Vite build error:', err);
    process.exit(1);
  });
}).catch((err) => {
  console.error('Import error:', err);
  process.exit(1);
});
