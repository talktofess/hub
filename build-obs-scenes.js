/* Generates "Sim Hub.json" — an OBS scene collection with one scene per sim,
   each holding a Browser source locked to that sim (hub.html#present&sim=NAME),
   1080x1920, audio routed through OBS, auto-refresh on activate. Scene-switch
   hotkeys are bound to number keys 1-7. Import via OBS: Scene Collection -> Import.
   Schema matched to the user's installed OBS (v536936450).                      */
const fs = require('fs');
const path = require('path');
const { randomUUID } = require('crypto');

const PREV = 536936450;
// Base hub URL the browser sources point at, up to (not including) the `#hash`.
// The hub now runs as a hosted app, so this is an http(s) origin — point OBS at
// the Vercel deployment, or at the local dev server (node serve.js) for testing:
//   PowerShell:  $env:HUB_URL='https://your-app.vercel.app/'; node build-obs-scenes.js
//   bash:        HUB_URL=https://your-app.vercel.app/ node build-obs-scenes.js
// Re-import the regenerated "Sim Hub.json" into OBS afterwards.
const HUB = process.env.HUB_URL || 'http://localhost:3000/';
const CANVAS_UUID = '6c69626f-6273-4c00-9d88-c5136d61696e'; // OBS main canvas

const sims = [
  { sim: 'email',     label: 'Email',     key: 'OBS_KEY_1' },
  { sim: 'imessage',  label: 'iMessage',  key: 'OBS_KEY_2' },
  { sim: 'notes',     label: 'Notes',     key: 'OBS_KEY_3' },
  { sim: 'lists',     label: 'Lists',     key: 'OBS_KEY_4' },
  { sim: 'corporate', label: 'Corporate', key: 'OBS_KEY_5' },
  { sim: 'typer',     label: 'Typer',     key: 'OBS_KEY_6' },
  { sim: 'tiktok',    label: 'TikTok',    key: 'OBS_KEY_7' },
  { sim: 'claude',    label: 'Claude',    key: 'OBS_KEY_8' },
  { sim: 'journal',   label: 'Journal',   key: 'OBS_KEY_9' },
];

const audioHotkeys = {
  'libobs.mute': [], 'libobs.unmute': [],
  'libobs.push-to-mute': [], 'libobs.push-to-talk': [],
};

function audioDevice(name, id, muted) {
  return {
    prev_ver: PREV, name, uuid: randomUUID(), id, versioned_id: id,
    settings: { device_id: 'default' },
    mixers: 255, sync: 0, flags: 0, volume: 1.0, balance: 0.5,
    enabled: true, muted, 'push-to-mute': false, 'push-to-mute-delay': 0,
    'push-to-talk': false, 'push-to-talk-delay': 0, hotkeys: audioHotkeys,
    deinterlace_mode: 0, deinterlace_field_order: 0, monitoring_type: 0,
    private_settings: {},
  };
}

function browserSource(label, sim) {
  return {
    prev_ver: PREV, name: label + ' Sim', uuid: randomUUID(),
    id: 'browser_source', versioned_id: 'browser_source',
    settings: {
      // Single-take recording: #present plays the keystroke audio through the
      // browser source's own (rerouted) audio track, which stays in sync as long
      // as OBS isn't dropping frames. The source is 1x (1080x1920, == canvas) —
      // the old 2x supersample (2160x3840 = 8.3 MP) overloaded the encoder
      // (~36% frames skipped) and made OBS inflate the audio buffer, which WAS
      // the drift. Bump back up only with a hardware encoder + headroom.
      url: `${HUB}#present&sim=${sim}`,
      is_local_file: false,
      width: 1080, height: 1920,
      fps_custom: true, fps: 30,
      reroute_audio: true,          // "Control audio via OBS" -> sim sound effects record
      restart_when_active: true,    // "Refresh browser when scene becomes active" -> fresh take
      shutdown: true,               // "Shutdown source when not visible" -> frees memory
    },
    mixers: 255, sync: 0, flags: 0, volume: 1.0, balance: 0.5,
    enabled: true, muted: false, 'push-to-mute': false, 'push-to-mute-delay': 0,
    'push-to-talk': false, 'push-to-talk-delay': 0,
    hotkeys: {
      'libobs.mute': [], 'libobs.unmute': [],
      'libobs.push-to-mute': [], 'libobs.push-to-talk': [],
      'ObsBrowser.Refresh': [],
    },
    deinterlace_mode: 0, deinterlace_field_order: 0, monitoring_type: 0,
    private_settings: {},
  };
}

function sceneItem(label, srcUuid) {
  return {
    name: label + ' Sim', source_uuid: srcUuid,
    visible: true, locked: false, rot: 0.0,
    scale_ref: { x: 1080.0, y: 1920.0 },
    align: 5, bounds_type: 2, bounds_align: 0, bounds_crop: false,
    crop_left: 0, crop_top: 0, crop_right: 0, crop_bottom: 0,
    id: 1, group_item_backup: false,
    pos: { x: 0.0, y: 0.0 }, pos_rel: { x: -1.0, y: -1.0 },
    scale: { x: 1.0, y: 1.0 }, scale_rel: { x: 1.0, y: 1.0 },
    bounds: { x: 1080.0, y: 1920.0 }, bounds_rel: { x: 2.0, y: 2.0 },
    scale_filter: 'disable', blend_method: 'default', blend_type: 'normal',
    show_transition: { duration: 0 }, hide_transition: { duration: 0 },
    private_settings: {},
  };
}

function scene(name, key, item) {
  return {
    prev_ver: PREV, name, uuid: randomUUID(), id: 'scene', versioned_id: 'scene',
    settings: { id_counter: 2, custom_size: false, items: [item] },
    mixers: 0, sync: 0, flags: 0, volume: 1.0, balance: 0.5,
    enabled: true, muted: false, 'push-to-mute': false, 'push-to-mute-delay': 0,
    'push-to-talk': false, 'push-to-talk-delay': 0,
    hotkeys: {
      'OBSBasic.SelectScene': [{ key }],
      'libobs.show_scene_item.1': [], 'libobs.hide_scene_item.1': [],
    },
    deinterlace_mode: 0, deinterlace_field_order: 0, monitoring_type: 0,
    canvas_uuid: CANVAS_UUID, private_settings: {},
  };
}

const sources = [];
for (const s of sims) {
  const bs = browserSource(s.label, s.sim);
  sources.push(bs);
  sources.push(scene(`${s.key.slice(-1)} ${s.label}`, s.key, sceneItem(s.label, bs.uuid)));
}

const collection = {
  name: 'Sim Hub',
  DesktopAudioDevice1: audioDevice('Desktop Audio', 'wasapi_output_capture', false),
  AuxAudioDevice1: audioDevice('Mic/Aux', 'wasapi_input_capture', true), // muted by default
  sources,
  groups: [],
  scene_order: sims.map((s) => ({ name: `${s.key.slice(-1)} ${s.label}` })),
  current_scene: '1 Email',
  current_program_scene: '1 Email',
  canvases: [],
  current_transition: 'Fade',
  transition_duration: 300,
  transitions: [],
  quick_transitions: [
    { name: 'Cut', duration: 300, hotkeys: [], id: 1, fade_to_black: false },
    { name: 'Fade', duration: 300, hotkeys: [], id: 2, fade_to_black: false },
  ],
  saved_projectors: [],
  preview_locked: false,
  scaling_enabled: false, scaling_level: 0, scaling_off_x: 0.0, scaling_off_y: 0.0,
  modules: {},
  resolution: { x: 1080, y: 1920 },
  version: 2,
};

const out = path.join(__dirname, 'Sim Hub.json');
fs.writeFileSync(out, JSON.stringify(collection, null, 4));
console.log('Wrote "Sim Hub.json"');
console.log('Scenes:', collection.scene_order.map((s) => s.name).join('  |  '));
