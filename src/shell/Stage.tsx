import { useRecording } from '../recording/useRecording';
import { getSim } from '../sims/registry';
import type { SimDef } from '../sims/types';
import { StageFrame } from './StageFrame';
import { useEffectsState } from '../recording/effects/EffectsProvider';
import { EffectsOverlay, cameraStyle } from '../recording/effects/EffectsOverlay';

export function Stage() {
  const rec = useRecording();
  const sim = getSim(rec.simId)!;
  const C = sim.Component;
  // a sim may pick its logical size from its own settings (e.g. email
  // desktop-landscape vs reel-portrait).
  const simS = { ...(sim.defaultSettings ?? {}), ...(rec.settings.sim?.[sim.id] ?? {}) };
  const logical = sim.getLogical ? sim.getLogical(simS as any) : sim.logical;
  const fx = useEffectsState();
  return (
    <div className={'stage ' + (rec.isRecording ? 'stage-rec' : 'stage-edit')} data-frame={sim.frame}>
      <StageFrame w={logical.w} h={logical.h}>
        <div className="cam" style={cameraStyle(fx.camera)}>
          {C ? <C /> : <NotReady sim={sim} />}
        </div>
        <EffectsOverlay />
        {rec.settings.grain && <div className="fx-grain" />}
        {rec.settings.vignette && <div className="fx-vignette" />}
      </StageFrame>
    </div>
  );
}

function NotReady({ sim }: { sim: SimDef }) {
  return (
    <div className="notready" style={{ width: sim.logical.w, height: sim.logical.h }}>
      <div className="notready-glyph">{sim.glyph}</div>
      <div className="notready-title">{sim.label}</div>
      <div className="notready-sub">Not rebuilt yet — coming next.</div>
    </div>
  );
}
