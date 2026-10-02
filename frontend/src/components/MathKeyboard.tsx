import { RefObject } from 'react';

type Target = HTMLInputElement | HTMLTextAreaElement;

const KEYS: { label: string; insert?: string; action?: 'back' | 'left' | 'right' | 'clear'; aria: string; wide?: boolean }[] = [
  { label: 'x', insert: 'x', aria: 'x' }, { label: 'y', insert: 'y', aria: 'y' }, { label: 'a', insert: 'a', aria: 'a' },
  { label: 'b', insert: 'b', aria: 'b' }, { label: 'xⁿ', insert: '^', aria: 'степен' }, { label: 'x²', insert: '^2', aria: 'на квадрат' },
  { label: '7', insert: '7', aria: '7' }, { label: '8', insert: '8', aria: '8' }, { label: '9', insert: '9', aria: '9' },
  { label: '(', insert: '(', aria: 'отваряща скоба' }, { label: ')', insert: ')', aria: 'затваряща скоба' }, { label: ':', insert: ':', aria: 'делено' },
  { label: '4', insert: '4', aria: '4' }, { label: '5', insert: '5', aria: '5' }, { label: '6', insert: '6', aria: '6' },
  { label: '+', insert: '+', aria: 'плюс' }, { label: '−', insert: '-', aria: 'минус' }, { label: '·', insert: '*', aria: 'по' },
  { label: '1', insert: '1', aria: '1' }, { label: '2', insert: '2', aria: '2' }, { label: '3', insert: '3', aria: '3' },
  { label: '0', insert: '0', aria: '0' }, { label: ',', insert: ',', aria: 'десетична запетая' }, { label: '/', insert: '/', aria: 'дробна черта' },
  { label: '◀', action: 'left', aria: 'курсор наляво' }, { label: '▶', action: 'right', aria: 'курсор надясно' },
  { label: '⌫ Изтрий', action: 'back', aria: 'изтрий символ', wide: true }, { label: 'Изчисти', action: 'clear', aria: 'изчисти полето', wide: true },
];

/** On-screen mathematical keyboard. Every key is at least 44×44 CSS px. */
export function MathKeyboard({ target, value, onChange }: { target: RefObject<Target>; value: string; onChange: (v: string) => void }) {
  function apply(k: (typeof KEYS)[number]) {
    const el = target.current;
    const start = el?.selectionStart ?? value.length;
    const end = el?.selectionEnd ?? value.length;
    let next = value;
    let caret = start;
    if (k.insert) {
      next = value.slice(0, start) + k.insert + value.slice(end);
      caret = start + k.insert.length;
    } else if (k.action === 'back') {
      if (start !== end) { next = value.slice(0, start) + value.slice(end); caret = start; }
      else if (start > 0) { next = value.slice(0, start - 1) + value.slice(start); caret = start - 1; }
    } else if (k.action === 'clear') { next = ''; caret = 0; }
    else if (k.action === 'left') caret = Math.max(0, start - 1);
    else if (k.action === 'right') caret = Math.min(value.length, start + 1);
    onChange(next);
    requestAnimationFrame(() => {
      if (el) { el.focus({ preventScroll: true }); el.setSelectionRange(caret, caret); }
    });
  }
  return (
    <div className="keyboard" role="group" aria-label="Математическа клавиатура">
      {KEYS.map((k) => (
        <button key={k.label} type="button" className={k.wide ? 'wide' : undefined} aria-label={k.aria}
          onMouseDown={(e) => e.preventDefault()} onClick={() => apply(k)}>
          {k.label}
        </button>
      ))}
    </div>
  );
}
