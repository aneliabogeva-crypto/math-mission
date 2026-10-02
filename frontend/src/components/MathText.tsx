import { Fragment } from 'react';

const SUP: Record<string, string> = { '0': '⁰', '1': '¹', '2': '²', '3': '³', '4': '⁴', '5': '⁵', '6': '⁶', '7': '⁷', '8': '⁸', '9': '⁹' };
const SUP_REV: Record<string, string> = Object.fromEntries(Object.entries(SUP).map(([k, v]) => [v, k]));

/** Turns typed notation (x^2, 2*x, a-b) into readable notation (x², 2·x, a − b). */
export function pretty(s: string): string {
  return s
    .replace(/\^\(?(\d+)\)?/g, (_, d: string) => d.split('').map((c) => SUP[c]).join(''))
    .replace(/\*/g, '·')
    .replace(/([A-Za-z0-9)\u00B2\u00B3\u00B9\u2070-\u2079])\s*-\s*/g, '$1 − ')
    .replace(/-/g, '−')
    .replace(/([A-Za-z0-9)\u00B2\u00B3\u00B9\u2070-\u2079])\s*\+\s*/g, '$1 + ')
    .replace(/\./g, ',');
}

/** Plain-language reading for screen readers: "x²" → "x на степен 2". */
export function spoken(s: string): string {
  return pretty(s)
    .replace(/([⁰¹²³⁴⁵⁶⁷⁸⁹]+)/g, (m) => ' на степен ' + m.split('').map((c) => SUP_REV[c]).join('') + ' ')
    .replace(/·/g, ' по ')
    .replace(/−/g, ' минус ')
    .replace(/:/g, ' делено на ')
    .replace(/\s+/g, ' ')
    .trim();
}

/** Inline maths. The visual form is hidden from assistive tech and replaced by a spoken label. */
export function MathText({ children, block = false }: { children: string; block?: boolean }) {
  const shown = pretty(children);
  const Tag = block ? 'div' : 'span';
  return (
    <Tag className={block ? 'math-block' : 'math'} role="math" aria-label={spoken(children)}>
      <span aria-hidden="true">{shown}</span>
    </Tag>
  );
}

/** Prose with embedded maths written as-is (content already uses Unicode superscripts). */
export function RichText({ text }: { text: string }) {
  const parts = text.split(/(`[^`]+`)/g);
  return (
    <>
      {parts.map((p, i) => (p.startsWith('`') ? <MathText key={i}>{p.slice(1, -1)}</MathText> : <Fragment key={i}>{p}</Fragment>))}
    </>
  );
}
