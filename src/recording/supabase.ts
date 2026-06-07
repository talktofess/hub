/* Supabase client for media + config storage.

   Reuses the shared Supabase project (the same one vault/wallet/inkwell use) via
   the public anon key — safe to expose in the client. The hub keeps its own
   PUBLIC bucket (default `hub-media`) so OBS can load uploaded media as real
   https files (full images, no truncation) and the deployed Vercel app works
   from any machine — no local server required.

   Configure with env vars (see .env.example + SUPABASE.md):
     VITE_SUPABASE_URL, VITE_SUPABASE_ANON_KEY, [VITE_SUPABASE_BUCKET] */

import { createClient } from '@supabase/supabase-js';
import type { SupabaseClient } from '@supabase/supabase-js';

const url = import.meta.env.VITE_SUPABASE_URL;
const anon = import.meta.env.VITE_SUPABASE_ANON_KEY;

export const BUCKET = import.meta.env.VITE_SUPABASE_BUCKET || 'hub-media';
export const hasSupabase = !!(url && anon);
export const supabase: SupabaseClient | null = hasSupabase ? createClient(url!, anon!) : null;
