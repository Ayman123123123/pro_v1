const fs = require('fs');
const filePath = 'node_modules/unplugin/dist/index.mjs';
let content = fs.readFileSync(filePath, 'utf8');
if (!content.includes('__unplugin_dirname')) {
  content = 'import { fileURLToPath as __fileURLToPath } from "node:url";\n' +
            'const __unplugin_dirname = typeof import.meta.dirname !== "undefined" ? import.meta.dirname : path.dirname(__fileURLToPath(import.meta.url));\n' +
            content.replace(/import\.meta\.dirname/g, '__unplugin_dirname');
  fs.writeFileSync(filePath, content, 'utf8');
  console.log('Successfully patched unplugin index.mjs');
} else {
  console.log('Already patched');
}
