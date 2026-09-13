const [major, minor] = process.versions.node.split('.').map(Number);
const supported = major > 20 || (major === 20 && minor >= 19);

if (!supported) {
  console.error(
    `Node.js ${process.versions.node} is unsupported. ` +
      'The admin dashboard requires Node.js 20.19+ (Node 22 LTS recommended).'
  );
  process.exit(1);
}
