# Media + config via Supabase

The hub stores uploaded media (images/videos/audio) and the OBS config token in
**Supabase Storage** — a public bucket of its own. OBS then loads media as real
`https://` files (complete images, no data-URL truncation) and it all works from
any machine and the hosted Vercel app, with **no local server**.

It reuses the **shared Supabase project** (the one vault/wallet/inkwell use). The
hub only touches its own `hub-media` bucket, so vault's `vault` bucket and
Inkwell's `inkwell_*` tables are untouched.

## One-time setup

### 1. Get the keys
Supabase dashboard → **Project Settings → API**. Copy:
- **Project URL** → `VITE_SUPABASE_URL`
- **anon public** key → `VITE_SUPABASE_ANON_KEY`

(These are the same values as vault/inkwell's `NEXT_PUBLIC_SUPABASE_URL` + anon key.
The anon key is designed to be public/client-side.)

### 2. Create the bucket + policies
Supabase → **SQL Editor** → run:

```sql
-- Public bucket for the hub (isolated from vault's 'vault' bucket)
insert into storage.buckets (id, name, public)
values ('hub-media', 'hub-media', true)
on conflict (id) do update set public = true;

-- No-login (anon) read/write scoped to ONLY this bucket
create policy "hub_media_read"   on storage.objects for select to anon using (bucket_id = 'hub-media');
create policy "hub_media_insert" on storage.objects for insert to anon with check (bucket_id = 'hub-media');
create policy "hub_media_update" on storage.objects for update to anon using (bucket_id = 'hub-media') with check (bucket_id = 'hub-media');
create policy "hub_media_delete" on storage.objects for delete to anon using (bucket_id = 'hub-media');
```

### 3. Set the env vars
- **Local:** copy `.env.example` → `.env` and fill `VITE_SUPABASE_URL` + `VITE_SUPABASE_ANON_KEY`.
- **Vercel:** Project → Settings → Environment Variables → add the same two (and
  optionally `VITE_SUPABASE_BUCKET`). Redeploy.

That's it. The settings drawer's **Background media** uploader now stores to
Supabase; **Copy OBS URL** stores the config there and emits
`…/#present&sim=NAME&cfg=TOKEN`, which OBS resolves from Supabase.

## Layout in the bucket
```
hub-media/
  media/    uploaded images / videos / audio  (public URLs)
  configs/  <token>.json  (the per-take config OBS fetches)
```

## Notes
- **Without env vars**, the app falls back to the local server (`npm run local`)
  — Supabase is optional, not required to run.
- Security: because uploads are anon, anyone with the anon key + bucket name can
  write to `hub-media`. That's fine for a personal recording tool; if you want it
  locked down, use a separate Supabase project or put it behind auth.
