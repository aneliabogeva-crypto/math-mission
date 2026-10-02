export const AVATARS: Record<string, { emoji: string; label: string }> = {
  fox: { emoji: '🦊', label: 'Лисица' }, owl: { emoji: '🦉', label: 'Бухал' }, rocket: { emoji: '🚀', label: 'Ракета' },
  cat: { emoji: '🐱', label: 'Котка' }, robot: { emoji: '🤖', label: 'Робот' }, planet: { emoji: '🪐', label: 'Планета' },
  dragon: { emoji: '🐉', label: 'Дракон' }, turtle: { emoji: '🐢', label: 'Костенурка' },
};

export function Avatar({ id, size = 40 }: { id?: string; size?: number }) {
  const a = AVATARS[id ?? 'fox'] ?? AVATARS.fox;
  return (
    <span role="img" aria-label={a.label}
      style={{ fontSize: size * 0.6, width: size, height: size, display: 'inline-grid', placeItems: 'center',
        borderRadius: '50%', background: 'var(--surface-2)' }}>
      {a.emoji}
    </span>
  );
}
