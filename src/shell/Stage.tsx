import { useRecording } from '../recording/useRecording';
import { getSim } from '../sims/registry';
import type { SimDef } from '../sims/types';
import { StageFrame } from './StageFrame';

export function Stage() {
  const rec = useRecording();
  const sim = getSim(rec.simId)!;
  const C = sim.Component;
  return (
    <div className={'stage ' + (rec.isRecording ? 'stage-rec' : 'stage-edit')} data-frame={sim.frame}>
      <StageFrame w={sim.logical.w} h={sim.logical.h}>
        {C ? <C /> : <NotReady sim={sim} />}
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
