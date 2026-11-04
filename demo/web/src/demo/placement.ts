export interface Box {
  left: number;
  top: number;
  width: number;
  height: number;
}

export interface Size {
  width: number;
  height: number;
}

export interface Placement {
  left: number;
  top: number;
  /** From the card to the highlighted feature. Null when there's nothing to point at. */
  arrow: { x1: number; y1: number; x2: number; y2: number } | null;
}

const MARGIN = 12;
const ARROW = 40;
const ARROW_INSET = 16;
const NARROW = 640;

const clamp = (value: number, low: number, high: number) => Math.min(Math.max(value, low), Math.max(low, high));

/** Puts the card on the side of the target with the most spare room, with an arrow across the gap. */
export function place(target: Box | null, card: Size, view: Size): Placement {
  if (!target) {
    return { left: (view.width - card.width) / 2, top: (view.height - card.height) / 2, arrow: null };
  }
  const right = target.left + target.width;
  const bottom = target.top + target.height;
  const centreX = target.left + target.width / 2;
  const centreY = target.top + target.height / 2;
  const across = clamp(centreX - card.width / 2, MARGIN, view.width - card.width - MARGIN);
  const along = clamp(centreY - card.height / 2, MARGIN, view.height - card.height - MARGIN);
  const pointX = (left: number) => clamp(centreX, left + ARROW_INSET, left + card.width - ARROW_INSET);
  const pointY = (top: number) => clamp(centreY, top + ARROW_INSET, top + card.height - ARROW_INSET);

  const needHigh = card.height + ARROW + MARGIN;
  const needWide = card.width + ARROW + MARGIN;
  const sides = [
    { spare: view.height - bottom - needHigh, at: () => {
      const top = bottom + ARROW;
      const x = pointX(across);
      return { left: across, top, arrow: { x1: x, y1: top, x2: x, y2: bottom } };
    } },
    { spare: target.top - needHigh, at: () => {
      const top = target.top - ARROW - card.height;
      const x = pointX(across);
      return { left: across, top, arrow: { x1: x, y1: target.top - ARROW, x2: x, y2: target.top } };
    } },
    ...(view.width <= NARROW ? [] : [
      { spare: view.width - right - needWide, at: () => {
        const left = right + ARROW;
        const y = pointY(along);
        return { left, top: along, arrow: { x1: left, y1: y, x2: right, y2: y } };
      } },
      { spare: target.left - needWide, at: () => {
        const left = target.left - ARROW - card.width;
        const y = pointY(along);
        return { left, top: along, arrow: { x1: target.left - ARROW, y1: y, x2: target.left, y2: y } };
      } },
    ]),
  ];
  const best = sides.reduce((most, side) => (side.spare > most.spare ? side : most));
  if (best.spare >= 0) {
    return best.at();
  }

  // Too big to sit beside, as on a phone, so the card sits over the bottom of it and points up into it
  const top = view.height - card.height - MARGIN;
  const x = pointX(across);
  return { left: across, top, arrow: { x1: x, y1: top, x2: x, y2: top - ARROW } };
}
