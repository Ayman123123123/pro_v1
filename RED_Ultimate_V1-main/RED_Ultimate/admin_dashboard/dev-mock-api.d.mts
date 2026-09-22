/** تعريفات TS لخادم التطوير المحاكي */
export function mockApiMiddleware(): (
  req: import('node:http').IncomingMessage,
  res: import('node:http').ServerResponse,
  next: (err?: unknown) => void
) => Promise<void>;
export function probeRealApi(target: string): Promise<boolean>;
