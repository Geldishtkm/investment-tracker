import React from 'react';
import { PolarAngleAxis, PolarGrid, PolarRadiusAxis, Radar, RadarChart, ResponsiveContainer } from 'recharts';
import { ScorePillar } from '../../types';
import { PILLAR_SHORT_NAME } from './styles';

/** Radar of the four pillar scores (0–250 each); pillars without enough data sit at the centre. */
const PillarRadar: React.FC<{ pillars: ScorePillar[] }> = ({ pillars }) => {
  const data = pillars.map(p => ({
    pillar: PILLAR_SHORT_NAME[p.key] + (p.available ? '' : ' (n/a)'),
    points: p.available ? Math.round(p.points) : 0,
    fullMark: p.maxPoints,
  }));
  return (
    <div className="h-72 w-full" aria-label="Pillar scores radar chart">
      <ResponsiveContainer width="100%" height="100%">
        <RadarChart data={data} outerRadius="75%">
          <PolarGrid stroke="#374151" />
          <PolarAngleAxis dataKey="pillar" tick={{ fill: '#d1d5db', fontSize: 12 }} />
          <PolarRadiusAxis domain={[0, 250]} tick={false} axisLine={false} />
          <Radar dataKey="points" stroke="#22d3ee" fill="#22d3ee" fillOpacity={0.35} />
        </RadarChart>
      </ResponsiveContainer>
    </div>
  );
};

export default PillarRadar;
