interface Props {
  title: string;
  sprint: string;
  description: string;
}

export function PlaceholderPage({ title, sprint, description }: Props) {
  return (
    <div className="card">
      <div className="flex items-center gap-3">
        <h1 className="text-2xl font-semibold">{title}</h1>
        <span className="badge bg-slate-100 text-slate-700">{sprint}</span>
      </div>
      <p className="mt-2 text-sm text-slate-600">{description}</p>
    </div>
  );
}
