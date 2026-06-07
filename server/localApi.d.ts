import type { IncomingMessage, ServerResponse } from 'http';

/** Returns true if the request was handled (response sent), false otherwise. */
export function handleLocalApi(req: IncomingMessage, res: ServerResponse): Promise<boolean>;
