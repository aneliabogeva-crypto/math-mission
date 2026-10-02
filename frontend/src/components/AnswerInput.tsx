import { useRef, useState } from 'react';
import type { AnswerPayload, QuestionView } from '../api';
import { MathKeyboard } from './MathKeyboard';
import { MathText } from './MathText';

type Field = HTMLInputElement | HTMLTextAreaElement;

/** Renders the right control for each response type. Mathematical input uses a restricted grammar on the server. */
export function AnswerInput({ question, value, onChange, disabled }: {
  question: QuestionView; value: AnswerPayload; onChange: (v: AnswerPayload) => void; disabled?: boolean;
}) {
  const [keyboard, setKeyboard] = useState(() => window.matchMedia?.('(pointer: coarse)').matches ?? false);
  const [active, setActive] = useState<string>('value');
  const refs = useRef<Record<string, Field | null>>({});
  const activeRef = { get current() { return refs.current[active] ?? null; } };
  const p = question.prompt;
  const t = question.responseType;

  const get = (field: string): string => {
    if (field === 'value') return value.value ?? '';
    if (field.startsWith('step:')) return value.steps?.[Number(field.slice(5))] ?? '';
    if (field.startsWith('part:')) return value.parts?.[field.slice(5)] ?? '';
    return value.text ?? '';
  };
  const set = (field: string, v: string) => {
    if (field === 'value') onChange({ ...value, value: v });
    else if (field.startsWith('step:')) {
      const steps = [...(value.steps ?? [''])];
      steps[Number(field.slice(5))] = v;
      onChange({ ...value, steps });
    } else if (field.startsWith('part:')) onChange({ ...value, parts: { ...(value.parts ?? {}), [field.slice(5)]: v } });
    else onChange({ ...value, text: v });
  };

  const mathField = (field: string, label: string, numeric = false) => (
    <label className="field" key={field}>
      <span>{label}</span>
      <input
        ref={(el) => { refs.current[field] = el; }}
        className="math-input"
        value={get(field)}
        disabled={disabled}
        inputMode={keyboard ? 'none' : numeric ? 'decimal' : 'text'}
        autoComplete="off" autoCapitalize="off" spellCheck={false}
        onFocus={() => setActive(field)}
        onChange={(e) => set(field, e.target.value)}
        aria-describedby={`${question.key}-hint`}
      />
      {get(field) && <span className="small muted">Преглед: <MathText>{get(field)}</MathText></span>}
    </label>
  );

  if (t === 'SINGLE_CHOICE') {
    return (
      <div className="options" role="radiogroup" aria-label="Възможни отговори">
        {p.options?.map((o) => (
          <button key={o.id} type="button" role="radio" className="option" aria-checked={value.optionId === o.id}
            disabled={disabled} onClick={() => onChange({ ...value, optionId: o.id })}>
            <span className="letter" aria-hidden="true">{o.id}</span>
            <MathText>{o.text}</MathText>
          </button>
        ))}
      </div>
    );
  }

  if (t === 'FREE_TEXT') {
    return (
      <label className="field">
        <span>Твоят отговор (ще бъде прегледан от учител)</span>
        <textarea value={value.text ?? ''} disabled={disabled} onChange={(e) => onChange({ ...value, text: e.target.value })} />
      </label>
    );
  }

  const keyboardToggle = (
    <div className="row">
      <button type="button" className="btn ghost small" aria-pressed={keyboard} onClick={() => setKeyboard((k) => !k)}>
        {keyboard ? 'Скрий мат. клавиатура' : 'Покажи мат. клавиатура'}
      </button>
      <span id={`${question.key}-hint`} className="small muted">
        {p.inputHint ?? 'Степен: x^2. Десетична запетая или точка: 2,5 или 2.5.'}
      </span>
    </div>
  );

  let fields: JSX.Element;
  if (t === 'STEPS') {
    const steps = value.steps?.length ? value.steps : [''];
    fields = (
      <div>
        {p.startExpression && <p>Начало: <MathText>{p.startExpression}</MathText></p>}
        {steps.map((_, i) => mathField(`step:${i}`, `Ред ${i + 1}`))}
        <div className="row">
          <button type="button" className="btn secondary" disabled={disabled} onClick={() => onChange({ ...value, steps: [...steps, ''] })}>
            + Нов ред
          </button>
          {steps.length > 1 && (
            <button type="button" className="btn ghost" disabled={disabled} onClick={() => onChange({ ...value, steps: steps.slice(0, -1) })}>
              Премахни последния ред
            </button>
          )}
        </div>
      </div>
    );
  } else if (t === 'STRUCTURED') {
    fields = <div>{p.parts?.map((part) => mathField(`part:${part.id}`, `${part.label} (${part.points} т.)`, part.type === 'NUMERIC'))}</div>;
  } else {
    fields = mathField('value', t === 'NUMERIC' ? 'Число' : 'Израз', t === 'NUMERIC');
  }

  return (
    <div>
      {fields}
      {keyboardToggle}
      {keyboard && !disabled && <MathKeyboard target={activeRef} value={get(active)} onChange={(v) => set(active, v)} />}
    </div>
  );
}

export function isAnswered(a?: AnswerPayload): boolean {
  if (!a) return false;
  return Boolean(a.optionId || a.value?.trim() || a.text?.trim() || a.steps?.some((s) => s.trim())
    || Object.values(a.parts ?? {}).some((s) => s.trim()));
}
